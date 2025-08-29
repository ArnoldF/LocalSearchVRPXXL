package localsearch;
import datastructures.Edge;
import datastructures.Node;
import datastructures.Pair;
import datastructures.Route;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;
import java.util.logging.Logger;

public class LocalSearch {

    private static final Logger logger = Logger.getLogger(LocalSearch.class.getName());

    // -------------------------------
    // Improve a single route
    // -------------------------------
    public static void improveRoute(
            Route route,
            VRPSolution solution,
            CostEvaluator costEvaluator,
            Map<String, Object> runParameters
    ) {
        long start = System.currentTimeMillis();

        if (route.getSize() > 2) {
            // Call Lin-Kernighan heuristic
            LinKernighan.runLinKernighanHeuristic(
                    solution,
                    costEvaluator,
                    route,
                    (Integer) runParameters.get("depth_lin_kernighan")
            );
        }

        long end = System.currentTimeMillis();
        solution.addStat("time_lin_kernighan", (end - start));
    }

    // -------------------------------
    // Get disjunct moves
    // -------------------------------
    public static List<LocalSearchMove> getDisjunctMoves(List<? extends LocalSearchMove> moves) {
        List<LocalSearchMove> disjunctMoves = new ArrayList<>();

        for (LocalSearchMove move : moves) {
            boolean isDisjunct = true;
            for (LocalSearchMove disjunctMove : disjunctMoves) {
                if (!move.isDisjunct(disjunctMove)) {
                    isDisjunct = false;
                    break;
                }
            }
            if (isDisjunct) {
                disjunctMoves.add(move);
            }
        }
        return disjunctMoves;
    }

    // -------------------------------
    // Find best improving moves
    // -------------------------------
    public static Pair<Integer, Set<Route>> findBestImprovingMoves(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            List<Node> startNodes,
            boolean intraRouteOpt,
            String operatorName,
            Map<String, Object> runParameters
    ) {
       
        long start = System.currentTimeMillis();
        List<? extends LocalSearchMove> candidateMoves = switch (operatorName) {
            case "segment_move" -> SegmentMoveOperator.search3OptMoves(solution, costEvaluator, startNodes);
            case "relocation_chain" -> RelocationChainSearch.searchRelocationChains(
                    solution, costEvaluator, startNodes, (int) runParameters.get("depth_relocation_chain")
            );
            case "cross_exchange" -> CrossExchangeOperator.searchCrossExchanges(solution, costEvaluator, startNodes);
            default -> throw new IllegalArgumentException("Operator '" + operatorName + "' is not defined");
        };

        long end = System.currentTimeMillis();
        solution.addStat("time_" + operatorName, (end - start));

        if (candidateMoves != null && !candidateMoves.isEmpty()) {
            logger.fine("Found " + candidateMoves.size() + " improving moves, current solution value: "
                    + costEvaluator.getSolutionCosts(solution, false));

            Set<Route> changedRoutes = new HashSet<>();
            List<LocalSearchMove> disjunctMoves = getDisjunctMoves(candidateMoves);

            // Execute the moves
            for (LocalSearchMove move : disjunctMoves) {
                changedRoutes.addAll(move.getRoutes());
                double oldCosts = costEvaluator.getSolutionCosts(solution, false);

                move.execute(solution);
                solution.addStat("move_count_" + operatorName, 1.0);

                double newCosts = costEvaluator.getSolutionCosts(solution, false);
                double improvement = oldCosts - newCosts;

                if (Math.abs(improvement - move.getImprovement()) > 1e-6) {
                    throw new RuntimeException("Improvement of move " + operatorName + " was " + improvement
                            + " but expected " + move.getImprovement());
                }
                solution.validate();
            }

            // Optimize all changed routes
            if (intraRouteOpt) {
                for (Route route : changedRoutes) {
                    improveRoute(route, solution, costEvaluator, runParameters);
                }
            }

            return new Pair<>(disjunctMoves.size(), changedRoutes);

        } else {
            return new Pair<>(0, new HashSet<>());
        }
    }

    // -------------------------------
    // Local search loop
    // -------------------------------
    public static Pair<Integer, Set<Route>> localSearch(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            Set<Node> startFromNodes,
            boolean intraRouteOpt,
            Map<String, Object> runParameters
    ) {
        int numExecutedMoves = 0;
        Set<Route> allChangedRoutes = new HashSet<>();

        @SuppressWarnings("unchecked")
        List<String> moves = (List<String>) runParameters.get("moves");

        for (String moveType : moves) {
            Pair<Integer, Set<Route>> result = findBestImprovingMoves(
                    solution,
                    costEvaluator,
                    new ArrayList<>(startFromNodes),
                    intraRouteOpt,
                    moveType,
                    runParameters
            );

            numExecutedMoves += result.getFirst();
            allChangedRoutes.addAll(result.getSecond());
        }

        return new Pair<>(numExecutedMoves, allChangedRoutes);
    }

    // -------------------------------
    // Improve the entire solution
    // -------------------------------
    public static void improveSolution(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            Set<Route> startSearchFromRoutes,
            Map<String, Object> runParameters
    ) {
        // Intra-route optimization
        for (Route route : startSearchFromRoutes) {
            improveRoute(route, solution, costEvaluator, runParameters);
        }

        // Inter-route optimization
        Set<Node> startFromNodes = new HashSet<>();
        for (Route route : startSearchFromRoutes) {
            startFromNodes.addAll(route.getCustomers());
        }

        boolean changesFound = true;
        while (changesFound) {
            Pair<Integer, Set<Route>> result = localSearch(
                    solution,
                    costEvaluator,
                    startFromNodes,
                    true,
                    runParameters
            );
            changesFound = result.getFirst() > 0;
        }
    }

    // -------------------------------
    // Perturbation
    // -------------------------------
    public static Set<Route> perturbateSolution(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            Map<String, Object> runParameters
    ) {
        logger.fine("Starting perturbation of solution");

        costEvaluator.enablePenalization();
        costEvaluator.determineEdgeBadness(solution.getRoutes());

        int appliedChanges = 0;
        Set<Route> changedRoutesPerturbation = new HashSet<>();

        int numPerturbations = (Integer) runParameters.get("num_perturbations");

        while (appliedChanges < numPerturbations) {
            Edge worstEdge = costEvaluator.getAndPenalizeWorstEdge();
            logger.fine("Penalizing edge (" + worstEdge.getFirstNode() + " - " + worstEdge.getSecondNode() + ")");
            List<Node> startFromNodes = new ArrayList<>();
            if (!worstEdge.getFirstNode().isDepot()){
                startFromNodes.add(worstEdge.getFirstNode());
            }
            if (!worstEdge.getSecondNode().isDepot()){
                startFromNodes.add(worstEdge.getSecondNode());
            }

            Pair<Integer, Set<Route>> result = localSearch(
                    solution,
                    costEvaluator,
                    new HashSet<>(startFromNodes),
                    false,
                    runParameters
            );

            appliedChanges += result.getFirst();
            changedRoutesPerturbation.addAll(result.getSecond());
        }

        costEvaluator.disablePenalization();
        return changedRoutesPerturbation;
    }


}

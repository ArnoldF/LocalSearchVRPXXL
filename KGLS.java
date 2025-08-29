import java.util.*;
import java.util.logging.Logger;

import construction.ClarkeWright;
import datastructures.Route;
import datastructures.VRPProblem;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import localsearch.LocalSearch;


public class KGLS {

    private VRPProblem vrpInstance;
    private CostEvaluator costEvaluator;
    private VRPSolution curSolution;
    private VRPSolution bestSolution;
    private double bestSolutionCost;
    private double lastResetValue;
    private int iteration;
    private long startTimeMillis;
    private long maxRuntimeMillis;
    private long bestSolutionTime;
    private List<Map<String, Object>> runStats = new ArrayList<>();

    private Map<String, Object> runParameters;

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    // Default parameters
    private static final Map<String, Object> DEFAULT_PARAMETERS = new HashMap<>();
    static {
        DEFAULT_PARAMETERS.put("depth_lin_kernighan", 5);
        DEFAULT_PARAMETERS.put("depth_relocation_chain", 3);
        DEFAULT_PARAMETERS.put("num_perturbations", 3);
        DEFAULT_PARAMETERS.put("neighborhood_size", 20);
        DEFAULT_PARAMETERS.put("moves",
                Arrays.asList("segment_move", "cross_exchange", "relocation_chain"));
    }

    public KGLS(VRPProblem instance, long maxRuntimeSeconds, Map<String, Object> userParams) {
        this.vrpInstance = instance;

        // merge userParams into defaults
        this.runParameters = new HashMap<>(DEFAULT_PARAMETERS);
        if (userParams != null) {
            for (Map.Entry<String, Object> entry : userParams.entrySet()) {
                if (!DEFAULT_PARAMETERS.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Invalid parameter: " + entry.getKey());
                }
                this.runParameters.put(entry.getKey(), entry.getValue());
            }
        }

        this.costEvaluator = new CostEvaluator(
                vrpInstance.getNodes(),
                vrpInstance.getCapacity(),
                runParameters
        );
        this.bestSolutionCost = Double.POSITIVE_INFINITY;
        this.lastResetValue =Double.POSITIVE_INFINITY;
        this.maxRuntimeMillis = maxRuntimeSeconds * 1000;
    }

    /** Run the KGLS loop */
    public void run() {
        startTimeMillis = System.currentTimeMillis();

        // construct initial solution
        curSolution = ClarkeWright.clarkeWrightRouteReduction(vrpInstance, costEvaluator);

        // improve initial solution
        LocalSearch.improveSolution(
            curSolution, costEvaluator, new HashSet<>(curSolution.getRoutes()), runParameters
        );

        updateRunStats();

        
        iteration = 0;
        while (!shouldAbort()) {
            iteration++;

            // Perturbation (shake current solution a little)
            Set<Route> changedRoutes = LocalSearch.perturbateSolution(
                    curSolution, costEvaluator, runParameters
            );

            // Local search improvement
            LocalSearch.improveSolution(
                    curSolution, costEvaluator, changedRoutes, runParameters
            );

            updateRunStats();

            if ((System.currentTimeMillis() - bestSolutionTime) > maxRuntimeMillis / 5  && bestSolutionCost < lastResetValue) {

                logger.fine("Resetting solution and penalties...");
                lastResetValue = bestSolutionCost;
                resetToBestSolution();
                bestSolutionTime = System.currentTimeMillis();
            }
        }

        // print solver stats
        printStats();
    }

    private void printStats(){
        Map<String, Double> stats = curSolution.getAllStats();

        if (stats != null && !stats.isEmpty()) {
            logger.info("=== Solution Stats ===");

            // number of moves
            logger.info("Move Count");
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                if (entry.getKey().startsWith("move_count")) { 
                    String formatted = String.format("%-30s : %.0f", entry.getKey(), entry.getValue());
                    logger.info(formatted);
                }
            }

            // time of moves
            logger.info("Time Distribution");
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                if (entry.getKey().startsWith("time_")) { 
                    Double time_percent = entry.getValue() / (this.maxRuntimeMillis) * 100;
                    String formatted = String.format("%-30s : %.0f", entry.getKey(), time_percent);
                    logger.info(formatted + " %");
                }
            }
        }
    }

    private void updateRunStats() {
        double currentCost = costEvaluator.getSolutionCosts(curSolution, false);

        if (currentCost < bestSolutionCost) {
            bestSolutionCost = currentCost;
            bestSolution = curSolution.copy();
            bestSolutionTime = System.currentTimeMillis();
            logger.info("Iteration " + iteration + " (" + (bestSolutionTime - startTimeMillis) / 1000 + "s): " + currentCost);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("iteration", iteration);
        stats.put("runtime", getRuntimeSeconds());
        stats.put("cost", currentCost);
        stats.put("bestCost", bestSolutionCost);
        runStats.add(stats);
    }

    private boolean shouldAbort() {
        return (System.currentTimeMillis() - startTimeMillis) >= maxRuntimeMillis;
    }

    private long getRuntimeSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    public VRPSolution getBestSolution() {
        return bestSolution;
    }

    private void resetToBestSolution() {
        if (bestSolution == null) return;
    
        // Save stats from current solution
        Map<String, Double> stats = new HashMap<>(curSolution.getAllStats());
    
        // Reset current solution to the best known one
        curSolution = bestSolution.copy();
        curSolution.setStats(stats);
    
        // Reset penalties in evaluator
        costEvaluator.resetPenalties();
    }
}

package localsearch;
import datastructures.Node;
import datastructures.Route;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;
import java.util.logging.Logger;

public class CrossExchangeOperator {

    private static final Logger logger = Logger.getLogger(CrossExchangeOperator.class.getName());

    // === Inner class representing the move ===
    public static class CrossExchange implements LocalSearchMove {
        private final List<Node> segment1;
        private final List<Node> segment2;
        private final Node segment1InsertAfter;
        private final Node segment2InsertAfter;
        private final Route route1;
        private final Route route2;
        private final double improvement;
        private final Node startNode;

        public CrossExchange(List<Node> segment1,
                             List<Node> segment2,
                             Node segment1InsertAfter,
                             Node segment2InsertAfter,
                             Route route1,
                             Route route2,
                             double improvement,
                             Node startNode) {
            this.segment1 = segment1;
            this.segment2 = segment2;
            this.segment1InsertAfter = segment1InsertAfter;
            this.segment2InsertAfter = segment2InsertAfter;
            this.route1 = route1;
            this.route2 = route2;
            this.improvement = improvement;
            this.startNode = startNode;
        }

        @Override
        public Set<Route> getRoutes() {
            return new HashSet<>(Arrays.asList(route1, route2));
        }

        @Override
        public boolean isDisjunct(LocalSearchMove other) {
            for (Route r : other.getRoutes()) {
                if (r.equals(route1) || r.equals(route2)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void execute(VRPSolution solution) {
            logger.fine("Executing cross-exchange with segments of sizes "
                    + segment1.size() + " and " + segment2.size()
                    + " with improvement of " + (int) improvement);

            solution.removeNodes(segment1);
            solution.removeNodes(segment2);

            solution.insertNodesAfter(segment1, segment1InsertAfter, route2);
            solution.insertNodesAfter(segment2, segment2InsertAfter, route1);
        }

        @Override
        public double getImprovement() {
            return improvement;
        }
    }

    // === Search methods ===

    public static List<CrossExchange> searchCrossExchangesFrom(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            Node startNode,
            int[] segment1Directions,
            int[] segment2Directions) {

        Route route1 = solution.routeOf(startNode);
        List<CrossExchange> candidateMoves = new ArrayList<>();

        for (int segment1Direction : segment1Directions) {
            for (int segment2Direction : segment2Directions) {

                Node route1SegmentConnectionStart = solution.neighbour(startNode, 1 - segment1Direction);

                for (Node route2SegmentConnectionStart : costEvaluator.getNeighborhood(startNode)) {
                    Route route2 = solution.routeOf(route2SegmentConnectionStart);

                    if (!route2.equals(route1)) {
                        Node segment2Start = solution.neighbour(route2SegmentConnectionStart, segment2Direction);
                        if (segment2Start.isDepot()) {
                            continue;
                        }

                        double improvementFirstCross = (
                                costEvaluator.getDistance(startNode, route1SegmentConnectionStart)
                                        + costEvaluator.getDistance(segment2Start, route2SegmentConnectionStart)
                                        - costEvaluator.getDistance(startNode, route2SegmentConnectionStart)
                                        - costEvaluator.getDistance(segment2Start, route1SegmentConnectionStart)
                        );

                        if (improvementFirstCross > 0) {
                            Node segment1End = startNode;
                            List<Node> segment1List = new ArrayList<>();
                            segment1List.add(segment1End);
                            int segment1Volume = segment1End.demand;

                            while (!segment1End.isDepot() && segment1List.size() < 40) {
                                Node segment2End = segment2Start;
                                List<Node> segment2List = new ArrayList<>();
                                segment2List.add(segment2End);
                                int segment2Volume = segment2End.demand;

                                while (!segment2End.isDepot()
                                        && costEvaluator.isFeasible(route1.getVolume() - segment1Volume + segment2Volume)) {

                                    if (costEvaluator.isFeasible(route2.getVolume() - segment2Volume + segment1Volume)) {
                                        Node route1SegmentConnectionEnd = solution.neighbour(segment1End, segment1Direction);
                                        Node route2SegmentConnectionEnd = solution.neighbour(segment2End, segment2Direction);

                                        double improvementSecondCross = (
                                                costEvaluator.getDistance(segment1End, route1SegmentConnectionEnd)
                                                        + costEvaluator.getDistance(segment2End, route2SegmentConnectionEnd)
                                                        - costEvaluator.getDistance(segment1End, route2SegmentConnectionEnd)
                                                        - costEvaluator.getDistance(segment2End, route1SegmentConnectionEnd)
                                        );

                                        double improvement = improvementFirstCross + improvementSecondCross;

                                        if (improvement > 0) {
                                            Node seg1InsertAfter = (segment2Direction == 1)
                                                    ? route2SegmentConnectionStart
                                                    : route2SegmentConnectionEnd;
                                            Node seg2InsertAfter = (segment1Direction == 1)
                                                    ? route1SegmentConnectionStart
                                                    : route1SegmentConnectionEnd;

                                            candidateMoves.add(new CrossExchange(
                                                    new ArrayList<>(segment1List),
                                                    new ArrayList<>(segment2List),
                                                    seg1InsertAfter,
                                                    seg2InsertAfter,
                                                    route1,
                                                    route2,
                                                    improvement,
                                                    startNode
                                            ));
                                        }
                                    }

                                    // Extend segment2
                                    segment2End = solution.neighbour(segment2End, segment2Direction);

                                    if ((segment2Direction == 1 && segment1Direction == 0)
                                            || (segment1Direction + segment2Direction == 0)) {
                                        segment2List.add(0, segment2End);
                                    } else {
                                        segment2List.add(segment2End);
                                    }
                                    segment2Volume += segment2End.demand;
                                }

                                // Extend segment1
                                segment1End = solution.neighbour(segment1End, segment1Direction);
                                if ((segment1Direction == 1 && segment2Direction == 0)
                                        || (segment1Direction + segment2Direction == 0)) {
                                    segment1List.add(0, segment1End);
                                } else {
                                    segment1List.add(segment1End);
                                }
                                segment1Volume += segment1End.demand;
                            }
                        }
                    }
                }
            }
        }

        return candidateMoves;
    }

    public static List<CrossExchange> searchCrossExchanges(
            VRPSolution solution,
            CostEvaluator costEvaluator,
            List<Node> startNodes) {

        List<CrossExchange> candidateMoves = new ArrayList<>();
        int[] defaultDirs = {0, 1};

        for (Node startNode : startNodes) {
            candidateMoves.addAll(
                    searchCrossExchangesFrom(solution, costEvaluator, startNode, defaultDirs, defaultDirs)
            );
        }

        // Sort by improvement descending
        candidateMoves.sort(Comparator.comparingDouble(CrossExchange::getImprovement).reversed());
        return candidateMoves;
    }
}

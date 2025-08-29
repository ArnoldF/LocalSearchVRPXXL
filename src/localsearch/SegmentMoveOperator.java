package localsearch;

import datastructures.Node;
import datastructures.Route;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;
import java.util.logging.Logger;

public class SegmentMoveOperator {

    private static final Logger logger = Logger.getLogger(SegmentMoveOperator.class.getName());

    /**
     * Move: relocating a contiguous segment of nodes into another route.
     */
    public static class SegmentMove implements LocalSearchMove {
        private final List<Node> segment;
        private final Route fromRoute;
        private final Route toRoute;
        private final Node moveAfter;
        private final double improvement;

        public SegmentMove(List<Node> segment,
                           Route fromRoute,
                           Route toRoute,
                           Node moveAfter,
                           double improvement) {
            this.segment = new ArrayList<>(segment);
            this.fromRoute = fromRoute;
            this.toRoute = toRoute;
            this.moveAfter = moveAfter;
            this.improvement = improvement;
        }

        @Override
        public void execute(VRPSolution solution) {
            logger.fine(() -> "Executing Segment relocation with segment of size "
                    + segment.size() + " and improvement " + (int) improvement);

            solution.removeNodes(segment);
            solution.insertNodesAfter(segment, moveAfter, toRoute);
        }

        @Override
        public Set<Route> getRoutes() {
            Set<Route> routes = new HashSet<>();
            routes.add(fromRoute);
            routes.add(toRoute);
            return routes;
        }

        @Override
        public boolean isDisjunct(LocalSearchMove other) {
            for (Route route : other.getRoutes()) {
                if (route.equals(fromRoute) || route.equals(toRoute)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public double getImprovement() {
            return improvement;
        }
    }

    /**
     * Search for segment relocation moves starting from one node.
     */
    public static List<SegmentMove> search3OptMovesFrom(
            VRPSolution solution,
            CostEvaluator evaluator,
            Node startNode,
            List<Integer> segmentDirections,
            List<Integer> insertDirections
    ) {
        List<SegmentMove> candidateMoves = new ArrayList<>();
        Route fromRoute = solution.routeOf(startNode);

        for (int segmentDirection : segmentDirections) {
            for (int insertDirection : insertDirections) {

                Node segment1Prev = solution.neighbour(startNode, 1 - segmentDirection);

                for (Node insertNextTo : evaluator.getNeighborhood(startNode)) {
                    Route toRoute = solution.routeOf(insertNextTo);

                    if (!toRoute.equals(fromRoute)) {
                        Node insertNextTo2 = solution.neighbour(insertNextTo, insertDirection);

                        double moveStartImprovement =
                                evaluator.getDistance(startNode, segment1Prev)
                                + evaluator.getDistance(insertNextTo, insertNextTo2)
                                - evaluator.getDistance(insertNextTo, startNode);

                        if (moveStartImprovement > 0) {
                            Node segmentEnd = startNode;
                            List<Node> segmentList = new ArrayList<>();
                            segmentList.add(segmentEnd);
                            int route2NewVolume = toRoute.getVolume() + segmentEnd.getDemand();

                            while (!segmentEnd.isDepot()
                                    && evaluator.isFeasible(route2NewVolume)) {

                                Node segmentDisconnect2 = solution.neighbour(segmentEnd, segmentDirection);

                                double moveEndImprovement =
                                        evaluator.getDistance(segmentEnd, segmentDisconnect2)
                                        - evaluator.getDistance(segment1Prev, segmentDisconnect2)
                                        - evaluator.getDistance(segmentEnd, insertNextTo2);

                                double improvement = moveStartImprovement + moveEndImprovement;

                                if (improvement > 0) {
                                    Node insertAfter = (insertDirection == 1) ? insertNextTo : insertNextTo2;

                                    candidateMoves.add(new SegmentMove(
                                            new ArrayList<>(segmentList),
                                            fromRoute,
                                            toRoute,
                                            insertAfter,
                                            improvement
                                    ));
                                }

                                // extend the segment
                                segmentEnd = segmentDisconnect2;
                                if (insertDirection == 1) {
                                    segmentList.add(segmentEnd);
                                } else {
                                    segmentList.add(0, segmentEnd);
                                }
                                route2NewVolume += segmentEnd.getDemand();
                            }
                        }
                    }
                }
            }
        }

        return candidateMoves;
    }

    /**
     * Run search from multiple nodes.
     */
    public static List<SegmentMove> search3OptMoves(
            VRPSolution solution,
            CostEvaluator evaluator,
            List<Node> startNodes
    ) {
        List<SegmentMove> allMoves = new ArrayList<>();
        for (Node startNode : startNodes) {
            allMoves.addAll(search3OptMovesFrom(
                    solution,
                    evaluator,
                    startNode,
                    Arrays.asList(0, 1),
                    Arrays.asList(0, 1)
            ));
        }
        allMoves.sort(Comparator.comparingDouble(SegmentMove::getImprovement).reversed());
        return allMoves;
    }
}

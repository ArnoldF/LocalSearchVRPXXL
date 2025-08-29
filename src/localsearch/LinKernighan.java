package localsearch;

import datastructures.Edge;
import datastructures.Node;
import datastructures.Route;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;
import java.util.logging.Logger;

public class LinKernighan {
    private static final Logger logger = Logger.getLogger(LinKernighan.class.getName());

    // ------------------------- LKEdge -------------------------
    public static class LKEdge {
        public final Node node1;
        public final Node node2;

        public LKEdge(Node n1, Node n2) {
            if (n1.getNodeId() > n2.getNodeId()) {
                this.node1 = n1;
                this.node2 = n2;
            } else {
                this.node1 = n2;
                this.node2 = n1;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(node1, node2);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LKEdge)) return false;
            LKEdge o = (LKEdge) other;
            return (o.node1.equals(node1) && o.node2.equals(node2));
        }
    }

    private static LKEdge normEdge(Node n1, Node n2) {
        return new LKEdge(n1, n2);
    }

    // ------------------------- NOptMove -------------------------
    public static class NOptMove implements LocalSearchMove {
        private final Set<LKEdge> newEdges;
        private final Set<LKEdge> removedEdges;
        private final double improvement;
        private final Node endWithNode;
        private final Route route;

        public NOptMove(Set<LKEdge> removedEdges, Set<LKEdge> newEdges,
                        double improvement, Node endWithNode, Route route) {
            this.removedEdges = new HashSet<>(removedEdges);
            this.newEdges = new HashSet<>(newEdges);
            this.improvement = improvement;
            this.endWithNode = endWithNode;
            this.route = route;
        }

        @Override
        public Set<Route> getRoutes() {
            return Collections.singleton(route);
        }

        @Override
        public boolean isDisjunct(LocalSearchMove other) {
            // TODO: define actual disjointness logic
            return false;
        }

        @Override
        public void execute(VRPSolution solution) {
            logger.fine("Executing " + removedEdges.size() + "-opt move with improvement " + (int) improvement);

            Map<Node, List<Node>> graph = new HashMap<>();
            List<Node> routeNodes = route.getNodesExceptStart();

            for (int i = 0; i < routeNodes.size(); i++) {
                Node node = routeNodes.get(i);
                List<Node> newNeighbors;

                if (node.isDepot()) {
                    newNeighbors = new ArrayList<>(Arrays.asList(
                            route.getCustomers().get(route.getCustomers().size() - 1),
                            route.getCustomers().get(0)
                    ));
                } else {
                    Node left_neighbour = solution.neighbour(node, 0);
                    Node right_neighbour = solution.neighbour(node, 1);
                    newNeighbors = new ArrayList<>(Arrays.asList(
                        left_neighbour, right_neighbour
                    ));
                }

                for (LKEdge r : removedEdges) {
                    if (r.node1.equals(node)) newNeighbors.remove(r.node2);
                    else if (r.node2.equals(node)) newNeighbors.remove(r.node1);
                }
                for (LKEdge a : newEdges) {
                    if (a.node1.equals(node)) newNeighbors.add(a.node2);
                    else if (a.node2.equals(node)) newNeighbors.add(a.node1);
                }
                graph.put(node, newNeighbors);
            }

            Node curNode = route.getDepot();
            List<Node> newRoute = new ArrayList<>();
            newRoute.add(curNode);

            while (newRoute.size() < route.getSize() + 1) {
                List<Node> neighbors = graph.get(curNode);
                assert neighbors.size() == 2;

                if (!newRoute.contains(neighbors.get(1))) {
                    curNode = neighbors.get(1);
                } else {
                    curNode = neighbors.get(0);
                }

                assert !newRoute.contains(curNode);
                newRoute.add(curNode);
            }

            newRoute.add(route.getDepot());

            solution.rearrangeRoute(route, newRoute);
        }

        public double getImprovement() {
            return improvement;
        }
    }

    // ------------------------- LKMoveSearcher -------------------------
    public static class LKMoveSearcher {
        private final List<NOptMove> validMoves = new ArrayList<>();
        private final Node endNode;
        private final Route route;
        private final int maxDepth;
        private final Map<Node, List<Map.Entry<Node, Integer>>> currentNeighbors;
        private final Map<Node, List<Map.Entry<Node, Integer>>> possibleNewNeighbors;
        private final Map<Node, Integer> completionCostsDict;
        private final int minCompletionCosts;

        public LKMoveSearcher(Route route, Node endNode, int maxDepth,
                              Map<Node, List<Map.Entry<Node, Integer>>> possibleNewNeighbors,
                              Map<Node, List<Map.Entry<Node, Integer>>> currentNeighbors,
                              Map<Node, Integer> completionCostsDict) {
            this.route = route;
            this.endNode = endNode;
            this.maxDepth = maxDepth;
            this.possibleNewNeighbors = possibleNewNeighbors;
            this.currentNeighbors = currentNeighbors;
            this.completionCostsDict = completionCostsDict;
            this.minCompletionCosts = Collections.min(completionCostsDict.values());
        }

        public void search(Node startNode, Set<LKEdge> addedEdges, Set<LKEdge> removedEdges,
                           int cumImprovement, int changesMade) {
            if (changesMade > 1) {
                int completionCost = completionCostsDict.getOrDefault(startNode, Integer.MAX_VALUE);
                if (cumImprovement - completionCost > 0) {
                    LKEdge edgeToClose = normEdge(endNode, startNode);
                    if (!addedEdges.contains(edgeToClose)) {
                        addedEdges.add(edgeToClose);

                        if (!hasSubRoutes(addedEdges, removedEdges)) {
                            validMoves.add(new NOptMove(
                                    new HashSet<>(removedEdges),
                                    new HashSet<>(addedEdges),
                                    cumImprovement - completionCost,
                                    endNode,
                                    route
                            ));
                        }
                        addedEdges.remove(edgeToClose);
                    }
                }
            }

            if (changesMade >= maxDepth) return;
            if (!validMoves.isEmpty()) return;

            for (Map.Entry<Node, Integer> candidate : possibleNewNeighbors.get(startNode)) {
                Node addEdgeTo = candidate.getKey();
                int costAdded = candidate.getValue();

                if (cumImprovement > costAdded) {
                    LKEdge edgeToAdd = normEdge(startNode, addEdgeTo);
                    if (!addedEdges.contains(edgeToAdd)) {
                        for (Map.Entry<Node, Integer> neighbor : currentNeighbors.get(addEdgeTo)) {
                            Node removeEdgeTo = neighbor.getKey();
                            int costRemoved = neighbor.getValue();

                            if (cumImprovement - costAdded + costRemoved > minCompletionCosts) {
                                LKEdge edgeToRemove = normEdge(addEdgeTo, removeEdgeTo);
                                if (!removedEdges.contains(edgeToRemove)) {

                                    HashSet addedEdges_extended = new HashSet<>(addedEdges);
                                    addedEdges_extended.add(edgeToAdd);
                                    HashSet removedEdges_extended = new HashSet<>(removedEdges);
                                    removedEdges_extended.add(edgeToRemove);

                                    search(removeEdgeTo, addedEdges_extended, removedEdges_extended,
                                            cumImprovement - costAdded + costRemoved, changesMade + 1);


                                }
                            }
                        }
                    }
                }
            }
        }

        private boolean hasSubRoutes(Set<LKEdge> addedEdges, Set<LKEdge> removedEdges) {
            Map<Node, List<Node>> graph = new HashMap<>();
            for (Node node : currentNeighbors.keySet()) {
                List<Node> newNeighbors = new ArrayList<>(Arrays.asList(
                        currentNeighbors.get(node).get(0).getKey(),
                        currentNeighbors.get(node).get(1).getKey()
                ));

                for (LKEdge r : removedEdges) {
                    if (r.node1.equals(node)) newNeighbors.remove(r.node2);
                    else if (r.node2.equals(node)) newNeighbors.remove(r.node1);
                }
                for (LKEdge a : addedEdges) {
                    if (a.node1.equals(node)) newNeighbors.add(a.node2);
                    else if (a.node2.equals(node)) newNeighbors.add(a.node1);
                }
                graph.put(node, newNeighbors);
            }

            Set<Node> visited = new HashSet<>();
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(endNode);

            while (!queue.isEmpty()) {
                Node node = queue.poll();
                if (!visited.contains(node)) {
                    visited.add(node);
                    for (Node neighbor : graph.get(node)) {
                        if (!visited.contains(neighbor)) queue.add(neighbor);
                    }
                }
            }
            return visited.size() != currentNeighbors.size();
        }

        public List<NOptMove> getValidMoves() {
            return validMoves;
        }
    }

    // ------------------------- Run Heuristic -------------------------
    public static void runLinKernighanHeuristic(VRPSolution solution, CostEvaluator evaluator,
                                                Route route, int maxDepth) {
        boolean moveFound = true;

        while (moveFound) {
            moveFound = false;
            Map<Node, List<Map.Entry<Node, Integer>>> neighbors =
                    getCurrentNeighbors(route, evaluator, solution);
            Map<Node, List<Map.Entry<Node, Integer>>> possibleNewNeighbors =
                    getCandidateNeighbors(route, evaluator, solution);

            List<Edge> edges = new ArrayList<>(route.getEdges());
            edges.sort((e1, e2) ->
                    Integer.compare(
                            evaluator.getDistance(e2.getFirstNode(), e2.getSecondNode()),
                            evaluator.getDistance(e1.getFirstNode(), e1.getSecondNode())
                    )
            );

            for (Edge edge : edges) {
                List<NOptMove> validMoves = new ArrayList<>();
                for (int startIndex = 0; startIndex < 2; startIndex++) {
                    Node startNode = edge.getFirstNode();
                    Node endNode = edge.getSecondNode();
                    if (startIndex == 1) {
                        startNode = edge.getSecondNode();
                        endNode = edge.getFirstNode();
                    }

                    Map<Node, Integer> completionCosts = new HashMap<>();
                    for (Node node : route.getNodesExceptStart()) {
                        if (!node.equals(endNode) &&
                                !node.equals(neighbors.get(endNode).get(0).getKey()) &&
                                !node.equals(neighbors.get(endNode).get(1).getKey())) {
                            completionCosts.put(node, evaluator.getDistance(endNode, node));
                        }
                    }

                    LKMoveSearcher searcher = new LKMoveSearcher(route, endNode, maxDepth,
                            possibleNewNeighbors, neighbors, completionCosts);

                    searcher.search(startNode,
                            new HashSet<>(),
                            new HashSet<>(Collections.singleton(normEdge(endNode, startNode))),
                            evaluator.getDistance(startNode, endNode), 1);

                    validMoves.addAll(searcher.getValidMoves());
                }

                if (!validMoves.isEmpty()) {
                    double oldCost = evaluator.getRouteCosts(route);
                    validMoves.sort(Comparator.comparingDouble(NOptMove::getImprovement).reversed());
                    NOptMove bestMove = validMoves.get(0);
                    bestMove.execute(solution);

                    double newCost = evaluator.getRouteCosts(route);
                    double improvement = oldCost - newCost;

                    assert Math.abs(improvement - bestMove.getImprovement()) < 1e-6;

                    solution.validate();
                    solution.addStat("move_count_linKernighan", 1.0);

                    moveFound = true;
                    break;
                }
            }
        }
    }

    // ------------------------- Helpers -------------------------
    public static Map<Node, List<Map.Entry<Node, Integer>>> getCandidateNeighbors(
            Route route, CostEvaluator evaluator, VRPSolution solution) {
        Map<Node, List<Map.Entry<Node, Integer>>> possible = new HashMap<>();
        Node depot = route.getDepot();

        List<Map.Entry<Node, Integer>> depotNeighbors = new ArrayList<>();
        for (Node customer : route.getCustomers()) {
            depotNeighbors.add(new AbstractMap.SimpleEntry<>(customer, evaluator.getDistance(depot, customer)));
        }
        possible.put(depot, depotNeighbors);

        for (int i = 0; i < route.getCustomers().size(); i++) {
            Node customer = route.getCustomers().get(i);
            List<Map.Entry<Node, Integer>> nearest = new ArrayList<>();
            
            Node left_neighbour = solution.neighbour(customer, 0);
            Node right_neighbour = solution.neighbour(customer, 1);
            for (Node node : route.getNodesExceptStart()) {
                if (!node.equals(customer) &&
                        !node.equals(left_neighbour) &&
                        !node.equals(right_neighbour)) {
                    nearest.add(new AbstractMap.SimpleEntry<>(node, evaluator.getDistance(customer, node)));
                }
            }
            nearest.sort(Comparator.comparingInt(Map.Entry::getValue));
            possible.put(customer, nearest.subList(0, Math.min(4, nearest.size())));
        }
        return possible;
    }

    public static Map<Node, List<Map.Entry<Node, Integer>>> getCurrentNeighbors(
            Route route, CostEvaluator evaluator, VRPSolution solution) {
        Map<Node, List<Map.Entry<Node, Integer>>> neighbors = new HashMap<>();
        Node depot = route.getDepot();

        List<Node> nodes = route.getNodesExceptStart();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.isDepot()) continue;

            Node left_neighbour = solution.neighbour(node, 0);
            Node right_neighbour = solution.neighbour(node, 1);

            neighbors.put(node, Arrays.asList(
                    new AbstractMap.SimpleEntry<>(left_neighbour, evaluator.getDistance(node, left_neighbour)),
                    new AbstractMap.SimpleEntry<>(right_neighbour, evaluator.getDistance(node, right_neighbour))
            ));
        }

        neighbors.put(depot, Arrays.asList(
                new AbstractMap.SimpleEntry<>(route.getCustomers().get(route.getCustomers().size() - 1),
                        evaluator.getDistance(depot, route.getCustomers().get(route.getCustomers().size() - 1))),
                new AbstractMap.SimpleEntry<>(route.getCustomers().get(0),
                        evaluator.getDistance(depot, route.getCustomers().get(0)))
        ));

        return neighbors;
    }
}

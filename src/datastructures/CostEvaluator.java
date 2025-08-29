package datastructures;
import java.util.*;


class MaxHeapWithUpdate {
    private final PriorityQueue<Edge> heap;

    public MaxHeapWithUpdate(List<Edge> elements) {
        this.heap = new PriorityQueue<>();
        this.heap.addAll(elements);
    }

    public Edge getMaxElement() {
        return heap.poll(); // pops largest
    }

    public void insertElement(Edge element) {
        heap.add(element);
    }

    public List<Edge> getSortedList() {
        List<Edge> result = new ArrayList<>(heap);
        result.sort(Comparator.reverseOrder());
        return result;
    }
}

/**
 * CostEvaluator: computes distances, neighborhoods, penalization logic
 */
public class CostEvaluator {
    private boolean penalizationEnabled = false;
    private final Map<Edge, Integer> edgePenalties = new HashMap<>();
    private double baselineCost = 0.0;
    private MaxHeapWithUpdate edgeRanking;

    private final int neighborhoodSize;
    private final int capacity;

    private final Map<Integer, Map<Integer, Integer>> costs = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> penalizedCosts = new HashMap<>();
    private final Map<Node, List<Node>> neighborhood = new HashMap<>();
    private final Map<Node, List<Node>> advancedNeighborhood = new HashMap<>();

    private String penalizationCriterium;

    public CostEvaluator(List<Node> nodes, int capacity, Map<String, Object> runParameters) {
        this.capacity = capacity;
        this.neighborhoodSize = (int) runParameters.get("neighborhood_size");

        // Compute Euclidean distances and nearest neighbors
        for (Node node1 : nodes) {
            Map<Integer, Integer> innerCosts = new HashMap<>();
            for (Node node2 : nodes) {
                innerCosts.put(node2.getNodeId(), computeEuclideanDistance(node1, node2));
            }

            // sort and keep only 100 nearest
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(innerCosts.entrySet());
            sorted.sort(Map.Entry.comparingByValue());

            Map<Integer, Integer> limited = new HashMap<>();
            int limit = Math.min(100, sorted.size());
            for (int i = 0; i < limit; i++) {
                limited.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            }

            // always keep depot distances
            for (Node depot : nodes) {
                if (depot.isDepot()) {
                    limited.put(depot.getNodeId(), innerCosts.get(depot.getNodeId()));
                }
            }

            costs.put(node1.getNodeId(), limited);
        }

        // Initialize penalized costs same as costs
        for (Node node1 : nodes) {
            penalizedCosts.put(node1.getNodeId(), new HashMap<>());
            for (Integer node2Id : costs.get(node1.getNodeId()).keySet()) {
                penalizedCosts.get(node1.getNodeId()).put(node2Id, costs.get(node1.getNodeId()).get(node2Id));
            }
        }

        // neighborhoods
        this.neighborhood.putAll(computeNeighborhood(nodes, neighborhoodSize));
        this.advancedNeighborhood.putAll(computeNeighborhood(nodes, 100));

        // baseline cost
        double total = 0.0;
        int count = 0;
        for (Node node : nodes) {
            if (!node.isDepot()) {
                for (Node other : this.neighborhood.get(node)) {
                    total += getDistance(node, other);
                    count++;
                }
            }
        }
        this.baselineCost = count > 0 ? total / count : 0.0;

        this.penalizationCriterium = "width"; // start cycle
    }

    private int computeEuclideanDistance(Node n1, Node n2) {
        return (int) Math.round(
                Math.sqrt(Math.pow(n1.getX() - n2.getX(), 2) + Math.pow(n1.getY() - n2.getY(), 2))
        );
    }

    public void resetPenalties() {
        edgePenalties.clear();
        
        // Initialize penalized costs same as costs
        for (int node1Id : costs.keySet()) {
            penalizedCosts.put(node1Id, new HashMap<>());
            for (Integer node2Id : costs.get(node1Id).keySet()) {
                penalizedCosts.get(node1Id).put(node2Id, costs.get(node1Id).get(node2Id));
            }
        }
    }

    public List<Node> getNeighborhood(Node node) {
        return neighborhood.getOrDefault(node, new ArrayList<>());
    }

    public List<Node> getAdvancedNeighborhood(Node node) {
        return advancedNeighborhood.getOrDefault(node, new ArrayList<>());
    }

    private Map<Node, List<Node>> computeNeighborhood(List<Node> nodes, int size) {
        Map<Node, List<Node>> neigh = new HashMap<>();
        for (Node node : nodes) {
            if (!node.isDepot()) {
                neigh.put(node, getNearestNeighbors(node, nodes, size));
            }
        }

        // make symmetric
        for (Map.Entry<Node, List<Node>> entry : neigh.entrySet()) {
            Node node = entry.getKey();
            for (Node neighbor : entry.getValue()) {
                neigh.computeIfAbsent(neighbor, k -> new ArrayList<>());
                if (!neigh.get(neighbor).contains(node)) {
                    neigh.get(neighbor).add(node);
                }
            }
        }

        return neigh;
    }

    private List<Node> getNearestNeighbors(Node node, List<Node> nodes, int size) {
        List<Node> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(n -> computeEuclideanDistance(n, node)));

        List<Node> nearest = new ArrayList<>();
        Iterator<Node> it = sorted.iterator();
        while (it.hasNext() && nearest.size() < size) {
            Node candidate = it.next();
            if (!candidate.isDepot() && !candidate.equals(node)) {
                nearest.add(candidate);
            }
        }
        return nearest;
    }

    public boolean isFeasible(int demand) {
        return demand <= capacity;
    }

    public void determineEdgeBadness(List<Route> routes) {
        List<Edge> edgesInSolution = new ArrayList<>();
        for (Route route : routes) {
            double centerX = 0, centerY = 0;
            if (penalizationCriterium.equals("width") || penalizationCriterium.equals("width_length")) {
                double[] center = computeRouteCenter(route.getNodesExceptStart());
                centerX = center[0];
                centerY = center[1];
            }

            for (Edge edge : route.getEdges()) {
                double value;
                switch (penalizationCriterium) {
                    case "length":
                        value = computeEdgeLengthValue(edge);
                        break;
                    case "width":
                        value = computeEdgeWidthValue(edge, centerX, centerY, route);
                        break;
                    case "width_length":
                        value = computeEdgeWidthValue(edge, centerX, centerY, route) + computeEdgeLengthValue(edge);
                        break;
                    default:
                        value = 0.0;
                }
                int penalty = edgePenalties.getOrDefault(edge, 0);
                edge.setValue((int)(value / (1 + penalty)));
                edgesInSolution.add(edge);
            }
        }
        edgeRanking = new MaxHeapWithUpdate(edgesInSolution);

        // rotate criterium
        if (penalizationCriterium.equals("width")) penalizationCriterium = "length";
        else if (penalizationCriterium.equals("length")) penalizationCriterium = "width_length";
        else penalizationCriterium = "width";
    }

    private double computeEdgeLengthValue(Edge edge) {
        int id1 = edge.getFirstNode().getNodeId();
        int id2 = edge.getSecondNode().getNodeId();
        return costs.getOrDefault(id1, new HashMap<>()).getOrDefault(id2, computeEuclideanDistance(edge.getFirstNode(), edge.getSecondNode()));
    }

    private double computeEdgeWidthValue(Edge edge, double centerX, double centerY, Route route) {
        return computeEdgeWidth(edge, centerX, centerY, route.getDepot());
    }

    public void enablePenalization() { penalizationEnabled = true; }
    public void disablePenalization() { penalizationEnabled = false; }

    public int getDistance(Node n1, Node n2) {
        if (!penalizationEnabled) {
            return costs.get(n1.getNodeId()).getOrDefault(n2.getNodeId(), computeEuclideanDistance(n1, n2));
        } else {
            return penalizedCosts.get(n1.getNodeId()).getOrDefault(n2.getNodeId(),
                    computeEuclideanDistance(n1, n2) + (int) (0.1 * baselineCost * edgePenalties.getOrDefault(new Edge(n1, n2), 0)));
        }
    }

    public Edge getAndPenalizeWorstEdge() {
        Edge worstEdge = edgeRanking.getMaxElement();
        edgePenalties.put(worstEdge, edgePenalties.getOrDefault(worstEdge, 0) + 1);

        int id1 = worstEdge.getFirstNode().getNodeId();
        int id2 = worstEdge.getSecondNode().getNodeId();

        int baseCost = costs.get(id1).getOrDefault(id2, computeEuclideanDistance(worstEdge.getFirstNode(), worstEdge.getSecondNode()));
        int penalized = (int) Math.round(baseCost + 0.1 * baselineCost * edgePenalties.get(worstEdge));

        penalizedCosts.get(id1).put(id2, penalized);
        penalizedCosts.get(id2).put(id1, penalized);

        worstEdge.setValue(baseCost / (1 + edgePenalties.get(worstEdge)));
        edgeRanking.insertElement(worstEdge);
        return worstEdge;
    }

    public void penalize(Edge edge) {
        edgePenalties.put(edge, edgePenalties.getOrDefault(edge, 0) + 1);
    }

    public int getSolutionCosts(VRPSolution solution, boolean ignorePenalties) {
        int cost = 0;
        for (Route route : solution.getRoutes()) {
            if (route.getSize() > 0) {
                List<Node> nodes = route.getNodes();
                for (int i = 0; i < nodes.size() - 1; i++) {
                    Node n1 = nodes.get(i);
                    Node n2 = nodes.get(i + 1);
                    if (ignorePenalties) {
                        cost += costs.get(n1.getNodeId()).getOrDefault(n2.getNodeId(), computeEuclideanDistance(n1, n2));
                    } else {
                        cost += getDistance(n1, n2);
                    }
                }
            }
        }
        return cost;
    }

    public int getRouteCosts(Route route) {
        if (route.getSize() == 0) return 0;
        int cost = 0;
        List<Node> nodes = route.getNodes();
        for (int i = 0; i < nodes.size() - 1; i++) {
            Node n1 = nodes.get(i);
            Node n2 = nodes.get(i + 1);
            cost += costs.get(n1.getNodeId()).getOrDefault(n2.getNodeId(), computeEuclideanDistance(n1, n2));
        }
        return cost;
    }

    private double computeEdgeWidth(Edge edge, double cx, double cy, Node depot) {
        Node n1 = edge.getFirstNode();
        Node n2 = edge.getSecondNode();

        double distanceDepotCenter = Math.sqrt(Math.pow(depot.getX() - cx, 2) + Math.pow(depot.getY() - cy, 2));

        double dist1 = ((cy - depot.getY()) * n1.getX() - (cx - depot.getX()) * n1.getY() + (cx * depot.getY()) - (cy * depot.getX()));
        dist1 = distanceDepotCenter == 0 ? 0 : dist1 / distanceDepotCenter;

        double dist2 = ((cy - depot.getY()) * n2.getX() - (cx - depot.getX()) * n2.getY() + (cx * depot.getY()) - (cy * depot.getX()));
        dist2 = distanceDepotCenter == 0 ? 0 : dist2 / distanceDepotCenter;

        return Math.abs(dist1 - dist2);
    }

    private double[] computeRouteCenter(List<Node> nodes) {
        double sumX = 0, sumY = 0;
        for (Node n : nodes) {
            sumX += n.getX();
            sumY += n.getY();
        }
        return new double[]{sumX / nodes.size(), sumY / nodes.size()};
    }
}

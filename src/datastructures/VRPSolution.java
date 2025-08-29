package datastructures;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class VRPSolution {
    private int nextRouteIndex = 0;
    private List<Route> routes = new ArrayList<>();
    private VRPProblem problem;

    private Map<String, Double> solutionStats = new HashMap<>();

    // nodeId -> prev/next Node or owning Route
    private  Map<Integer, Node> prev = new HashMap<>();
    private  Map<Integer, Node> next = new HashMap<>();
    private  Map<Integer, Route> routeOf = new HashMap<>();

    public VRPSolution(VRPProblem problem) {
        this.problem = Objects.requireNonNull(problem, "problem");

        int n = Math.max(16, problem.getNodes().size() * 2);

        // Initialize maps with initial capacity
        this.prev = new HashMap<>(n);
        this.next = new HashMap<>(n);
        this.routeOf = new HashMap<>(n);

        for (Node c : problem.getCustomers()) {
            prev.put(c.getNodeId(), null);
            next.put(c.getNodeId(), null);
            routeOf.put(c.getNodeId(), null);
        }
        // depots will be added lazily when we link edges (like Python dicts)   
    }

    public Node prev(Node node) { return prev.get(node.getNodeId()); }
    public Node next(Node node) { return next.get(node.getNodeId()); }
    public Route routeOf(Node node) { return routeOf.get(node.getNodeId()); }

    public Node neighbour(Node node, int direction) {
        return direction == 0 ? prev(node) : next(node);
    }

    public List<Route> getRoutes() { return routes; }
    public VRPProblem getProblem() { return problem; }

    public void validate() {
        // Validate routes and capacity + ownership
        for (Route r : routes) r.validate();
        for (Route r : routes) {
            if (r.getVolume() > problem.getCapacity()) {
                throw new IllegalStateException("Capacity violation");
            }
            for (Node node : r.getCustomers()) {
                Route owner = routeOf.get(node.getNodeId());
                if (owner != r) throw new IllegalStateException("Route ownership mismatch");
            }
        }

        // check that nodes are linked correctly
        for (Route r : routes) {
            if (r.getSize() > 0) {
                List<Node> rs = r.nodes; // includes depots
                if (prev(rs.get(1)) != r.getDepot())
                    throw new IllegalStateException("Prev of first customer must be depot");
                if (next(rs.get(rs.size() - 2)) != r.getDepot())
                    throw new IllegalStateException("Next of last customer must be depot");
            }
        }

        for (Node node : problem.getNodes()) {
            if (!node.isDepot()) {
                Node p = prev(node);
                if (p != null && !p.isDepot()) {
                    Node pn = next(p);
                    if (pn != node) throw new IllegalStateException("Linking invariant (next(prev(node)) == node) failed");
                }
                Node n = next(node);
                if (n != null && !n.isDepot()) {
                    Node np = prev(n);
                    if (np != node) throw new IllegalStateException("Linking invariant (prev(next(node)) == node) failed");
                }
            }
        }

        // All customers have been visited exactly once
        Set<Node> visited = new HashSet<>();
        for (Route r : routes) visited.addAll(r.getCustomers());

        if (visited.size() != problem.getCustomers().size())
            throw new IllegalStateException("Not all customers have been planned or duplicates exist");
    }

    public VRPSolution copy() {
        VRPSolution copy = new VRPSolution(problem);
        for (Route r : routes) {
            copy.addRoute(r.getCustomers());
        }
        return copy;
    }

    public void toFile(String pathToFile) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(pathToFile))) {
            for (Route r : routes) {
                if (r.getSize() > 0) {
                    bw.write(r.print());
                    bw.newLine();
                }
            }
        }
    }

    public void removeNodes(List<Node> nodesToBeRemoved) {
        if (nodesToBeRemoved == null || nodesToBeRemoved.isEmpty())
            throw new IllegalArgumentException("nodesToBeRemoved must not be empty");
        Route route = routeOf(nodesToBeRemoved.get(0));
        if (route == null) throw new IllegalStateException("Nodes not in any route");

        Node prevLeftNeighbor;
        Node prevRightNeighbor;

        if (nodesToBeRemoved.size() > 1 &&
            !Objects.equals(next(nodesToBeRemoved.get(0)), nodesToBeRemoved.get(1))) {
            prevLeftNeighbor = prev.get(nodesToBeRemoved.get(nodesToBeRemoved.size() - 1).getNodeId());
            prevRightNeighbor = next.get(nodesToBeRemoved.get(0).getNodeId());
        } else {
            prevLeftNeighbor = prev.get(nodesToBeRemoved.get(0).getNodeId());
            prevRightNeighbor = next.get(nodesToBeRemoved.get(nodesToBeRemoved.size() - 1).getNodeId());
        }

        // Link neighbors around the removed segment
        if (prevLeftNeighbor != null)
            next.put(prevLeftNeighbor.getNodeId(), prevRightNeighbor);
        if (prevRightNeighbor != null)
            prev.put(prevRightNeighbor.getNodeId(), prevLeftNeighbor);

        for (Node n : nodesToBeRemoved) {
            routeOf.put(n.getNodeId(), null);
            route.removeCustomer(n);
        }
    }

    public void addRoute(List<Node> nodes) {
        Node depot = problem.getDepot();
        ArrayList<Node> routeNodes = new ArrayList<>(nodes.size() + 2);
        routeNodes.add(depot);
        routeNodes.addAll(nodes);
        routeNodes.add(depot);

        Route newRoute = new Route(routeNodes, nextRouteIndex++);
        routes.add(newRoute);

        for (int i = 0; i < routeNodes.size(); i++) {
            Node node = routeNodes.get(i);
            if (!node.isDepot()) {
                Node p = routeNodes.get(i - 1);
                Node n = routeNodes.get(i + 1);
                prev.put(node.getNodeId(), p);
                next.put(node.getNodeId(), n);
                routeOf.put(node.getNodeId(), newRoute);

                // ensure depot keys exist when used as neighbors
                prev.putIfAbsent(p.getNodeId(), null);
                next.putIfAbsent(p.getNodeId(), null);
                prev.putIfAbsent(n.getNodeId(), null);
                next.putIfAbsent(n.getNodeId(), null);
            }
        }
    }

    public void insertNodesAfter(List<Node> nodesToInsert, Node moveAfterNode, Route route) {
        if (nodesToInsert == null || nodesToInsert.isEmpty())
            throw new IllegalArgumentException("nodesToInsert must not be empty");
        if (moveAfterNode == null || route == null)
            throw new IllegalArgumentException();

        // re-link the nodes to be inserted
        for (int i = 0; i < nodesToInsert.size(); i++) {
            Node node = nodesToInsert.get(i);
            if (i + 1 < nodesToInsert.size()) {
                Node nextNode = nodesToInsert.get(i + 1);
                next.put(node.getNodeId(), nextNode);
                prev.put(nextNode.getNodeId(), node);
            }
            routeOf.put(node.getNodeId(), route);
        }

        Node oldNextNode;
        if (moveAfterNode.isDepot()) {
            oldNextNode = route.nodes.get(1);
        } else {
            oldNextNode = next(moveAfterNode);
        }

        next.put(moveAfterNode.getNodeId(), nodesToInsert.get(0));
        prev.put(nodesToInsert.get(0).getNodeId(), moveAfterNode);

        Node tail = nodesToInsert.get(nodesToInsert.size() - 1);
        next.put(tail.getNodeId(), oldNextNode);
        prev.put(oldNextNode.getNodeId(), tail);

        route.addCustomersAfter(nodesToInsert, moveAfterNode);
    }

    public void rearrangeRoute(Route route, List<Node> nodeOrder) {
        if (!nodeOrder.get(0).isDepot()) throw new IllegalArgumentException("first node has to be a depot");
        if (!nodeOrder.get(nodeOrder.size() - 1).isDepot()) throw new IllegalArgumentException("last node has to be a depot");

        for (int i = 0; i < nodeOrder.size(); i++) {
            Node node = nodeOrder.get(i);
            if (!node.isDepot()) {
                prev.put(node.getNodeId(), nodeOrder.get(i - 1));
                next.put(node.getNodeId(), nodeOrder.get(i + 1));
            }
        }
        route.nodes = new ArrayList<>(nodeOrder);
        validate();
    }

    public void addStat(String key, double value) {
        solutionStats.put(key, solutionStats.getOrDefault(key, 0.0) + value);
    }

    public double getStat(String key) {
        return solutionStats.getOrDefault(key, 0.0);
    }

    public void setStat(String key, double value) {
        solutionStats.put(key, value);
    }

    public Map<String, Double> getAllStats() {
        return solutionStats;
    }
}

package datastructures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class Route {
    private final int routeIndex;
    private final Node depot;
    // Nodes include both starting and ending depot
    List<Node> nodes; // package-private so VRPSolution can adjust as in Python

    private int size;   // number of customers (excl. depots)
    private int volume; // sum of demand for all nodes in the route (incl. depot which is usually 0)

    public Route(List<Node> nodes, int routeIndex) {
        if (nodes == null || nodes.size() < 2)
            throw new IllegalArgumentException("Route must contain at least depot-start and depot-end");
        if (!nodes.get(0).isDepot()) throw new IllegalArgumentException("First node of a route has to be a depot.");
        if (!nodes.get(nodes.size() - 1).isDepot()) throw new IllegalArgumentException("Last node of a route has to be a depot.");
        if (!nodes.get(0).equals(nodes.get(nodes.size() - 1)))
            throw new IllegalArgumentException("Start and return depot have to be the same.");

        this.routeIndex = routeIndex;
        this.depot = nodes.get(0);
        this.nodes = new ArrayList<>(nodes);

        this.size = this.nodes.size() - 2;
        int vol = 0;
        for (Node n : this.nodes) vol += n.getDemand();
        this.volume = vol;

        validate();
    }

    public int getRouteIndex() { return routeIndex; }
    public Node getDepot() { return depot; }
    public int getSize() { return size; }
    public int getVolume() { return volume; }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Node> getCustomers() {
        if (nodes.size() <= 2) return Collections.emptyList();
        return new ArrayList<>(nodes.subList(1, nodes.size() - 1));
    }

    public List<Node> getNodesExceptStart() {
        if (nodes.size() <= 1) return Collections.emptyList();
        return new ArrayList<>(nodes.subList(1, nodes.size()));
    }

    public List<Edge> getEdges() {
        List<Edge> edges = new ArrayList<>(Math.max(0, nodes.size() - 1));
        for (int i = 0; i < nodes.size() - 1; i++) {
            edges.add(new Edge(nodes.get(i), nodes.get(i + 1)));
        }
        return edges;
    }

    public void removeCustomer(Node node) {
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        if (node.isDepot()) throw new IllegalArgumentException("A depot is removed from a route");
        if (!nodes.contains(node)) throw new IllegalArgumentException("Node does not exist in route");
        nodes.remove(node);
        size -= 1;
        volume -= node.getDemand();
    }

    public void addCustomersAfter(List<Node> nodesToAdd, Node insertAfter) {
        if (insertAfter == null || nodesToAdd == null) throw new IllegalArgumentException();
        int idx = nodes.indexOf(insertAfter);
        if (idx < 0) throw new IllegalArgumentException("Customer " + insertAfter + " not found in the route.");

        // Validate inputs first to fail fast
        for (Node n : nodesToAdd) {
            if (n.isDepot()) throw new IllegalArgumentException("A depot is inserted into a route");
        }

        List<Node> newNodes = new ArrayList<>(nodes.size() + nodesToAdd.size());
        newNodes.addAll(nodes.subList(0, idx + 1));
        newNodes.addAll(nodesToAdd);
        newNodes.addAll(nodes.subList(idx + 1, nodes.size()));
        nodes = newNodes;

        for (Node n : nodesToAdd) {
            size += 1;
            volume += n.getDemand();
        }
    }

    public void reverse() {
        Collections.reverse(nodes);
    }

    public void validate() {
        if (!nodes.get(0).isDepot()) throw new IllegalStateException("First node has to be a depot.");
        if (!nodes.get(nodes.size() - 1).isDepot()) throw new IllegalStateException("Last node has to be a depot.");
        if (!nodes.get(0).equals(nodes.get(nodes.size() - 1)))
            throw new IllegalStateException("Start and return depot have to be the same.");
        if (size != nodes.size() - 2) throw new IllegalStateException("Size mismatch");
        int vol = 0;
        for (Node n : nodes) vol += n.getDemand();
        if (volume != vol) throw new IllegalStateException("Volume mismatch");
        for (int i = 1; i < nodes.size() - 1; i++) {
            if (nodes.get(i).isDepot()) throw new IllegalStateException("Depot found among customers");
        }
    }

    public String print() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append('-');
            sb.append(nodes.get(i).getNodeId());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return print();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(routeIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Route)) return false;
        Route other = (Route) obj;
        return this.routeIndex == other.routeIndex;
    }
}

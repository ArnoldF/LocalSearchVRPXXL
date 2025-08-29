package datastructures;
import java.util.ArrayList;
import java.util.List;


public final class VRPProblem {
    private final List<Node> nodes;
    private final int capacity;
    private final double bks;

    private final List<Node> customers;
    private final Node depot;

    public VRPProblem(List<Node> nodes, int capacity) {
        this(nodes, capacity, Double.POSITIVE_INFINITY);
    }

    public VRPProblem(List<Node> nodes, int capacity, double bks) {
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("Nodes must not be empty");
        this.nodes = new ArrayList<>(nodes);
        this.capacity = capacity;
        this.bks = bks;

        List<Node> cust = new ArrayList<>();
        Node dep = null;
        for (Node n : nodes) {
            if (n.isDepot()) {
                if (dep == null) dep = n;
            } else {
                cust.add(n);
            }
        }
        if (dep == null) throw new IllegalArgumentException("At least one depot is required");
        this.customers = cust;
        this.depot = dep;
    }

    public List<Node> getNodes() { return new ArrayList<>(nodes); }
    public List<Node> getCustomers() { return new ArrayList<>(customers); }
    public Node getDepot() { return depot; }
    public int getCapacity() { return capacity; }
    public double getBks() { return bks; }
}

package datastructures;
import java.util.Objects;

public final class Edge implements Comparable<Edge> {
    private final Node first;   // max nodeId first (ties allowed)
    private final Node second;  // other node
    private int value;

    public Edge(Node n1, Node n2) {
        this(n1, n2, 0);
    }

    public Edge(Node n1, Node n2, int value) {
        if (n1 == null || n2 == null) throw new IllegalArgumentException("Nodes cannot be null");
        // get_sorted_tuple: place node with >= nodeId first
        if (n1.getNodeId() >= n2.getNodeId()) {
            this.first = n1;
            this.second = n2;
        } else {
            this.first = n2;
            this.second = n1;
        }
        this.value = value;
    }

    public int getValue() { return value; }
    public Node getFirstNode() { return first; }
    public Node getSecondNode() { return second; }

    public void setValue(int value) {this.value = value;}

    public boolean hasDepot() {
        return first.isDepot() || second.isDepot();
    }

    public boolean contains(Node node) {
        return first.equals(node) || second.equals(node);
    }

    public Node otherNode(Node node) {
        if (!contains(node)) return null;
        return first.equals(node) ? second : first;
    }

    // Python __lt__ was "self.value > other.value" (descending by value)
    @Override
    public int compareTo(Edge other) {
        return Integer.compare(other.value, this.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge edge = (Edge) o;
        return first.equals(edge.first) && second.equals(edge.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "Edge(" + first + ", " + second + ")";
    }
}

package datastructures;


public final class Node implements Comparable<Node> {
    public final int nodeId;
    public final double xCoordinate;
    public final double yCoordinate;
    public final int demand;
    public final boolean depot;

    public Node(int nodeId, double xCoordinate, double yCoordinate, int demand, boolean isDepot) {
        this.nodeId = nodeId;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.demand = demand;
        this.depot = isDepot;
    }

    public int getNodeId() { return nodeId; }
    public double getX() { return xCoordinate; }
    public double getY() { return yCoordinate; }
    public int getDemand() { return demand; }
    public boolean isDepot() { return depot; }

    @Override
    public String toString() {
        return Integer.toString(nodeId);
    }

    // Python __lt__ was "self.node_id > other.node_id" (descending)
    @Override
    public int compareTo(Node other) {
        return Integer.compare(other.nodeId, this.nodeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return nodeId == node.nodeId;
    }

    @Override
    public int hashCode() {
        return nodeId;
    }

}
package construction;
import datastructures.Node;

public class Saving implements Comparable<Saving> {
    public final Node fromNode;
    public final Node toNode;
    public final double saving;

    public Saving(Node fromNode, Node toNode, double saving) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.saving = saving;
    }

    @Override
    public int compareTo(Saving other) {
        // Descending order (larger savings first)
        return Double.compare(other.saving, this.saving);
    }
}

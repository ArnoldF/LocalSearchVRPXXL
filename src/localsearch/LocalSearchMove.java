package localsearch;
import datastructures.Route;
import datastructures.VRPSolution;
import java.util.Set;

public interface LocalSearchMove extends Comparable<LocalSearchMove> {

    /**
     * Execute the move on the given solution.
     */
    void execute(VRPSolution solution);

    /**
     * Return the routes affected by this move.
     */
    Set<Route> getRoutes();

    /**
     * Check if this move is disjunct (independent) from another move.
     */
    boolean isDisjunct(LocalSearchMove other);

    /**
     * Get the improvement value of this move.
     */
    double getImprovement();

    /**
     * Comparison operator: higher improvement = "smaller" (so sorting gives steepest descent first).
     */
    @Override
    default int compareTo(LocalSearchMove other) {
        return Double.compare(other.getImprovement(), this.getImprovement());
    }
}

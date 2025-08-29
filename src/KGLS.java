import java.util.*;
import construction.ClarkeWright;
import datastructures.Route;
import datastructures.VRPProblem;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import localsearch.LocalSearch;
import read_write.VRPInstanceReader;


public class KGLS {

    private VRPProblem vrpInstance;
    private CostEvaluator costEvaluator;
    private VRPSolution curSolution;
    private VRPSolution bestSolution;
    private double bestSolutionCost;
    private double lastResetValue;
    private int iteration;
    private long startTimeMillis;
    private long maxRuntimeMillis;
    private long bestSolutionTime;
    private List<Map<String, Object>> runStats = new ArrayList<>();

    private Map<String, Object> runParameters;

    // Default parameters
    private static final Map<String, Object> DEFAULT_PARAMETERS = new HashMap<>();
    static {
        DEFAULT_PARAMETERS.put("depth_lin_kernighan", 5);
        DEFAULT_PARAMETERS.put("depth_relocation_chain", 3);
        DEFAULT_PARAMETERS.put("num_perturbations", 3);
        DEFAULT_PARAMETERS.put("neighborhood_size", 20);
        DEFAULT_PARAMETERS.put("moves",
                Arrays.asList("segment_move", "cross_exchange", "relocation_chain"));
    }

    public KGLS(VRPProblem instance, long maxRuntimeSeconds, Map<String, Object> userParams) {
        this.vrpInstance = instance;

        // merge userParams into defaults
        this.runParameters = new HashMap<>(DEFAULT_PARAMETERS);
        if (userParams != null) {
            for (Map.Entry<String, Object> entry : userParams.entrySet()) {
                if (!DEFAULT_PARAMETERS.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Invalid parameter: " + entry.getKey());
                }
                this.runParameters.put(entry.getKey(), entry.getValue());
            }
        }

        this.costEvaluator = new CostEvaluator(
                vrpInstance.getNodes(),
                vrpInstance.getCapacity(),
                runParameters
        );
        this.bestSolutionCost = Double.POSITIVE_INFINITY;
        this.lastResetValue =Double.POSITIVE_INFINITY;
        this.maxRuntimeMillis = maxRuntimeSeconds * 1000;
    }

    /** Run the KGLS loop */
    public void run() {
        System.out.println("Running KGLS (MaxTime = " + (maxRuntimeMillis / 1000) + "s)");
        startTimeMillis = System.currentTimeMillis();

        // Step 1: construct initial solution (Clark & Wright parallel savings)
        curSolution = ClarkeWright.clarkeWrightRouteReduction(vrpInstance, costEvaluator);

        updateRunStats();

        // Step 2: main loop
        iteration = 0;
        while (!shouldAbort()) {
            iteration++;

            // Perturbation (shake current solution a little)
            Set<Route> changedRoutes = LocalSearch.perturbateSolution(
                    curSolution, costEvaluator, runParameters
            );

            // Local search improvement
            LocalSearch.improveSolution(
                    curSolution, costEvaluator, changedRoutes, runParameters
            );

            updateRunStats();

            // Restart mechanism (similar to Python version)
            if ((System.currentTimeMillis() - bestSolutionTime) > maxRuntimeMillis / 5  && bestSolutionCost < lastResetValue) {

                // System.out.println("Resetting solution and penalties...");
                lastResetValue = bestSolutionCost;
                resetToBestSolution();
                runParameters.put("num_perturbations", 1);
                bestSolutionTime = System.currentTimeMillis();
            }
        }

        System.out.println("KGLS finished after " + getRuntimeSeconds() + "s and " + iteration + " iterations.");
        System.out.println("Best solution cost: " + bestSolutionCost);
    }

    private void updateRunStats() {
        double currentCost = costEvaluator.getSolutionCosts(curSolution, false);

        if (currentCost < bestSolutionCost) {
            bestSolutionCost = currentCost;
            bestSolution = curSolution.copy();
            bestSolutionTime = System.currentTimeMillis();
            //System.out.println("Iteration " + iteration + " (" + (bestSolutionTime - startTimeMillis) / 1000 + ") " + " improved solution: " + currentCost);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("iteration", iteration);
        stats.put("runtime", getRuntimeSeconds());
        stats.put("cost", currentCost);
        stats.put("bestCost", bestSolutionCost);
        runStats.add(stats);
    }

    private boolean shouldAbort() {
        return (System.currentTimeMillis() - startTimeMillis) >= maxRuntimeMillis;
    }

    private long getRuntimeSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    public VRPSolution getBestSolution() {
        return bestSolution;
    }

    private void resetToBestSolution() {
        if (bestSolution == null) return;
    
        // Save stats from current solution
        //Map<String, Object> stats = curSolution.getSolutionStats();
    
        // Reset current solution to the best known one
        curSolution = bestSolution.copy();
        //curSolution.setSolutionStats(stats);
    
        // Reset penalties in evaluator
        costEvaluator.resetPenalties();
    }

    // ==== MAIN for console run ====
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java KGLS <instance-file> <max-time-seconds>");
            System.exit(1);
        }

        String instancePath = "src/X-n139-k10.vrp"; //args[0];
        long maxTime = 600; //Long.parseLong(args[1]);

        try {
            VRPProblem problem = VRPInstanceReader.readVRPInstance(instancePath);

            // Example: user-provided parameters
            Map<String, Object> userParams = new HashMap<>();
            //userParams.put("moves", Arrays.asList("segment_move", "cross_exchange"));
            userParams.put("num_perturbations", 3);

            KGLS solver = new KGLS(problem, maxTime, userParams);

            solver.run();

            // Optionally: save best solution
            // solver.getBestSolution().writeToFile("best_solution.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import datastructures.VRPProblem;
import read_write.VRPInstanceReader;

import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java KGLS <instance-file> <max-time-seconds> [key=value ...]");
            System.exit(1);
        }

        Logger rootLogger = Logger.getLogger("");
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        rootLogger.addHandler(consoleHandler);
        rootLogger.setLevel(Level.INFO);

        String instancePath = args[0];
        long maxTime = Long.parseLong(args[1]);

        // Parse runParameters
        Map<String, Object> userParams = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            if (kv.length != 2) {
                System.err.println("Invalid parameter: " + args[i] + " (expected key=value)");
                System.exit(1);
            }
            String key = kv[0];
            String value = kv[1];

            // handle moves (comma-separated list) vs integers
            if (key.equals("moves")) {
                List<String> moves = Arrays.asList(value.split(","));
                userParams.put("moves", moves);
            } else {
                try {
                    int intVal = Integer.parseInt(value);
                    userParams.put(key, intVal);
                } catch (NumberFormatException e) {
                    System.err.println("Parameter " + key + " must be an integer (except moves). Got: " + value);
                    System.exit(1);
                }
            }
        }

            try {
                VRPProblem problem = VRPInstanceReader.readVRPInstance(instancePath);

                KGLS solver = new KGLS(problem, maxTime, userParams);
                solver.run();

                // Optionally: save best solution
                // solver.getBestSolution().writeToFile("best_solution.txt");
            } catch (Exception e) {
                e.printStackTrace();
            }
    }
}

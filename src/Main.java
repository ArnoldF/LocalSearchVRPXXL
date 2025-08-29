import datastructures.VRPProblem;
import read_write.VRPInstanceReader;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


// Custom formatter that prints only the message
class PlainMessageFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        return record.getMessage() + System.lineSeparator();
    }
}


public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java KGLS <instance-file> <max-time-seconds> [key=value ...]");
            System.exit(1);
        }

        try{
            Logger rootLogger = Logger.getLogger("");
            FileHandler fileHandler = new FileHandler("kgls_run.log", true);
            fileHandler.setFormatter(new PlainMessageFormatter());
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(Level.INFO);
        } catch (Exception e) {
            e.printStackTrace();
        }

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


        String fileNameWithExt = instancePath.substring(instancePath.lastIndexOf("/") + 1); 
        int dotIndex = fileNameWithExt.lastIndexOf(".");
        String fileNameWithoutExt = (dotIndex == -1) ? fileNameWithExt : fileNameWithExt.substring(0, dotIndex);


        logger.info("Solving " + fileNameWithoutExt + " with KGLS (MaxTime = " + (maxTime) + "s)");

        try {
            VRPProblem problem = VRPInstanceReader.readVRPInstance(instancePath);

            KGLS solver = new KGLS(problem, maxTime, userParams);
            solver.run(); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package read_write;
import datastructures.Node;
import datastructures.VRPProblem;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Utility class to read VRP instances (.vrp) and best known solutions (.sol).
 */
public final class VRPInstanceReader {

    private VRPInstanceReader() {
        // Utility class, no instantiation
    }

    public static VRPProblem readVRPInstance(String filePath) throws IOException {
        Map<Integer, Map<String, Object>> nodes = new LinkedHashMap<>();
        int capacity = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String currentSection = null;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("CAPACITY")) {
                    capacity = Integer.parseInt(line.split(":")[1].trim());
                } else if (!Character.isDigit(line.charAt(0))) {
                    currentSection = line;
                    continue;
                } else if ("NODE_COORD_SECTION".equals(currentSection)) {
                    String[] parts = line.split("\\s+");
                    int nodeId = Integer.parseInt(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", nodeId);
                    entry.put("x", x);
                    entry.put("y", y);
                    nodes.put(nodeId, entry);
                } else if ("DEMAND_SECTION".equals(currentSection)) {
                    String[] parts = line.split("\\s+");
                    int nodeId = Integer.parseInt(parts[0]);
                    int demand = Integer.parseInt(parts[1]);
                    Map<String, Object> entry = nodes.get(nodeId);
                    if (entry == null) {
                        throw new IllegalStateException("Demand specified for unknown node: " + nodeId);
                    }
                    entry.put("demand", demand);
                } else if (line.equals("EOF")) {
                    break;
                }
            }
        }

        // read best known solution if available
        double bestSolution;
        String solFilePath = filePath.replace(".vrp", ".sol");
        File solFile = new File(solFilePath);
        if (solFile.exists()) {
            bestSolution = readBestKnownSolution(solFilePath);
        } else {
            bestSolution = Double.POSITIVE_INFINITY;
        }

        List<Node> vrpNodes = new ArrayList<>();
        for (Map<String, Object> entry : nodes.values()) {
            int id = (Integer) entry.get("id");
            double x = (Double) entry.get("x");
            double y = (Double) entry.get("y");
            int demand = (Integer) entry.get("demand");
            boolean isDepot = (demand == 0);
            vrpNodes.add(new Node(id, x, y, demand, isDepot));
        }

        return new VRPProblem(vrpNodes, capacity, bestSolution);
    }

    public static double readBestKnownSolution(String filePath) throws IOException {
        double cost = Double.POSITIVE_INFINITY;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Cost")) {
                    String[] parts = line.split("\\s+");
                    cost = Double.parseDouble(parts[1]);
                }
            }
        }
        return cost;
    }
}

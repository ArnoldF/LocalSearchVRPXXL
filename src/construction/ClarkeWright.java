package construction;
import datastructures.Node;
import datastructures.VRPProblem;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;

public final class ClarkeWright {

    ClarkeWright() {
        // utility class
    }

    public static List<Saving> computeSavings(List<Node> customers, Node depot, CostEvaluator evaluator) {
        List<Saving> savingsList = new ArrayList<>();

        for (Node node1 : customers) {
            for (Node node2 : evaluator.getAdvancedNeighborhood(node1)) {
                double saving = evaluator.getDistance(node1, depot)
                        + evaluator.getDistance(node2, depot)
                        - evaluator.getDistance(node1, node2);

                savingsList.add(new Saving(node1, node2, saving));
            }
        }

        Collections.sort(savingsList);
        return savingsList;
    }

    public static List<Saving> computeWeightedSavings(List<Node> customers, Node depot, CostEvaluator evaluator) {
        List<Saving> baseSavings = computeSavings(customers, depot, evaluator);

        double maxSaving = baseSavings.stream()
                .mapToDouble(s -> s.saving)
                .max().orElse(1.0);

        List<Integer> demands = customers.stream().map(n -> n.demand).sorted().toList();
        int maxDemand = demands.get(demands.size() - 1) + demands.get(demands.size() - 2);

        List<Saving> weightedSavings = new ArrayList<>();
        for (Node node1 : customers) {
            for (Node node2 : evaluator.getAdvancedNeighborhood(node1)) {
                double saving = evaluator.getDistance(node1, depot)
                        + evaluator.getDistance(node2, depot)
                        - evaluator.getDistance(node1, node2);

                double weighted = saving / maxSaving + (node1.demand + node2.demand) / (double) maxDemand;
                weightedSavings.add(new Saving(node1, node2, weighted));
            }
        }

        Collections.sort(weightedSavings);
        return weightedSavings;
    }

    public static VRPSolution clarkeWrightParallel(VRPProblem instance, CostEvaluator evaluator,
                                                   boolean demandWeighted) {
        System.out.println("Starting Clarke-Wright savings heuristic...");

        List<Saving> savingsList = demandWeighted
                ? computeWeightedSavings(instance.getCustomers(), instance.getDepot(), evaluator)
                : computeSavings(instance.getCustomers(), instance.getDepot(), evaluator);

        Set<Node> notPlanned = new HashSet<>(instance.getCustomers());
        Set<Node> canBeExtended = new HashSet<>();
        Set<Node> cannotBeExtended = new HashSet<>();

        VRPSolution solution = new VRPSolution(instance);

        for (Saving saving : savingsList) {
            Node node1 = saving.fromNode;
            Node node2 = saving.toNode;

            if (cannotBeExtended.contains(node1) || cannotBeExtended.contains(node2)) {
                continue;
            } else if (notPlanned.contains(node1) && notPlanned.contains(node2)) {
                if (node1.demand + node2.demand <= instance.getCapacity()) {
                    solution.addRoute(Arrays.asList(node1, node2));
                    notPlanned.remove(node1);
                    notPlanned.remove(node2);
                    canBeExtended.add(node1);
                    canBeExtended.add(node2);
                }
            } else if (canBeExtended.contains(node1) && notPlanned.contains(node2)) {
                var route1 = solution.routeOf(node1);
                if (route1.getVolume() + node2.demand <= instance.getCapacity()) {
                    if (solution.prev(node1).isDepot()) {
                        solution.insertNodesAfter(Collections.singletonList(node2), solution.prev(node1), route1);
                    } else {
                        solution.insertNodesAfter(Collections.singletonList(node2), node1, route1);
                    }
                    canBeExtended.remove(node1);
                    notPlanned.remove(node2);
                    cannotBeExtended.add(node1);
                    canBeExtended.add(node2);
                }
            } else if (canBeExtended.contains(node2) && notPlanned.contains(node1)) {
                var route2 = solution.routeOf(node2);
                if (route2.getVolume() + node1.demand <= instance.getCapacity()) {
                    if (solution.prev(node2).isDepot()) {
                        solution.insertNodesAfter(Collections.singletonList(node1), solution.prev(node2), route2);
                    } else {
                        solution.insertNodesAfter(Collections.singletonList(node1), node2, route2);
                    }
                    canBeExtended.remove(node2);
                    notPlanned.remove(node1);
                    cannotBeExtended.add(node2);
                    canBeExtended.add(node1);
                }
            } else if (canBeExtended.contains(node1) && canBeExtended.contains(node2)) {
                var route1 = solution.routeOf(node1);
                var route2 = solution.routeOf(node2);

                if (!route1.equals(route2) && route1.getVolume() + route2.getVolume() <= instance.getCapacity()) {
                    List<Node> route2Customers = new ArrayList<>(route2.getCustomers());
                    solution.removeNodes(route2Customers);

                    if (solution.next(node1).isDepot()) {
                        if (solution.next(node2).isDepot()) {
                            Collections.reverse(route2Customers);
                        }
                        solution.insertNodesAfter(route2Customers, node1, route1);
                    }
                    if (solution.prev(node1).isDepot()) {
                        if (solution.prev(node2).isDepot()) {
                            Collections.reverse(route2Customers);
                        }
                        solution.insertNodesAfter(route2Customers, solution.prev(node1), route1);
                    }

                    canBeExtended.remove(node1);
                    canBeExtended.remove(node2);
                    cannotBeExtended.add(node1);
                    cannotBeExtended.add(node2);
                }
            }
        }

        // Assign leftover customers to their own routes
        for (Node node : notPlanned) {
            solution.addRoute(Collections.singletonList(node));
        }

        // Remove empty routes
        solution.getRoutes().removeIf(r -> r.getSize() == 0);

        solution.validate();
        return solution;
    }

    public static VRPSolution clarkeWrightRouteReduction(VRPProblem instance, CostEvaluator evaluator) {
        VRPSolution solution = clarkeWrightParallel(instance, evaluator, false);

        int totalDemand = instance.getCustomers().stream().mapToInt(n -> n.demand).sum();
        int minimalRoutes = (int) Math.ceil(totalDemand / (double) instance.getCapacity());

        if (solution.getRoutes().size() > minimalRoutes + 1) {
            solution = clarkeWrightParallel(instance, evaluator, true);
        }

        return solution;
    }
}

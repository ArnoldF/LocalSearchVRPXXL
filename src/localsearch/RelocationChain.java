package localsearch;
import datastructures.Edge;
import datastructures.Node;
import datastructures.Route;
import datastructures.VRPSolution;
import datastructures.CostEvaluator;
import java.util.*;

// Relocation move: moving a single node between routes
class Relocation {
    Node nodeToMove;
    Route moveFromRoute;
    Route moveToRoute;
    Node curPrev;
    Node curNext;
    Node moveAfter;
    Node moveBefore;
    double improvement;
    Set<Node> forbiddenNodes;

    public Relocation(Node nodeToMove, Node curPrev, Node curNext,
                      Route moveFromRoute, Route moveToRoute,
                      Node moveAfter, Node moveBefore,
                      double improvement) {
        this.nodeToMove = nodeToMove;
        this.curPrev = curPrev;
        this.curNext = curNext;
        this.moveFromRoute = moveFromRoute;
        this.moveToRoute = moveToRoute;
        this.moveAfter = moveAfter;
        this.moveBefore = moveBefore;
        this.improvement = improvement;
        this.forbiddenNodes = new HashSet<>(Arrays.asList(
                nodeToMove, curPrev, curNext, moveAfter, moveBefore
        ));
    }
}

// Chain of relocations
class RelocationChain implements LocalSearchMove {
    List<Relocation> relocations = new ArrayList<>();
    Set<Node> forbiddenNodes = new HashSet<>();
    Set<Edge> forbiddenInsertion = new HashSet<>();
    Set<Node> relocatedNodes = new HashSet<>();
    Map<Route, Integer> demandChanges = new HashMap<>();
    double improvement = 0;

    public RelocationChain() {}

    @Override
    public Set<Route> getRoutes() {
        Set<Route> involvedRoutes = new HashSet<>();
        for (Relocation r : relocations) {
            involvedRoutes.add(r.moveFromRoute);
            involvedRoutes.add(r.moveToRoute);
        }
        return involvedRoutes;
    }

    private void addRelocation(Relocation r) {
        relocations.add(r);
        forbiddenNodes.addAll(r.forbiddenNodes);
        forbiddenInsertion.add(new Edge(r.moveAfter, r.moveBefore));
        forbiddenInsertion.add(new Edge(r.curPrev, r.nodeToMove));
        forbiddenInsertion.add(new Edge(r.nodeToMove, r.curNext));

        demandChanges.put(r.moveFromRoute,
                demandChanges.getOrDefault(r.moveFromRoute, 0) - r.nodeToMove.demand);
        demandChanges.put(r.moveToRoute,
                demandChanges.getOrDefault(r.moveToRoute, 0) + r.nodeToMove.demand);

        relocatedNodes.add(r.nodeToMove);
        improvement += r.improvement;
    }

    public boolean canInsertBetween(Node n1, Node n2) {
        return !forbiddenInsertion.contains(new Edge(n1, n2))
                && !relocatedNodes.contains(n1)
                && !relocatedNodes.contains(n2);
    }

    @Override
    public boolean isDisjunct(LocalSearchMove other) {
        if (!(other instanceof RelocationChain)) return true;
        RelocationChain o = (RelocationChain) other;
        for (Route r : this.getRoutes()) {
            if (o.getRoutes().contains(r)) return false;
        }
        return true;
    }

    public RelocationChain extend(Relocation r) {
        RelocationChain extended = new RelocationChain();
        extended.relocations = new ArrayList<>(this.relocations);
        extended.forbiddenNodes = new HashSet<>(this.forbiddenNodes);
        extended.forbiddenInsertion = new HashSet<>(this.forbiddenInsertion);
        extended.relocatedNodes = new HashSet<>(this.relocatedNodes);
        extended.demandChanges = new HashMap<>(this.demandChanges);
        extended.improvement = this.improvement;
        extended.addRelocation(r);
        return extended;
    }

    @Override
    public void execute(VRPSolution solution) {

        for (Relocation r : relocations) {
            solution.removeNodes(Collections.singletonList(r.nodeToMove));
            solution.insertNodesAfter(Collections.singletonList(r.nodeToMove), r.moveAfter, r.moveToRoute);
        }
    }

    @Override
    public double getImprovement() {
        return improvement;
    }
}

// ---------- Helper functions ----------

class RelocationChainSearch {
    public static Relocation insertNode(Node nodeToMove, double removalGain, Node insertNextTo,
                                        RelocationChain curChain, VRPSolution solution,
                                        CostEvaluator evaluator) {
        Node predecessor = solution.prev(insertNextTo);
        Node successor = solution.next(insertNextTo);

        double insertionCostBefore =
                evaluator.getDistance(nodeToMove, predecessor) +
                        evaluator.getDistance(nodeToMove, insertNextTo) -
                        evaluator.getDistance(predecessor, insertNextTo);

        double insertionCostAfter =
                evaluator.getDistance(nodeToMove, successor) +
                        evaluator.getDistance(nodeToMove, insertNextTo) -
                        evaluator.getDistance(successor, insertNextTo);

        double insertionCost;
        Node insertAfter, insertBefore;
        if (insertionCostBefore <= insertionCostAfter) {
            insertionCost = insertionCostBefore;
            insertAfter = predecessor;
            insertBefore = insertNextTo;
        } else {
            insertionCost = insertionCostAfter;
            insertAfter = insertNextTo;
            insertBefore = successor;
        }

        double costChange = removalGain - insertionCost;

        if (curChain.improvement + costChange > 0) {
            if (curChain.canInsertBetween(insertAfter, insertBefore)) {
                Route route = solution.routeOf(insertNextTo);
                return new Relocation(nodeToMove,
                        solution.prev(nodeToMove), solution.next(nodeToMove),
                        solution.routeOf(nodeToMove), route,
                        insertAfter, insertBefore,
                        costChange);
            }
        }
        return null;
    }

    public static void searchRelocationChainsFrom(List<RelocationChain> validChains,
                                                  VRPSolution solution, CostEvaluator evaluator,
                                                  Node nodeToMove, int maxDepth,
                                                  int currentDepth, RelocationChain curChain) {
        if (currentDepth >= maxDepth) return;

        if (curChain == null) curChain = new RelocationChain();

        Node curPrev = solution.prev(nodeToMove);
        Node curNext = solution.next(nodeToMove);

        double removalGain =
                evaluator.getDistance(nodeToMove, curPrev) +
                        evaluator.getDistance(nodeToMove, curNext) -
                        evaluator.getDistance(curPrev, curNext);

        Route fromRoute = solution.routeOf(nodeToMove);

        Map<Route, List<Relocation>> candidateInsertions = new HashMap<>();
        for (Node neighbour : evaluator.getNeighborhood(nodeToMove)) {
            Route toRoute = solution.routeOf(neighbour);
            if (!toRoute.equals(fromRoute) && !curChain.relocatedNodes.contains(neighbour)) {
                Relocation insertion = insertNode(nodeToMove, removalGain, neighbour, curChain, solution, evaluator);
                if (insertion != null) {
                    candidateInsertions.computeIfAbsent(toRoute, k -> new ArrayList<>()).add(insertion);
                }
            }
        }

        for (Map.Entry<Route, List<Relocation>> entry : candidateInsertions.entrySet()) {
            Route destinationRoute = entry.getKey();
            List<Relocation> insertions = entry.getValue();
            insertions.sort(Comparator.comparingDouble(r -> -r.improvement));
            Relocation bestInsertion = insertions.get(0);

            RelocationChain extended = curChain.extend(bestInsertion);
            int newRouteVolume = destinationRoute.getVolume() + extended.demandChanges.getOrDefault(destinationRoute, 0);

            if (evaluator.isFeasible(newRouteVolume)) {
                validChains.add(extended);
            } else if (extended.relocations.size() < maxDepth) {
                for (Node candidateNode : destinationRoute.getCustomers()) {
                    if (evaluator.isFeasible(newRouteVolume - candidateNode.demand)) {
                        if (!extended.forbiddenNodes.contains(candidateNode)) {
                            searchRelocationChainsFrom(validChains, solution, evaluator,
                                    candidateNode, maxDepth, currentDepth + 1, extended);
                        }
                    }
                }
            }
        }
    }

    public static List<RelocationChain> searchRelocationChains(VRPSolution solution,
                                                               CostEvaluator evaluator,
                                                               List<Node> startNodes,
                                                               int maxDepth) {
        List<RelocationChain> found = new ArrayList<>();
        for (Node startNode : startNodes) {
            searchRelocationChainsFrom(found, solution, evaluator, startNode, maxDepth, 0, null);
        }
        found.sort(Comparator.comparingDouble(r -> -r.improvement));
        return found;
    }
}

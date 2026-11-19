import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraphBuilder {
    private final Map<String, List<String>> adjacencyList = new LinkedHashMap<>();
    private final List<String> detectedCycles = new ArrayList<>();

    public void build(String rootPackage, DependencyProvider provider) throws IOException {
        adjacencyList.clear();
        detectedCycles.clear();
        Set<String> visited = new HashSet<>();
        visited.add(rootPackage);
        bfsRecursive(List.of(rootPackage), provider, visited);
    }

    public Map<String, List<String>> getAdjacencyList() {
        return adjacencyList;
    }

    public List<String> getDetectedCycles() {
        return detectedCycles;
    }

    private void bfsRecursive(
            List<String> currentLevel,
            DependencyProvider provider,
            Set<String> visited) throws IOException {
        if (currentLevel.isEmpty()) {
            return;
        }
        List<String> nextLevel = new ArrayList<>();
        for (String currentPkg : currentLevel) {
            processPackage(currentPkg, provider, visited, nextLevel);
        }
        bfsRecursive(nextLevel, provider, visited);
    }

    private void processPackage(
            String currentPkg,
            DependencyProvider provider,
            Set<String> visited,
            List<String> nextLevel) throws IOException {
        List<String> dependencies = provider.getDependencies(currentPkg);
        adjacencyList.put(currentPkg, dependencies);
        for (String dep : dependencies) {
            handleEdge(currentPkg, dep, visited, nextLevel);
        }
    }

    private void handleEdge(
            String source,
            String target,
            Set<String> visited,
            List<String> nextLevel) {
        if (visited.contains(target)) {
            String cycleEdge = source + " -> " + target;
            if (!detectedCycles.contains(cycleEdge)) {
                detectedCycles.add(cycleEdge);
            }
            return;
        }
        visited.add(target);
        nextLevel.add(target);
    }
}

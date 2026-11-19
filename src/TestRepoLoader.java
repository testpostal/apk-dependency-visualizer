import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestRepoLoader implements DependencyProvider {
    private static final int NOT_FOUND = -1;
    private static final int START_INDEX = 0;
    private static final int COLON_OFFSET = 1;
    private static final String PACKAGE_PATTERN = "[A-Z]+";

    private final Map<String, List<String>> graphData;

    public TestRepoLoader(String filePath, String rootPackage) throws IOException {
        this.graphData = new HashMap<>();
        loadFromFile(new File(filePath));
        validateRootExists(rootPackage);
    }

    @Override
    public List<String> getDependencies(String packageName) {
        return graphData.getOrDefault(packageName, List.of());
    }

    private void loadFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line.trim());
            }
        }
    }

    private void parseLine(String line) throws IOException {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int colonIndex = line.indexOf(":");
        if (colonIndex == NOT_FOUND) {
            throw new IOException("Некорректная строка в тестовом репозитории: '" + line + "'.");
        }
        String pkg = line.substring(START_INDEX, colonIndex).trim();
        validatePackageName(pkg);
        String rawDeps = line.substring(colonIndex + COLON_OFFSET).trim();
        graphData.put(pkg, parseDependencyList(rawDeps));
    }

    private List<String> parseDependencyList(String rawDeps) throws IOException {
        List<String> dependencies = new ArrayList<>();
        if (rawDeps.isEmpty()) {
            return dependencies;
        }
        for (String token : rawDeps.split("[,\\s]+")) {
            if (!token.isEmpty()) {
                validatePackageName(token);
                dependencies.add(token);
            }
        }
        return dependencies;
    }

    private void validatePackageName(String name) throws IOException {
        if (!name.matches(PACKAGE_PATTERN)) {
            throw new IOException(
                    "Имя пакета должно состоять из заглавных латинских букв: '" + name + "'.");
        }
    }

    private void validateRootExists(String rootPackage) throws IOException {
        validatePackageName(rootPackage);
        if (!graphData.containsKey(rootPackage)) {
            throw new IOException("Пакет '" + rootPackage + "' не найден в тестовом репозитории.");
        }
    }
}

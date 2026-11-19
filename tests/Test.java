import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test {
    private static final int NOT_FOUND = -1;
    private static final int START_INDEX = 0;
    private static final int COLON_OFFSET = 1;

    private static final String[] REQUIRED_KEYS = {
            "package_name", "repository_url", "repo_mode",
            "package_version", "output_image_path", "ascii_tree_output"
    };

    public static void main(String[] args) {
        System.out.println("=== ДЕМОНСТРАЦИЯ ОБРАБОТКИ ОШИБОК ДЛЯ ПАРАМЕТРОВ ===");
        runValidationTest("tests/missing_package.yaml");
        runValidationTest("tests/missing_url.yaml");

        System.out.println("\n=== ДЕМОНСТРАЦИЯ ЭТАПА 3 (ТЕСТОВЫЙ РЕПОЗИТОРИЙ) ===");
        runGraphScenario("Случай 1: Граф с общими зависимостями", "tests/repo_dag.txt", "A");
        runGraphScenario("Случай 2: Граф с циклическими зависимостями", "tests/repo_cycle.txt", "A");
        runGraphScenario("Случай 3: Изолированная компонента графа", "tests/repo_isolated.txt", "X");
        System.out.println("====================================================");
    }

    private static void runGraphScenario(String title, String repoPath, String rootPkg) {
        System.out.println("\n" + title + " (" + repoPath + ", пакет " + rootPkg + "):");
        try {
            TestRepoLoader loader = new TestRepoLoader(repoPath, rootPkg);
            DependencyGraphBuilder builder = new DependencyGraphBuilder();
            builder.build(rootPkg, loader);
            for (Map.Entry<String, List<String>> entry : builder.getAdjacencyList().entrySet()) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
            System.out.println("  Циклы/повторные ребра: " + builder.getDetectedCycles());
        } catch (IOException e) {
            System.out.println("  Ошибка сценария: " + e.getMessage());
        }
    }

    private static void runValidationTest(String path) {
        System.out.println("\nТестирование файла: " + path);
        File file = new File(path);
        Map<String, String> testConfig = parseTestYaml(file);

        if (testConfig.isEmpty() && !file.exists()) {
            return;
        }

        boolean errorFound = false;
        for (String key : REQUIRED_KEYS) {
            if (!testConfig.containsKey(key)) {
                System.out.println("[ОБРАБОТАНО УСПЕШНО]: Критическая ошибка!");
                System.out.println("Отсутствует параметр: '" + key + "'");
                errorFound = true;
            }
        }

        if (!errorFound) {
            System.out.println("[СБОЙ ТЕСТА]: Файл ошибочно признан валидным!");
        }
    }

    private static Map<String, String> parseTestYaml(File file) {
        Map<String, String> testConfig = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseYamlLine(line.trim(), testConfig);
            }
        } catch (IOException e) {
            System.out.println("Ошибка чтения: " + e.getMessage());
        }
        return testConfig;
    }

    private static void parseYamlLine(String line, Map<String, String> testConfig) {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int colonIndex = line.indexOf(":");
        if (colonIndex != NOT_FOUND) {
            String key = line.substring(START_INDEX, colonIndex).trim();
            String value = line.substring(colonIndex + COLON_OFFSET).trim();
            if (!value.isEmpty()) {
                testConfig.put(key, value);
            }
        }
    }
}

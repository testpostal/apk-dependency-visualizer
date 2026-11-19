import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String DEFAULT_CONFIG_PATH = "config.yaml";
    private static final int NOT_FOUND = -1;
    private static final int FIRST_ARG_INDEX = 0;
    private static final int START_INDEX = 0;
    private static final int QUOTE_OFFSET = 1;
    private static final int EXIT_FAILURE = 1;
    private static final int MIN_QUOTED_LENGTH = 2;

    private static final List<String> REQUIRED_KEYS = List.of(
            "package_name",
            "repository_url",
            "repo_mode",
            "package_version",
            "output_image_path",
            "ascii_tree_output"
    );

    public static void main(String[] args) {
        String configPath = args.length > FIRST_ARG_INDEX
                ? args[FIRST_ARG_INDEX]
                : DEFAULT_CONFIG_PATH;
        File configFile = new File(configPath);

        if (!configFile.exists() || !configFile.isFile()) {
            exitWithError("Конфигурационный файл '" + configPath + "' не найден.");
        }

        Map<String, String> config = parseYaml(configFile);
        validateConfig(config);
        runStageThree(config);
    }

    private static Map<String, String> parseYaml(File file) {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line.trim(), config);
            }
        } catch (IOException e) {
            exitWithError("Ошибка при чтении файла: " + e.getMessage());
        }
        return config;
    }

    private static void parseLine(String line, Map<String, String> config) {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int colonIndex = line.indexOf(":");
        if (colonIndex == NOT_FOUND) {
            exitWithError("Некорректный формат строки YAML: '" + line + "'.");
        }
        String key = line.substring(START_INDEX, colonIndex).trim();
        if (!REQUIRED_KEYS.contains(key)) {
            exitWithError("Неизвестный параметр конфигурации: '" + key + "'.");
        }
        String rawValue = stripInlineComment(line.substring(colonIndex + QUOTE_OFFSET)).trim();
        config.put(key, stripQuotes(rawValue));
    }

    private static String stripInlineComment(String value) {
        boolean inQuotes = false;
        for (int i = START_INDEX; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' || current == '\'') {
                inQuotes = !inQuotes;
            } else if (current == '#' && !inQuotes) {
                return value.substring(START_INDEX, i);
            }
        }
        return value;
    }

    private static String stripQuotes(String value) {
        boolean hasDoubleQuotes = value.startsWith("\"") && value.endsWith("\"");
        boolean hasSingleQuotes = value.startsWith("'") && value.endsWith("'");
        if (value.length() >= MIN_QUOTED_LENGTH && (hasDoubleQuotes || hasSingleQuotes)) {
            return value.substring(QUOTE_OFFSET, value.length() - QUOTE_OFFSET);
        }
        return value;
    }

    private static void validateConfig(Map<String, String> config) {
        for (String key : REQUIRED_KEYS) {
            String value = config.get(key);
            if (value == null || value.isEmpty()) {
                exitWithError("Отсутствует или пуст обязательный параметр '" + key + "'.");
            }
        }
        validateNoSpaces(config.get("package_name"), "package_name");
        validateNoSpaces(config.get("package_version"), "package_version");
        validateBoolean(config.get("repo_mode"), "repo_mode");
        validateBoolean(config.get("ascii_tree_output"), "ascii_tree_output");
        validateRepository(
                config.get("repository_url"),
                Boolean.parseBoolean(config.get("repo_mode"))
        );
        validateOutputImage(config.get("output_image_path"));
    }

    private static void validateNoSpaces(String value, String paramName) {
        if (value.contains(" ") || value.contains("\t")) {
            exitWithError("Параметр '" + paramName + "' не должен содержать пробелы.");
        }
    }

    private static void validateBoolean(String value, String paramName) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            exitWithError("Параметр '" + paramName + "' должен быть true или false.");
        }
    }

    private static void validateRepository(String repoLocation, boolean isTestMode) {
        if (isTestMode) {
            File testRepo = new File(repoLocation);
            if (!testRepo.exists() || !testRepo.isFile()) {
                exitWithError("Файл тестового репозитория не найден: '" + repoLocation + "'.");
            }
            return;
        }
        try {
            URI uri = URI.create(repoLocation);
            if (uri.getScheme() == null || uri.getHost() == null) {
                exitWithError("Некорректный URL репозитория: '" + repoLocation + "'.");
            }
        } catch (IllegalArgumentException e) {
            exitWithError("Некорректный URL репозитория: '" + repoLocation + "'.");
        }
    }

    private static void validateOutputImage(String imagePath) {
        if (!imagePath.toLowerCase().endsWith(".png")) {
            exitWithError("Выходной файл должен иметь расширение .png: '" + imagePath + "'.");
        }
    }

    private static void runStageThree(Map<String, String> config) {
        String packageName = config.get("package_name");
        try {
            DependencyProvider provider = createProvider(config);
            DependencyGraphBuilder builder = new DependencyGraphBuilder();
            builder.build(packageName, provider);
            printGraph(packageName, builder);
        } catch (IOException e) {
            exitWithError("Ошибка при построении графа зависимостей: " + e.getMessage());
        }
    }

    private static DependencyProvider createProvider(
            Map<String, String> config) throws IOException {
        boolean isTestMode = Boolean.parseBoolean(config.get("repo_mode"));
        String repoLocation = config.get("repository_url");
        String packageName = config.get("package_name");
        if (isTestMode) {
            return new TestRepoLoader(repoLocation, packageName);
        }
        ApkIndexLoader loader = new ApkIndexLoader();
        loader.loadRepository(repoLocation, packageName, config.get("package_version"));
        return loader;
    }

    private static void printGraph(String rootPackage, DependencyGraphBuilder builder) {
        System.out.println("Граф зависимостей для пакета '" + rootPackage + "':");
        for (Map.Entry<String, List<String>> entry : builder.getAdjacencyList().entrySet()) {
            String deps = entry.getValue().isEmpty()
                    ? "(нет зависимостей)"
                    : String.join(", ", entry.getValue());
            System.out.println("  " + entry.getKey() + " -> " + deps);
        }
        printCycles(builder.getDetectedCycles());
    }

    private static void printCycles(List<String> cycles) {
        if (cycles.isEmpty()) {
            System.out.println("Циклические зависимости не обнаружены.");
            return;
        }
        System.out.println("Обнаружены повторные/циклические переходы:");
        for (String cycle : cycles) {
            System.out.println("  * " + cycle);
        }
    }

    private static void exitWithError(String message) {
        System.err.println("Ошибка: " + message);
        System.exit(EXIT_FAILURE);
    }
}

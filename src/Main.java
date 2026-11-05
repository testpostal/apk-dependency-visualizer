import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String[] REQUIRED_KEYS = {
            "package_name", "repository_url", "repo_mode",
            "package_version", "output_image_path", "ascii_tree_output"
    };

    public static void main(String[] args) {
        String configPath = "config.yaml";
        File configFile = new File(configPath);

        if (!configFile.exists()) {
            System.err.println("Ошибка: Конфигурационный файл '" + configPath + "' не найден.");
            System.exit(1);
        }

        Map<String, String> config = parseYaml(configFile);
        validateConfig(config);
        printConfig(config);
    }

    private static Map<String, String> parseYaml(File file) {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int colonIndex = line.indexOf(":");
                if (colonIndex != -1) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    value = stripQuotes(value);
                    if (!value.isEmpty()) config.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка при чтении файла: " + e.getMessage());
            System.exit(1);
        }
        return config;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void validateConfig(Map<String, String> config) {
        for (String key : REQUIRED_KEYS) {
            if (!config.containsKey(key)) {
                System.err.println("Ошибка конфигурации: отсутствует параметр '" + key + "'.");
                System.exit(1);
            }
        }
    }

    private static void printConfig(Map<String, String> config) {
        System.out.println("===Параметры конфигурации===");
        for (String key : REQUIRED_KEYS) {
            System.out.println(key + ": " + config.get(key));
        }
    }
}

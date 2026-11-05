import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Test {
    private static final String[] REQUIRED_KEYS = {
            "package_name", "repository_url", "repo_mode",
            "package_version", "output_image_path", "ascii_tree_output"
    };

    public static void main(String[] args) {
        System.out.println("=== ДЕМОНСТРАЦИЯ ОБРАБОТКИ ОШИБОК ДЛЯ ПАРАМЕТРОВ ===");

        runValidationTest("tests/missing_package.yaml");
        runValidationTest("tests/missing_url.yaml");

        System.out.println("====================================================");
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
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colonIndex = line.indexOf(":");
                if (colonIndex != -1) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    if (!value.isEmpty()) {
                        testConfig.put(key, value);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка чтения: " + e.getMessage());
        }
        return testConfig;
    }
}

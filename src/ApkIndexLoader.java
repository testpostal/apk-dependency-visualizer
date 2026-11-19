import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ApkIndexLoader implements DependencyProvider {
    private static final int TAR_BLOCK_SIZE = 512;
    private static final int NAME_OFFSET = 0;
    private static final int NAME_LENGTH = 100;
    private static final int SIZE_OFFSET = 124;
    private static final int SIZE_LENGTH = 12;
    private static final int OCTAL_RADIX = 8;
    private static final int ZERO_BYTE = 0;
    private static final int PREFIX_LENGTH = 2;
    private static final String TARGET_ENTRY = "APKINDEX";

    private final Map<String, List<String>> packageDeps = new HashMap<>();
    private final Map<String, String> packageVersions = new HashMap<>();
    private final Map<String, String> virtualProviders = new HashMap<>();

    public void loadRepository(
            String repoUrl,
            String rootPackage,
            String rootVersion) throws IOException {
        byte[] indexBytes = downloadAndExtractIndex(buildIndexUrl(repoUrl));
        parseIndex(indexBytes);
        String actualVersion = packageVersions.get(rootPackage);
        if (actualVersion == null || !actualVersion.equals(rootVersion)) {
            throw new IOException(
                    "Пакет '" + rootPackage + "' версии '" + rootVersion + "' не найден.");
        }
    }

    @Override
    public List<String> getDependencies(String packageName) {
        List<String> rawDeps = packageDeps.getOrDefault(packageName, List.of());
        List<String> resolved = new ArrayList<>();
        for (String dep : rawDeps) {
            String target = virtualProviders.getOrDefault(dep, dep);
            if (!target.equals(packageName) && !resolved.contains(target)) {
                resolved.add(target);
            }
        }
        return resolved;
    }

    private String buildIndexUrl(String repoUrl) {
        if (repoUrl.endsWith("/")) {
            return repoUrl + "APKINDEX.tar.gz";
        }
        return repoUrl + "/APKINDEX.tar.gz";
    }

    private byte[] downloadAndExtractIndex(String indexUrl) throws IOException {
        try (InputStream urlStream = URI.create(indexUrl).toURL().openStream();
             GZIPInputStream gzipStream = new GZIPInputStream(urlStream)) {
            return readTarEntry(gzipStream, TARGET_ENTRY);
        } catch (IllegalArgumentException e) {
            throw new IOException("Некорректный URL индекса: " + indexUrl, e);
        }
    }

    private byte[] readTarEntry(InputStream tarStream, String targetName) throws IOException {
        byte[] header = new byte[TAR_BLOCK_SIZE];
        while (readFully(tarStream, header) == TAR_BLOCK_SIZE) {
            if (isEmptyBlock(header)) {
                break;
            }
            String entryName = extractString(header, NAME_OFFSET, NAME_LENGTH);
            long entrySize = parseOctalSize(header);
            if (targetName.equals(entryName)) {
                return tarStream.readNBytes((int) entrySize);
            }
            skipEntryData(tarStream, entrySize);
        }
        throw new IOException("Файл " + targetName + " не найден внутри архива.");
    }

    private int readFully(InputStream stream, byte[] buffer) throws IOException {
        int totalRead = ZERO_BYTE;
        while (totalRead < buffer.length) {
            int count = stream.read(buffer, totalRead, buffer.length - totalRead);
            if (count < ZERO_BYTE) {
                break;
            }
            totalRead += count;
        }
        return totalRead;
    }

    private boolean isEmptyBlock(byte[] block) {
        for (byte currentByte : block) {
            if (currentByte != ZERO_BYTE) {
                return false;
            }
        }
        return true;
    }

    private String extractString(byte[] header, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && header[end] != ZERO_BYTE) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private long parseOctalSize(byte[] header) {
        String sizeText = extractString(header, SIZE_OFFSET, SIZE_LENGTH);
        if (sizeText.isEmpty()) {
            return ZERO_BYTE;
        }
        return Long.parseLong(sizeText, OCTAL_RADIX);
    }

    private void skipEntryData(InputStream stream, long entrySize) throws IOException {
        long remainder = entrySize % TAR_BLOCK_SIZE;
        long padding = remainder == ZERO_BYTE ? ZERO_BYTE : TAR_BLOCK_SIZE - remainder;
        stream.skipNBytes(entrySize + padding);
    }

    private void parseIndex(byte[] indexBytes) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(indexBytes), StandardCharsets.UTF_8));
        Map<String, String> record = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                saveRecord(record);
                record.clear();
            } else if (line.length() >= PREFIX_LENGTH) {
                record.put(line.substring(ZERO_BYTE, PREFIX_LENGTH), line.substring(PREFIX_LENGTH));
            }
        }
        saveRecord(record);
    }

    private void saveRecord(Map<String, String> record) {
        String name = record.get("P:");
        String version = record.get("V:");
        if (name == null || version == null) {
            return;
        }
        packageVersions.put(name, version);
        packageDeps.put(name, parseTokens(record.getOrDefault("D:", "")));
        for (String provided : parseTokens(record.getOrDefault("p:", ""))) {
            virtualProviders.putIfAbsent(provided, name);
        }
    }

    private List<String> parseTokens(String rawLine) {
        List<String> items = new ArrayList<>();
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) {
            return items;
        }
        for (String token : trimmed.split("\\s+")) {
            String cleaned = cleanTokenName(token);
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
        }
        return items;
    }

    private String cleanTokenName(String token) {
        if (token.startsWith("!")) {
            return "";
        }
        return token.replaceFirst("[<>=~].*$", "");
    }
}
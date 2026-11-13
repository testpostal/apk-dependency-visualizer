import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class ApkIndexLoader {
    private static final int TAR_BLOCK_SIZE = 512;
    private static final int NAME_OFFSET = 0;
    private static final int NAME_LENGTH = 100;
    private static final int SIZE_OFFSET = 124;
    private static final int SIZE_LENGTH = 12;
    private static final int OCTAL_RADIX = 8;
    private static final int ZERO_BYTE = 0;
    private static final int PREFIX_LENGTH = 2;
    private static final String TARGET_ENTRY = "APKINDEX";

    public List<String> getDirectDependencies(
            String repoUrl,
            String packageName,
            String packageVersion) throws IOException {
        String indexUrl = buildIndexUrl(repoUrl);
        byte[] indexBytes = downloadAndExtractIndex(indexUrl);
        return findDependenciesInIndex(indexBytes, packageName, packageVersion);
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

    private List<String> findDependenciesInIndex(
            byte[] indexBytes,
            String targetName,
            String targetVersion) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(indexBytes), StandardCharsets.UTF_8));
        String currentName = "";
        String currentVersion = "";
        List<String> currentDeps = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (isTargetPackage(targetName, targetVersion, currentName, currentVersion)) {
                    return currentDeps;
                }
                currentName = "";
                currentVersion = "";
                currentDeps = new ArrayList<>();
            } else if (line.startsWith("P:")) {
                currentName = line.substring(PREFIX_LENGTH);
            } else if (line.startsWith("V:")) {
                currentVersion = line.substring(PREFIX_LENGTH);
            } else if (line.startsWith("D:")) {
                currentDeps = parseDependenciesLine(line.substring(PREFIX_LENGTH));
            }
        }
        if (isTargetPackage(targetName, targetVersion, currentName, currentVersion)) {
            return currentDeps;
        }
        throw new IOException("Пакет '" + targetName + "' версии '" + targetVersion + "' не найден.");
    }

    private boolean isTargetPackage(
            String targetName,
            String targetVersion,
            String currentName,
            String currentVersion) {
        return targetName.equals(currentName) && targetVersion.equals(currentVersion);
    }

    private List<String> parseDependenciesLine(String rawDeps) {
        List<String> dependencies = new ArrayList<>();
        String trimmed = rawDeps.trim();
        if (trimmed.isEmpty()) {
            return dependencies;
        }
        for (String item : trimmed.split("\\s+")) {
            String cleaned = cleanDependencyName(item);
            if (!cleaned.isEmpty()) {
                dependencies.add(cleaned);
            }
        }
        return dependencies;
    }

    private String cleanDependencyName(String token) {
        if (token.startsWith("!")) {
            return "";
        }
        return token.replaceFirst("[<>=~].*$", "");
    }
}

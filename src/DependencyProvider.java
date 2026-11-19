import java.io.IOException;
import java.util.List;

public interface DependencyProvider {
    List<String> getDependencies(String packageName) throws IOException;
}

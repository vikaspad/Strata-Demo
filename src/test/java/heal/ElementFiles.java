package heal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Finds the element file the tests use. The first setting found wins:
 * <ol>
 *   <li>the system property {@code -Delements.file=<path>};</li>
 *   <li>{@code elements.file=<path>} in {@code heal.properties} on the test classpath
 *       (e.g. {@code src/test/resources/heal.properties});</li>
 *   <li>{@value #DEFAULT}.</li>
 * </ol>
 * A relative path resolves against the working directory, which is the project root for Maven, Gradle and IntelliJ
 * runs. Point it at the <b>source</b> file, not the copy under {@code target/} or {@code build/}, so healed XPaths
 * land in the file you commit.
 */
public final class ElementFiles {
    public static final String PROPERTY = "elements.file";
    public static final String CONFIG = "heal.properties";
    public static final String DEFAULT = "src/test/resources/UIElements/WebElements.xml";

    private ElementFiles() {
    }

    /** The configured element file, as an absolute path. */
    public static Path configured() {
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isBlank()) {
            path = fromConfig();
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT;
        }
        return Path.of(path.trim()).toAbsolutePath().normalize();
    }

    /** Opens the configured file: {@code .properties} as {@link PropertiesElementRepository}, anything else as XML. */
    public static ElementRepository open() {
        return open(configured());
    }

    public static ElementRepository open(final Path file) {
        return file.getFileName().toString().endsWith(".properties")
                ? PropertiesElementRepository.load(file)
                : XmlElementRepository.load(file);
    }

    private static String fromConfig() {
        try (InputStream in = ElementFiles.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) {
                return null;
            }
            final Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(PROPERTY);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + CONFIG, e);
        }
    }
}

package heal;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@code .properties} element file: {@code Login.signInButton=//button[normalize-space()='Sign in']}, plus an
 * optional {@code Login.signInButton.strata=LoginForm.signInButton} naming the Strata live locator used to heal it.
 * Keep each XPath on one line, so the plugin can write the healed XPath back.
 */
public final class PropertiesElementRepository implements ElementRepository {
    private static final String STRATA = ".strata";

    private final Path file;
    private final Properties properties;
    private final Map<String, String> healed = new ConcurrentHashMap<>();

    private PropertiesElementRepository(final Path file, final Properties properties) {
        this.file = file;
        this.properties = properties;
    }

    public static PropertiesElementRepository load(final Path file) {
        final Path abs = file.toAbsolutePath().normalize();
        final Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(abs, StandardCharsets.UTF_8)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read element file " + abs, e);
        }
        for (final String key : properties.stringPropertyNames()) {
            if (!key.endsWith(STRATA)) {
                XmlElementRepository.compile(abs, key, properties.getProperty(key));
            }
        }
        return new PropertiesElementRepository(abs, properties);
    }

    @Override
    public String xpath(final String page, final String element) {
        final String key = page + "." + element;
        final String xpath = healed.getOrDefault(key, properties.getProperty(key));
        if (xpath == null) {
            throw new IllegalArgumentException("No element " + key + " in " + file);
        }
        return xpath;
    }

    @Override
    public Optional<String> strataKey(final String page, final String element) {
        return Optional.ofNullable(properties.getProperty(page + "." + element + STRATA)).map(String::trim);
    }

    @Override
    public Map<String, String> all() {
        final Map<String, String> all = new TreeMap<>();
        for (final String key : properties.stringPropertyNames()) {
            if (!key.endsWith(STRATA)) {
                all.put(key, properties.getProperty(key));
            }
        }
        return Map.copyOf(all);
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public void healed(final String page, final String element, final String xpath) {
        healed.put(page + "." + element, xpath);
    }
}

package heal;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Where a test suite keeps its locators: one XPath per {@code page.element}. Implement it for your own element-file
 * format. {@link XmlElementRepository} and {@link PropertiesElementRepository} are ready to use, and
 * {@link ElementFiles#open()} picks one by file extension.
 */
public interface ElementRepository {

    /** The XPath of {@code page.element}; throws {@link IllegalArgumentException} if there is none. */
    String xpath(String page, String element);

    /**
     * The element's Strata live-locator key ({@code Module.element}, as in {@code .strata/locator-coverage.xml}),
     * if the file names one. {@link Healer} uses it to find the replacement XPath when this one breaks.
     */
    Optional<String> strataKey(String page, String element);

    /** Every {@code Page.element} -> XPath as loaded from the file (heals not applied). */
    Map<String, String> all();

    /** The source file the locators came from. The Strata plugin writes healed XPaths back into it. */
    Path file();

    /** Uses {@code xpath} for {@code page.element} for the rest of this JVM. The file itself is not changed. */
    void healed(String page, String element, String xpath);
}

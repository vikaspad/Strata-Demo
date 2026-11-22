package heal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * An XML element file:
 *
 * <pre>{@code
 * <web-elements>
 *     <page name="Login">
 *         <element name="signInButton" xpath="//button[normalize-space()='Sign in']" strata="LoginForm.signInButton"/>
 *     </page>
 * </web-elements>
 * }</pre>
 *
 * {@code strata} is optional and names the Strata live locator used to heal the element. The file is read once, and
 * every XPath is compiled when it loads, so a typo or a duplicate name fails on the first lookup.
 */
public final class XmlElementRepository implements ElementRepository {
    private final Path file;
    private final Map<String, String> xpaths;
    private final Map<String, String> strataKeys;
    private final Map<String, String> healed = new ConcurrentHashMap<>();

    private XmlElementRepository(final Path file, final Map<String, String> xpaths, final Map<String, String> strataKeys) {
        this.file = file;
        this.xpaths = xpaths;
        this.strataKeys = strataKeys;
    }

    public static XmlElementRepository load(final Path file) {
        final Path abs = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(abs)) {
            throw new IllegalStateException("Element file " + abs + " does not exist. Set -D" + ElementFiles.PROPERTY
                    + " or " + ElementFiles.PROPERTY + " in " + ElementFiles.CONFIG);
        }
        try (InputStream in = Files.newInputStream(abs)) {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final NodeList pages = factory.newDocumentBuilder().parse(in).getDocumentElement()
                    .getElementsByTagName("page");
            final Map<String, String> xpaths = new LinkedHashMap<>();
            final Map<String, String> strata = new LinkedHashMap<>();
            for (int p = 0; p < pages.getLength(); p++) {
                final Element page = (Element) pages.item(p);
                final NodeList elements = page.getElementsByTagName("element");
                for (int e = 0; e < elements.getLength(); e++) {
                    final Element element = (Element) elements.item(e);
                    final String key = page.getAttribute("name") + "." + element.getAttribute("name");
                    final String xpath = element.getAttribute("xpath");
                    if (xpath.isBlank()) {
                        throw new IllegalStateException(key + " in " + abs + " has no xpath");
                    }
                    compile(abs, key, xpath);
                    if (xpaths.put(key, xpath) != null) {
                        throw new IllegalStateException(key + " appears twice in " + abs);
                    }
                    if (!element.getAttribute("strata").isBlank()) {
                        strata.put(key, element.getAttribute("strata").trim());
                    }
                }
            }
            return new XmlElementRepository(abs, Map.copyOf(xpaths), Map.copyOf(strata));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Cannot read " + abs + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String xpath(final String page, final String element) {
        final String key = page + "." + element;
        final String xpath = healed.getOrDefault(key, xpaths.get(key));
        if (xpath == null) {
            throw new IllegalArgumentException("No element " + key + " in " + file);
        }
        return xpath;
    }

    @Override
    public Optional<String> strataKey(final String page, final String element) {
        return Optional.ofNullable(strataKeys.get(page + "." + element));
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public void healed(final String page, final String element, final String xpath) {
        healed.put(page + "." + element, xpath);
    }

    /** Every {@code Page.element} → XPath as loaded from the file, in file order. */
    @Override
    public Map<String, String> all() {
        return xpaths;
    }

    static void compile(final Path file, final String key, final String xpath) {
        try {
            XPathFactory.newInstance().newXPath().compile(xpath);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(key + " in " + file + " is not a valid XPath: " + xpath, e);
        }
    }
}

package heal.selenium;

import heal.ElementRepository;
import heal.Healer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.decorators.Decorated;
import org.openqa.selenium.support.decorators.WebDriverDecorator;

/**
 * Self-healing for every lookup, with no change to the tests. Wrap the driver once where it is created:
 *
 * <pre>{@code
 * WebDriver driver = HealingDriver.wrap(new ChromeDriver(options), ElementFiles.open());
 * driver.findElement(WebElements.by("Login", "signInButton")).click();   // heals if the XPath finds nothing
 * }</pre>
 *
 * <p>Every {@code findElement(By.xpath(x))} on the driver, or on an element it returned, is checked against the
 * element file. When {@code x} is the XPath of exactly one {@code page.element} there, the lookup goes through
 * {@link Healer}: it runs as usual, and only if it throws {@link NoSuchElementException} under <b>Run with Locator
 * Coverage</b> is the element healed with the live Strata XPath and the lookup retried. Once healed, the old XPath
 * maps to the new one for the rest of the JVM, so a {@code By} built before the heal (a field, a page object, a
 * {@code @FindBy}) keeps working.
 *
 * <p>Not healed, and passed through unchanged:
 * <ul>
 *   <li>{@code findElements}: an empty list is the usual way to check that something is absent;</li>
 *   <li>XPaths that aren't in the element file, or appear there under more than one name, and non-XPath locators;</li>
 *   <li>template entries (a row, card or option): use {@code healer.find(page, element, value)} for those.</li>
 * </ul>
 * On a plain run the original {@link NoSuchElementException} reaches the test unchanged.
 */
public final class HealingDriver extends WebDriverDecorator<WebDriver> {

    private final ElementRepository elements;
    private final Map<String, String> keyByXpath;          // XPath as loaded -> "Page.element", unique only
    private final Map<String, String> healedXpaths = new ConcurrentHashMap<>();   // old XPath -> healed XPath
    private final Consumer<String> log;

    private HealingDriver(final ElementRepository elements, final Consumer<String> log) {
        this.elements = elements;
        this.keyByXpath = uniqueKeys(elements.all());
        this.log = once(log);
    }

    /** {@code driver}, healing every element-file lookup; {@code [heal]} lines go to standard out. */
    public static WebDriver wrap(final WebDriver driver, final ElementRepository elements) {
        return wrap(driver, elements, System.out::println);
    }

    public static WebDriver wrap(final WebDriver driver, final ElementRepository elements,
            final Consumer<String> log) {
        return new HealingDriver(elements, log).decorate(driver);
    }

    @Override
    public Object call(final Decorated<?> target, final Method method, final Object[] args) throws Throwable {
        if (!"findElement".equals(method.getName()) || args == null || args.length != 1
                || !(args[0] instanceof By by)) {
            return super.call(target, method, args);
        }
        final Optional<String> xpath = xpathOf(by);
        if (xpath.isEmpty()) {
            return super.call(target, method, args);
        }
        final String healed = healedXpaths.get(xpath.get());
        if (healed != null) {
            return super.call(target, method, new Object[] {By.xpath(healed)});
        }
        final String key = keyByXpath.get(xpath.get());
        if (key == null || Healer.finding()) {   // an explicit healer.find(...) heals this lookup itself
            return super.call(target, method, args);
        }
        final int dot = key.indexOf('.');
        final String page = key.substring(0, dot);
        final String element = key.substring(dot + 1);
        final Healer<WebElement> healer = new Healer<>(elements, x -> find(target, method, x),
                e -> e instanceof NoSuchElementException, log);
        final WebElement found = healer.find(page, element);
        final String now = elements.xpath(page, element);
        if (!now.equals(xpath.get())) {
            healedXpaths.put(xpath.get(), now);
        }
        return found;
    }

    /** One lookup through the decorator, so the result is decorated too; the lookup's own exception is rethrown. */
    private WebElement find(final Decorated<?> target, final Method method, final String xpath) {
        try {
            return (WebElement) super.call(target, method, new Object[] {By.xpath(xpath)});
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new UndeclaredThrowableException(e.getTargetException());
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    /** The XPath of a {@code By.xpath}, or empty for any other locator. */
    static Optional<String> xpathOf(final By by) {
        if (by instanceof By.ByXPath xpath) {
            final By.Remotable.Parameters parameters = xpath.getRemoteParameters();
            if ("xpath".equals(parameters.using()) && parameters.value() instanceof String value) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** XPath -> key, leaving out XPaths shared by several keys: a heal there would be a guess. */
    static Map<String, String> uniqueKeys(final Map<String, String> xpathByKey) {
        final Map<String, String> keys = new HashMap<>();
        final Set<String> shared = ConcurrentHashMap.newKeySet();
        xpathByKey.forEach((key, xpath) -> {
            if (keys.putIfAbsent(xpath, key) != null) {
                shared.add(xpath);
            }
        });
        shared.forEach(keys::remove);
        return Map.copyOf(keys);
    }

    /** Each distinct line once: a WebDriverWait that polls a broken locator would repeat it every poll. */
    private static Consumer<String> once(final Consumer<String> log) {
        final Set<String> seen = ConcurrentHashMap.newKeySet();
        return line -> {
            if (seen.add(line)) {
                log.accept(line);
            }
        };
    }
}

package demo;

import heal.ElementFiles;
import heal.ElementRepository;
import heal.Healer;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

/**
 * Selenium access to the demo's element file, configured in {@code src/test/resources/heal.properties} (default
 * {@code src/test/resources/UIElements/WebElements.xml}; override with {@code -Delements.file=...}).
 *
 * <pre>{@code
 * driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys("jdoe");   // heals on its own (Browser.open)
 * WebElements.healer(driver).find("HealingDemo", "firstRowNotionalCell", 1);      // a template entry needs its row
 * }</pre>
 */
public final class WebElements {
    private static final ElementRepository ELEMENTS = ElementFiles.open();

    private WebElements() {
    }

    /** {@code By.xpath} of {@code page.element}. */
    public static By by(final String page, final String element) {
        return By.xpath(xpath(page, element));
    }

    /** The XPath of {@code page.element}; throws if the file has no such entry. */
    public static String xpath(final String page, final String element) {
        return ELEMENTS.xpath(page, element);
    }

    /** The loaded element file. */
    public static ElementRepository repository() {
        return ELEMENTS;
    }

    /** A self-healing finder over {@code context} (a driver or an element). */
    public static Healer<WebElement> healer(final SearchContext context) {
        return new Healer<>(ELEMENTS, xpath -> context.findElement(By.xpath(xpath)),
                e -> e instanceof NoSuchElementException);
    }
}

package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.strata.api.Strata;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Uses the two {@code HealingDemo} entries in {@code UIElements/WebElements.xml}, which start out deliberately broken,
 * the kind of break a UI change leaves behind:
 * <ul>
 *   <li>the Sign in button is looked up by an id it doesn't have ({@code sign-in-button});</li>
 *   <li>the grid's notional cell is looked up by a column id that was renamed ({@code notionalAmount}).</li>
 * </ul>
 * Right-click → <b>Run with Locator Coverage</b>: {@link heal.Healer} replaces each one with the live Strata XPath
 * named by the entry's {@code strata} attribute, prints a {@code [heal]} line, and the test passes. When the run ends,
 * the plugin writes the healed XPaths into WebElements.xml, so the next run (plain or not) uses them directly.
 *
 * <p>While the entries are still broken, a plain Run fails with {@code NoSuchElementException}. Tagged
 * {@code healing}, so {@code mvn test} skips it.
 */
@Tag("healing")
class SelfHealingTest {
    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void openBrowser() {
        driver = Browser.open();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void closeBrowser() {
        driver.quit();
    }

    @Test
    void brokenSignInButtonHeals() {
        driver.get(DemoSite.baseUrl() + "/login");
        // Live locators for the page the browser shows right now (empty on a plain run).
        Strata.xpaths().forEach((name, xpath) -> System.out.println("[strata] " + name + " = " + xpath));

        driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys("jdoe");
        driver.findElement(WebElements.by("HealingDemo", "signInButton")).click();
        wait.until(ExpectedConditions.titleIs("Trade Blotter"));
    }

    @Test
    void brokenGridColumnHeals() {
        driver.get(DemoSite.baseUrl() + "/grid");
        wait.until(ExpectedConditions.numberOfElementsToBe(WebElements.by("Grid", "dataRows"), 4));
        // OrdersGrid.notionalCell is a row template in Strata; row 1 is the first data row.
        final String notional = WebElements.healer(driver).find("HealingDemo", "firstRowNotionalCell", 1).getText();
        assertFalse(notional.isBlank(), "notional of the first order");
        assertEquals("Cancel", driver.findElement(WebElements.by("Grid", "secondRowCancelButton")).getText());
    }
}

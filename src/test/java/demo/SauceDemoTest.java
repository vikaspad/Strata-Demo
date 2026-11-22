package demo;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Logs in to the public practice site <a href="https://www.saucedemo.com">saucedemo.com</a> and follows "About" in the
 * left-hand menu. It needs internet access, so it's tagged {@code public} and {@code mvn test} skips it unless run with
 * {@code -Dgroups=public}.
 */
@Tag("public")
class SauceDemoTest {
    private static final String SITE = "https://www.saucedemo.com/";

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void openBrowser() {
        driver = Browser.open();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void closeBrowser() {
        driver.quit();
    }

    @Test
    void loginAndOpenAbout() {
        driver.get(SITE);
        driver.findElement(WebElements.by("SauceLogin", "usernameInput")).sendKeys("standard_user");
        driver.findElement(WebElements.by("SauceLogin", "passwordInput")).sendKeys("secret_sauce");
        driver.findElement(WebElements.by("SauceLogin", "loginButton")).click();
        wait.until(ExpectedConditions.urlContains("/inventory.html"));

        driver.findElement(WebElements.by("SauceInventory", "menuButton")).click();
        final WebElement about = wait.until(ExpectedConditions.elementToBeClickable(
                WebElements.by("SauceInventory", "aboutLink")));
        // The menu slides in; wait for it to stop so the click lands on the link's final position.
        wait.until(ExpectedConditions.jsReturnsValue(
                "return document.getAnimations().length === 0 ? true : null"));
        about.click();
        wait.until(ExpectedConditions.urlContains("saucelabs.com"));
    }
}

package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * A shopping journey on the public practice site <a href="https://www.automationexercise.com">automationexercise.com</a>:
 * navigation, product search, add to cart, the cart table, a failed login and the contact form. It needs internet
 * access, so it's tagged {@code public} and {@code mvn test} skips it unless run with {@code -Dgroups=public}.
 *
 * <p>No account is created and the contact form is not submitted. Ad and consent scripts are blocked, because their
 * full-screen ads cover the page and their constant DOM changes keep Locator Coverage's settle wait at its cap.
 */
@Tag("public")
class AutomationExerciseTest {
    private static final String SITE = "https://www.automationexercise.com";
    private static final List<String> BLOCKED = List.of("*googlesyndication.com*", "*doubleclick.net*",
            "*adservice.google.*", "*googletagservices.com*", "*fundingchoicesmessages.google.com*",
            "*google-analytics.com*", "*googletagmanager.com*");

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void openBrowser() {
        driver = Browser.open();
        ((HasCdp) driver).executeCdpCommand("Network.enable", Map.of());
        ((HasCdp) driver).executeCdpCommand("Network.setBlockedURLs", Map.of("urls", BLOCKED));
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void closeBrowser() {
        driver.quit();
    }

    @Test
    void searchAddToCartAndRemove() {
        driver.get(SITE);
        driver.findElement(WebElements.by("AeNavigation", "productsLink")).click();
        wait.until(ExpectedConditions.textToBe(WebElements.by("AeProducts", "listTitle"), "ALL PRODUCTS"));

        driver.findElement(WebElements.by("AeProducts", "searchInput")).sendKeys("Blue Top");
        driver.findElement(WebElements.by("AeProducts", "searchButton")).click();
        wait.until(ExpectedConditions.textToBe(WebElements.by("AeProducts", "listTitle"), "SEARCHED PRODUCTS"));

        driver.findElement(WebElements.by("AeProducts", "addToCartButton")).click();
        final WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                WebElements.by("AeProducts", "cartModal")));
        modal.findElement(WebElements.by("AeProducts", "viewCartLink")).click();

        final WebElement row = wait.until(ExpectedConditions.visibilityOfElementLocated(
                WebElements.by("AeCart", "productRow")));
        assertEquals("Blue Top", row.findElement(WebElements.by("AeCart", "productNameLink")).getText());
        assertEquals("1", row.findElement(WebElements.by("AeCart", "quantityButton")).getText());
        row.findElement(WebElements.by("AeCart", "deleteLink")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(WebElements.by("AeCart", "emptyCartMessage")));
    }

    @Test
    void productDetailsLoginAndContactForm() {
        driver.get(SITE + "/product_details/1");
        assertEquals("Blue Top", driver.findElement(WebElements.by("AeProductDetails", "productName")).getText());
        final WebElement quantity = driver.findElement(WebElements.by("AeProductDetails", "quantityInput"));
        quantity.clear();
        quantity.sendKeys("3");
        assertEquals("3", quantity.getDomProperty("value"));

        driver.findElement(WebElements.by("AeNavigation", "loginLink")).click();
        driver.findElement(WebElements.by("AeLogin", "emailInput")).sendKeys("nobody@example.invalid");
        driver.findElement(WebElements.by("AeLogin", "passwordInput")).sendKeys("not-a-password");
        driver.findElement(WebElements.by("AeLogin", "loginButton")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(WebElements.by("AeLogin", "loginForm"),
                "Your email or password is incorrect!"));
        assertTrue(driver.findElement(WebElements.by("AeLogin", "signupNameInput")).isEnabled());

        driver.findElement(WebElements.by("AeNavigation", "contactUsLink")).click();
        driver.findElement(WebElements.by("AeContactUs", "nameInput")).sendKeys("Strata Demo");
        driver.findElement(WebElements.by("AeContactUs", "emailInput")).sendKeys("demo@example.invalid");
        driver.findElement(WebElements.by("AeContactUs", "subjectInput")).sendKeys("Locator coverage");
        final WebElement message = driver.findElement(WebElements.by("AeContactUs", "messageTextarea"));
        message.sendKeys("Just filling the form; it is not submitted.");
        assertEquals("Locator coverage",
                driver.findElement(WebElements.by("AeContactUs", "subjectByName")).getDomProperty("value"));
        assertTrue(message.isDisplayed());
    }
}

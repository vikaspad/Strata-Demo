package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Plain JUnit 5 version of the journey in Blotter.feature. Right-click it → Run with Locator Coverage. */
class BlotterJourneyTest {
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
    void traderExportsTheBlotterAndOpensAnotherOne() {
        driver.get(DemoSite.baseUrl() + "/login");
        driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys("jdoe");
        driver.findElement(WebElements.by("Login", "passwordInput")).sendKeys("not-a-real-password");
        driver.findElement(WebElements.by("Login", "signInButton")).click();
        wait.until(ExpectedConditions.titleIs("Trade Blotter"));

        driver.findElement(WebElements.by("Blotter", "counterpartyInput")).sendKeys("Halvorsen");
        driver.findElement(WebElements.by("Blotter", "applyFiltersButton")).click();

        driver.findElement(WebElements.by("Blotter", "exportButton")).click();
        assertTrue(driver.findElement(WebElements.by("ExportDialog", "dialog")).isDisplayed());
        driver.findElement(WebElements.by("ExportDialog", "downloadButton")).click();

        driver.get(DemoSite.baseUrl() + "/blotter/99001");
        assertEquals("Blotter 99001", driver.findElement(WebElements.by("Blotter", "heading")).getText());
        driver.findElement(WebElements.by("Blotter", "allocateButton")).click();
    }
}

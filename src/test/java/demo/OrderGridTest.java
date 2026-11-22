package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Drives the AG Grid on {@code /grid}: sort, cancel an order, read a cell. With Locator Coverage the grid comes out
 * as a GRID module whose headers, rows, cells and Cancel buttons are keyed by AG Grid's {@code col-id}.
 */
class OrderGridTest {
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
    void cancelSecondOrder() {
        driver.get(DemoSite.baseUrl() + "/grid");
        wait.until(ExpectedConditions.numberOfElementsToBe(WebElements.by("Grid", "dataRows"), 4));
        driver.findElement(WebElements.by("Grid", "orderIdHeader")).click();
        driver.findElement(WebElements.by("Grid", "secondRowCancelButton")).click();
        wait.until(ExpectedConditions.textToBe(WebElements.by("Grid", "secondRowStatusCell"), "Cancelled"));
        assertEquals("Buy", driver.findElement(WebElements.by("Grid", "firstRowSideCell")).getText());
    }
}

package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

/**
 * Touches one of each component type on {@code /components}: inputs, dropdowns and options, an overlay listbox,
 * a menu, table rows and cells, icons, a paragraph, a span and a same-origin iframe. Run it with Locator Coverage
 * to see all of them in the XML, with the ones used here marked {@code used="true"}.
 */
class ComponentsTest {
    private WebDriver driver;

    @BeforeEach
    void openBrowser() {
        driver = Browser.open();
    }

    @AfterEach
    void closeBrowser() {
        driver.quit();
    }

    @Test
    void profileFormAndToolbar() {
        driver.get(DemoSite.baseUrl() + "/components");
        driver.findElement(WebElements.by("Components", "fullNameInput")).sendKeys("Jane");
        driver.findElement(WebElements.by("Components", "passwordInput")).sendKeys("not-a-real-password");
        driver.findElement(WebElements.by("Components", "bioTextarea")).sendKeys("notes");
        new Select(driver.findElement(WebElements.by("Components", "countryDropdown"))).selectByVisibleText("India");
        driver.findElement(WebElements.by("Components", "newsletterCheckbox")).click();
        driver.findElement(WebElements.by("Components", "callMeRadio")).click();
        driver.findElement(WebElements.by("Components", "downloadIcon"));
    }

    @Test
    void dropdownsAndMenus() {
        driver.get(DemoSite.baseUrl() + "/components");
        driver.findElement(WebElements.by("Components", "currencyToggle")).click();
        driver.findElement(WebElements.by("Components", "eurOption")).click();
        driver.findElement(WebElements.by("Components", "actionsToggle")).click();
        assertTrue(driver.findElement(WebElements.by("Components", "exportMenuItem")).isDisplayed());
    }

    @Test
    void positionsTableAndFrame() {
        driver.get(DemoSite.baseUrl() + "/components");
        assertEquals(3, driver.findElements(WebElements.by("Components", "positionRows")).size());
        assertEquals("410.55", driver.findElement(WebElements.by("Components", "secondPositionPriceCell")).getText());
        driver.findElement(WebElements.by("Components", "disclaimer")).getText();

        driver.switchTo().frame(driver.findElement(WebElements.by("Components", "helpFrame")));
        driver.findElement(WebElements.by("HelpFrame", "questionInput")).sendKeys("How do I export?");
        driver.switchTo().defaultContent();
    }
}

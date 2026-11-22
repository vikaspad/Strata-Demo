package demo.steps;

import demo.Browser;
import demo.WebElements;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SauceDemoSteps {
    private static final String SITE = "https://www.saucedemo.com/";

    private WebDriver driver;
    private WebDriverWait wait;

    @Before("@public")
    public void openBrowser() {
        driver = Browser.open();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @After("@public")
    public void closeBrowser() {
        driver.quit();
    }

    @Given("I open the Sauce Demo site")
    public void iOpenTheSauceDemoSite() {
        driver.get(SITE);
    }

    @When("I log in with username {string} and password {string}")
    public void iLogIn(final String username, final String password) {
        driver.findElement(WebElements.by("SauceLogin", "usernameInput")).sendKeys(username);
        driver.findElement(WebElements.by("SauceLogin", "passwordInput")).sendKeys(password);
        driver.findElement(WebElements.by("SauceLogin", "loginButton")).click();
    }

    @Then("I see the products page")
    public void iSeeTheProductsPage() {
        wait.until(ExpectedConditions.urlContains("/inventory.html"));
    }

    @When("I click About in the left menu")
    public void iClickAboutInTheLeftMenu() {
        driver.findElement(WebElements.by("SauceInventory", "menuButton")).click();
        final WebElement about = wait.until(ExpectedConditions.elementToBeClickable(
                WebElements.by("SauceInventory", "aboutLink")));
        // The menu slides in; wait for it to stop so the click lands on the link's final position.
        wait.until(ExpectedConditions.jsReturnsValue(
                "return document.getAnimations().length === 0 ? true : null"));
        about.click();
    }

    @Then("I am taken to the Sauce Labs website")
    public void iAmTakenToTheSauceLabsWebsite() {
        wait.until(ExpectedConditions.urlContains("saucelabs.com"));
    }
}

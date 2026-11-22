package demo.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import demo.Browser;
import demo.DemoSite;
import demo.WebElements;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class BlotterSteps {
    private WebDriver driver;
    private WebDriverWait wait;

    @Before("not @public")
    public void openBrowser() {
        driver = Browser.open();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @After("not @public")
    public void closeBrowser() {
        driver.quit();
    }

    @Given("I am signed in as {string}")
    public void iAmSignedInAs(final String user) {
        driver.get(DemoSite.baseUrl() + "/login");
        driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys(user);
        driver.findElement(WebElements.by("Login", "passwordInput")).sendKeys("not-a-real-password");
        driver.findElement(WebElements.by("Login", "signInButton")).click();
        wait.until(ExpectedConditions.titleIs("Trade Blotter"));
    }

    @When("I filter the blotter by counterparty {string}")
    public void iFilterByCounterparty(final String counterparty) {
        driver.findElement(WebElements.by("Blotter", "counterpartyInput")).sendKeys(counterparty);
        driver.findElement(WebElements.by("Blotter", "applyFiltersButton")).click();
    }

    @When("I export the trades")
    public void iExportTheTrades() {
        driver.findElement(WebElements.by("Blotter", "exportButton")).click();
        driver.findElement(WebElements.by("ExportDialog", "downloadButton")).click();
    }

    @Then("the export dialog is shown")
    public void theExportDialogIsShown() {
        assertTrue(driver.findElement(WebElements.by("ExportDialog", "dialog")).isDisplayed());
    }

    @When("I open blotter {string}")
    public void iOpenBlotter(final String id) {
        driver.get(DemoSite.baseUrl() + "/blotter/" + id);
    }

    @Then("I see the heading {string}")
    public void iSeeTheHeading(final String heading) {
        assertEquals(heading, driver.findElement(WebElements.by("Blotter", "heading")).getText());
    }

    @When("I allocate the trade")
    public void iAllocateTheTrade() {
        driver.findElement(WebElements.by("Blotter", "allocateButton")).click();
    }
}

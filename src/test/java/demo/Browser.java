package demo;

import heal.selenium.HealingDriver;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Chrome, headless unless the run sets {@code -Ddemo.headed=true}. Selenium Manager fetches chromedriver.
 *
 * <p>The driver heals every element-file lookup on its own ({@link HealingDriver}): under Run with Locator Coverage, a
 * {@code driver.findElement(WebElements.by(...))} whose XPath finds nothing is retried with the live Strata XPath.
 */
public final class Browser {
    private Browser() {
    }

    public static WebDriver open() {
        final ChromeOptions options = new ChromeOptions().addArguments("--window-size=1280,900");
        // Chrome's "change your password" warning after a login swallows all later clicks on the page.
        options.setExperimentalOption("prefs", Map.of("credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false));
        if (!Boolean.getBoolean("demo.headed")) {
            options.addArguments("--headless=new", "--disable-gpu");
        }
        return HealingDriver.wrap(new ChromeDriver(options), WebElements.repository());
    }
}

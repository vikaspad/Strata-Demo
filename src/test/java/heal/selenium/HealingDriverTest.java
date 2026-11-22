package heal.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import demo.WebElements;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;

/** The driver wrapper without a browser: a stub driver that finds nothing. Healing itself needs Locator Coverage. */
class HealingDriverTest {
    private final List<By> asked = new ArrayList<>();

    private WebDriver findsNothing() {
        return (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {WebDriver.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findElement")) {
                        asked.add((By) args[0]);
                        throw new NoSuchElementException("stub: " + args[0]);
                    }
                    if (method.getName().equals("findElements")) {
                        asked.add((By) args[0]);
                        return List.of();
                    }
                    return null;
                });
    }

    @Test
    void onAPlainRunTheOriginalFailureReachesTheTest() {
        final List<String> log = new ArrayList<>();
        final WebDriver driver = HealingDriver.wrap(findsNothing(), WebElements.repository(), log::add);
        final By broken = WebElements.by("HealingDemo", "signInButton");
        final NoSuchElementException e = assertThrows(NoSuchElementException.class, () -> driver.findElement(broken));
        assertEquals("stub: " + broken, e.getMessage().lines().findFirst().orElse(""));
        assertEquals(List.of(broken), asked, "looked up once, with the element file's XPath");
        assertTrue(log.isEmpty(), "nothing to heal with: " + log);
    }

    @Test
    void otherLocatorsAndFindElementsPassThrough() {
        final WebDriver driver = HealingDriver.wrap(findsNothing(), WebElements.repository(), line -> { });
        assertThrows(NoSuchElementException.class, () -> driver.findElement(By.id("x")));
        assertThrows(NoSuchElementException.class, () -> driver.findElement(By.xpath("//not-in-the-file")));
        assertEquals(List.of(), driver.findElements(WebElements.by("Login", "signInButton")));
        assertEquals(List.of(By.id("x"), By.xpath("//not-in-the-file"), WebElements.by("Login", "signInButton")),
                asked);
    }

    @Test
    void anXpathSharedByTwoElementsIsNotHealed() {
        assertEquals(Map.of("//b", "P.b"), HealingDriver.uniqueKeys(Map.of("P.a", "//a", "Q.a", "//a", "P.b", "//b")));
    }

    @Test
    void readsTheXpathOfAByXpathOnly() {
        assertEquals(Optional.of("//h1"), HealingDriver.xpathOf(By.xpath("//h1")));
        assertEquals(Optional.empty(), HealingDriver.xpathOf(By.cssSelector("h1")));
    }
}

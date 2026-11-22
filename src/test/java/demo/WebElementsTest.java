package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import heal.ElementFiles;
import heal.ElementRepository;
import heal.Healer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;

/** The element file, its configuration and the generic healer. No browser needed. */
class WebElementsTest {
    @Test
    void loadsTheConfiguredFile() {
        assertEquals(Path.of("src/test/resources/UIElements/WebElements.xml").toAbsolutePath().normalize(),
                WebElements.repository().file());
        assertEquals("//form[@id='login-form']//input[@id='username']", WebElements.xpath("Login", "usernameInput"));
        assertEquals(By.xpath("//form[@id='login-form']//button[normalize-space()='Sign in']"), WebElements.by("Login", "signInButton"));
        assertEquals(Optional.of("LoginForm.signInButton"),
                WebElements.repository().strataKey("HealingDemo", "signInButton"));
    }

    @Test
    void unknownElementNamesTheFile() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> WebElements.by("Login", "noSuchThing"));
        assertTrue(e.getMessage().startsWith("No element Login.noSuchThing in "), e.getMessage());
    }

    @Test
    void propertiesFilesWorkToo(@TempDir final Path dir) throws IOException {
        final Path file = Files.writeString(dir.resolve("elements.properties"),
                "Login.signIn=//button[@id='go']\nLogin.signIn.strata=LoginForm.signInButton\n");
        final ElementRepository elements = ElementFiles.open(file);
        assertEquals("//button[@id='go']", elements.xpath("Login", "signIn"));
        assertEquals(Optional.of("LoginForm.signInButton"), elements.strataKey("Login", "signIn"));
    }

    @Test
    void healerReturnsWhatTheFinderFinds() {
        final List<String> asked = new ArrayList<>();
        final Object found = new Object();
        final Healer<Object> healer = new Healer<>(WebElements.repository(), xpath -> {
            asked.add(xpath);
            return found;
        }, e -> e instanceof Healer.NotFoundException);
        assertSame(found, healer.find("Login", "signInButton"));
        assertEquals(List.of("//form[@id='login-form']//button[normalize-space()='Sign in']"), asked);
    }

    @Test
    void onAPlainRunTheOriginalFailureReachesTheTest() {
        final Healer<Object> healer = new Healer<>(WebElements.repository(), xpath -> {
            throw new Healer.NotFoundException(xpath);
        }, e -> e instanceof Healer.NotFoundException);
        final Healer.NotFoundException e = assertThrows(Healer.NotFoundException.class,
                () -> healer.find("HealingDemo", "signInButton"));
        assertEquals("Nothing matches //*[@id='sign-in-button']", e.getMessage());
        assertThrows(IllegalStateException.class, () -> new Healer<>(WebElements.repository(), xpath -> {
            throw new IllegalStateException("not a lookup failure");
        }, x -> x instanceof Healer.NotFoundException).find("Login", "signInButton"), "other errors are not healed");
    }
}

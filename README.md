# Strata Demo

A small Selenium project for trying **Run with Locator Coverage** from the Strata IntelliJ plugin. Strata records
every page your tests visit, gives each element a verified XPath, shows which elements your tests use, and heals test
locators that stop working.

- [What's in the project](#whats-in-the-project)
- [Set up](#set-up)
- [Run with Locator Coverage](#run-with-locator-coverage)
- [Read the results](#read-the-results)
- [Live XPaths during a run](#live-xpaths-during-a-run)
- [Self-healing locators](#self-healing-locators)
- [Use Strata in your own test suite](#use-strata-in-your-own-test-suite)
- [Run output](#run-output)
- [Troubleshooting](#troubleshooting)

## What's in the project

| Path | What it is |
|---|---|
| `src/test/resources/site/` | A static trading site: login, a trade blotter, an export dialog that JS opens, and a second blotter. Plus `/components` (one of each of the 21 component types Strata covers, including an overlay dropdown, a menu, a table, icons, and a same-origin and a cross-origin iframe) and `/grid` (AG Grid Community 36.2.0, MIT, vendored under `site/vendor/`). `DemoSite` serves it on a random local port, so nothing external is needed. |
| `BlotterJourneyTest` | The blotter journey as a plain JUnit 5 test |
| `ComponentsTest` | Touches each component type on `/components` |
| `OrderGridTest` | Sorts, cancels an order and reads a cell in the AG Grid |
| `SelfHealingTest` | Two **deliberately broken locators** that heal under Locator Coverage. Tagged `healing` |
| `AutomationExerciseTest` | A real public site, [automationexercise.com](https://www.automationexercise.com): navigation, search, cart, a failed login, the contact form. Ad and consent scripts are blocked over CDP. Tagged `public` |
| `SauceDemoTest` | Logs in to [saucedemo.com](https://www.saucedemo.com/) as `standard_user` and opens **About**. Tagged `public` |
| `features/*.feature` + `demo.steps` | The blotter and SauceDemo journeys in Cucumber 7 (`RunCucumberTest`) |
| `src/test/resources/UIElements/WebElements.xml` | Every locator the tests use, as XPath, read through `demo.WebElements` |
| `src/test/java/heal/` | The self-healing finder. Only `heal/selenium/` (the driver wrapper) needs Selenium, so you can copy the rest into any Java suite |

Tests run in headless Chrome. Add `-Ddemo.headed=true` to the VM options to watch them.

```
mvn test                                                              # local tests only
mvn test -Dgroups=public -DexcludedGroups=none                        # the public-site tests (needs internet)
mvn test -Dgroups=public -DexcludedGroups=none -Dtest=SauceDemoTest   # one of them
```

The "Unable to find CDP implementation" warning is harmless.

## Set up

1. **Build the plugin.** In `../Strata`, run:

   ```
   gradlew buildPlugin
   ```

   The zip lands in `strata-plugin/build/distributions/strata-plugin-0.1.0.zip`. A prebuilt copy is in `../Strata/dist/`.
2. **Install it.** In IntelliJ IDEA 2024.2+, go to **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip,
   and restart. (Or run `gradlew :strata-plugin:runIde` in `../Strata` and open this project in the sandbox IDE.)
3. **Open this folder as a Maven project** with a **JDK 17+** project SDK. The agent needs Java 17.
4. **Add the live API.** You only need it for live XPaths and healing. **Tools → Add Strata Live API to Project**
   copies `strata-api.jar` to `.strata/lib/`. This project already references it from `pom.xml`:

   ```xml
   <!-- Strata live locators. No dependencies; does nothing outside Run with Locator Coverage. -->
   <dependency>
       <groupId>dev.strata</groupId>
       <artifactId>strata-api</artifactId>
       <version>1</version>
       <scope>system</scope>
       <systemPath>${project.basedir}/.strata/lib/strata-api.jar</systemPath>
   </dependency>
   ```

   For Gradle:

   ```kotlin
   dependencies {
       testImplementation(files(".strata/lib/strata-api.jar"))
   }
   ```

## Run with Locator Coverage

Right-click a test class, a test method or a `.feature` file (for example `BlotterJourneyTest`) and choose
**Run with Locator Coverage**. Feature files need the *Gherkin* and *Cucumber for Java* plugins.

Your tests don't change. The plugin attaches the Strata agent to the test JVM, and the agent watches every Selenium
command:

```java
driver.get(DemoSite.baseUrl() + "/login");                                     // captures the Login page
driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys("jdoe"); // marks the input as used
driver.findElement(WebElements.by("Login", "signInButton")).click();           // captures the blotter it opens
```

Snapshots are redacted in the browser before they're written to disk: no table data, no input values, no scripts.

## Read the results

When the run ends, a balloon says something like *"3 pages, 118 elements, 27 used (23%)"*.

The **Locator Coverage** tool window (at the bottom of the IDE) has these sections:

| Section | What it shows |
|---|---|
| Pages Captured, Interactable Elements, Covered by Tests, Broken Test Locators | Four summary tiles |
| Pages table | Per page: visits, interactable elements, how many your tests used, broken locators |
| Broken locators table | Test locators that never found anything, with the route and the reason |
| **Healed Locators** | Each locator a test healed: the element, **old XPath → new XPath**, the file, and a status (*Healed*, *Already in file*, *Skipped: reason*). Hover over a cell to see the full XPath |

**`.strata/locator-coverage.xml`** (click **Open XML** in the balloon) holds the full model, as page → module → xpath:

```xml
<module name="BlotterFilterForm" kind="FORM" xpath="//form[@id='blotter-filter']" used="2" total="9">
  <xpath name="counterpartyInput" type="TEXT_INPUT" strategy="NAME" stability="HIGH" matches="1" used="true"
         value=".//input[@name='counterparty']"
         absolute="//form[@id='blotter-filter']//input[@name='counterparty']"/>
</module>
```

- `stability` is `HIGH` (test id, id, name), `MEDIUM` or `LOW` (text, position).
- `used="true"` means a test looked the element up or interacted with it.
- Tables, lists, cards and dropdown options are **templates** with one `%s` to fill in, such as a row number or an
  option's text.
- Iframe content appears under `<frame>`.

## Live XPaths during a run

With `strata-api` on the classpath, tests can ask for the verified XPath of any element on the page the browser
is showing **right now**. Keys are `Module.element`, as in the XML.

```java
import dev.strata.api.Strata;

driver.findElement(By.xpath(Strata.xpath("LoginForm", "usernameInput"))).sendKeys("jdoe");

// A template: fill in the row (1-based), card or option text.
String notional = driver.findElement(By.xpath(Strata.xpath("OrdersGrid", "notionalCell", 1))).getText();

// An optional lookup: empty if there's no such element on this page, or on a plain run.
Strata.find("ExportDialog", "downloadButton").ifPresent(l -> driver.findElement(By.xpath(l.xpath())).click());

// Everything on the current page, in document order.
Strata.xpaths().forEach((key, xpath) -> System.out.println(key + " = " + xpath));

if (!Strata.isActive()) {
    // A plain Run or mvn test: the agent isn't attached and the lookups are empty.
}
```

`Strata.xpath(...)` throws `StrataException` if the element is unknown or the API is inactive. Use `Strata.find(...)`
when the code must also work on a plain run.

## Self-healing locators

Healing runs in the background: tests use plain `driver.findElement(...)`. `Browser.open()` wraps the driver once
with `heal.selenium.HealingDriver`, a Selenium 4 `WebDriverDecorator`. When a `findElement(By.xpath(x))` on the driver,
or on an element it returned, finds nothing and `x` is an XPath in `WebElements.xml`, the healer picks the **most
similar Strata live locator on the current page**, retries with it, and records the heal. When the run ends, the
plugin writes the new XPath into `WebElements.xml`.

```java
driver.findElement(WebElements.by("Blotter", "counterpartyInput")).sendKeys("Halvorsen");   // heals on its own

// A template entry (row, card or option) still needs its value, so call the healer for it.
WebElements.healer(driver).find("HealingDemo", "firstRowNotionalCell", 1).getText();
```

A healed XPath replaces the old one for the rest of the run, so a `By` held in a field or a `@FindBy` page object
(through `PageFactory.initElements(driver, …)`) heals too, as long as its XPath is in the element file. These aren't
healed: `findElements` (an empty list usually means "absent"), XPaths that aren't in the file or appear there under two
names, and CSS or id locators.

The element file needs no Strata mapping:

```xml
<page name="Blotter" url="/blotter/{id}">
    <!-- broken: the input's name is now 'counterparty' -->
    <element name="counterpartyInput" xpath="//*[@name='counterparty1']"/>
</page>
```

**How the healer chooses.** `heal.LocatorMatcher` is deterministic and uses no AI. It gives each live locator on the
page a score from 0 to 1:

| Signal | Weight | Example |
|---|---|---|
| Element name, without its type suffix | 55% | `counterpartyInput` vs Strata's `counterpartyInput` |
| Type suffix vs the locator's type | 15% | `…Input` fits `TEXT_INPUT`; `…Button` never heals to an input |
| Quoted values in the old XPath vs the new one | 20% | `'counterparty1'` vs `'counterparty'` |
| Page name vs module name | 10% | `Blotter` vs `BlotterFilterForm` |

It heals only when the best score is **at least 0.70** and **beats the runner-up by at least 0.15**. When two
candidates are equally good, such as the same name in two modules, nothing is healed and the test fails as it would
without Strata. A lookup with a template value only considers templates, and one without only considers plain
locators.

A heal prints its choice and the runner-up:

```
[heal] Blotter.counterpartyInput = //*[@name='counterparty1'] found nothing; healed with BlotterFilterForm.counterpartyInput 0.97 (next BlotterFilterForm.notesTextarea 0.43) = //form[@id='blotter-filter']//input[@name='counterparty'] (WebElements.xml is updated when the run ends)
```

When nothing stands out, it says why and lists the top candidates:

```
[heal] Blotter.heading = //h2 found nothing and can't be healed: the best match, Allocations.allocationsHeading 0.28, is below 0.70; candidates: Allocations.allocationsHeading 0.28, ExportDialog.exportTradesHeading 0.26, TradeGrid.tradesHeading 0.26
```

**When the names don't resemble each other**, name the Strata key yourself, either in the element file or in code:

```xml
<element name="firstRowNotionalCell" xpath="//*[@row-index='0']//*[@col-id='notionalAmount']" strata="OrdersGrid.notionalCell"/>
```

```java
WebElements.healer(driver).find("HealingDemo", "firstRowNotionalCell", "OrdersGrid.notionalCell", 1);
```

**Try it:** right-click `SelfHealingTest` → **Run with Locator Coverage**.
1. Both broken locators heal and the test passes.
2. When it ends, a balloon says *"2 locators healed in WebElements.xml"*. The file is changed in one undoable step and
   already saved.
3. The **Healed Locators** section lists the old and new XPaths.
4. From then on the test also passes on a plain **Run**. To see the heal again, put the broken XPaths back. The
   comment above the `HealingDemo` entries says how.

On a plain **Run** or `mvn test`, nothing is healed. A broken locator fails with `NoSuchElementException`, as usual.

## Use Strata in your own test suite

Everything below works in any Selenium 4 + Java 17 suite (JUnit 5, TestNG or Cucumber 7). Only step 6 changes code
you already have, and it changes one line.

### 1. Install the plugin

As in [Set up](#set-up), steps 1–2. **Run with Locator Coverage** then works on your tests with no other change: you
get captured pages, the coverage XML and the tool window. Steps 2–6 add live XPaths and healing.

### 2. Add `strata-api`

In your project, **Tools → Add Strata Live API to Project** copies `strata-api.jar` to `.strata/lib/` and shows the
dependency to add (see [Set up](#set-up), step 4). The jar has no dependencies, and on a plain run its lookups are
empty, so it is safe to keep in every build.

### 3. Copy the healer

Copy these classes from `src/test/java/heal/` in this project into your test sources:

```
src/test/java/heal/
├── Healer.java                       # find(page, element[, value]): element-file XPath first, then the live match
├── LocatorMatcher.java               # scores live locators against a broken entry (no AI)
├── ElementRepository.java            # where your locators live; implement it for your own format
├── XmlElementRepository.java         # <element name="…" xpath="…"/>
├── PropertiesElementRepository.java  # Page.element=xpath
├── ElementFiles.java                 # picks the file (-Delements.file, heal.properties, or the default)
└── selenium/HealingDriver.java       # wraps a WebDriver so every lookup heals; the only class that needs Selenium
```

The first six depend on each other, so copy all of them, even if you use only one element-file format (`ElementFiles`
opens both). `selenium/HealingDriver.java` is for Selenium suites. Without Selenium, skip it and call `Healer`
directly, as its javadoc shows.

Don't copy the test classes. `selenium/HealingDriverTest.java` uses this demo's `demo.WebElements` and won't compile in
your project. `LocatorMatcherTest.java` compiles with JUnit 5 but only tests the matcher, so it's optional.

Keep the package name `heal`, or rename it everywhere.

### 4. Put your locators in an element file

Each element gets a page name, an element name and one XPath:

```xml
<!-- src/test/resources/UIElements/WebElements.xml -->
<web-elements>
    <page name="Login" url="/login">
        <element name="usernameInput" xpath="//*[@id='username']"/>
        <element name="passwordInput" xpath="//*[@id='password']"/>
        <element name="signInButton"  xpath="//button[normalize-space()='Sign in']"/>
    </page>
    <page name="Orders" url="/orders">
        <!-- a row template in Strata: heal it with healer.find(..., row) -->
        <element name="firstRowNotionalCell" xpath="//*[@row-index='0']//*[@col-id='notional']"
                 strata="OrdersGrid.notionalCell"/>
    </page>
</web-elements>
```

A `.properties` file works too:

```properties
Login.usernameInput=//*[@id='username']
Login.signInButton=//button[normalize-space()='Sign in']
Login.signInButton.strata=LoginForm.signInButton
```

`strata` is optional. Add it only when your element name doesn't resemble Strata's (see
[How the healer chooses](#self-healing-locators)). Then tell the healer where the file is. Point it at the **source**
file, not a copy under `target/` or `build/`, so healed XPaths land in the file you commit:

```properties
# src/test/resources/heal.properties
elements.file=src/test/resources/UIElements/WebElements.xml
```

### 5. Give tests one way to reach a locator

```java
package com.acme.ui;

import heal.ElementFiles;
import heal.ElementRepository;
import heal.Healer;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

/** The element file, loaded once per JVM. */
public final class WebElements {
    private static final ElementRepository ELEMENTS = ElementFiles.open();

    private WebElements() {
    }

    /** By.xpath of page.element. */
    public static By by(final String page, final String element) {
        return By.xpath(ELEMENTS.xpath(page, element));
    }

    public static ElementRepository repository() {
        return ELEMENTS;
    }

    /** Only needed for template entries (a row, card or option), which need a value to heal. */
    public static Healer<WebElement> healer(final SearchContext context) {
        return new Healer<>(ELEMENTS, xpath -> context.findElement(By.xpath(xpath)),
                e -> e instanceof NoSuchElementException);
    }
}
```

### 6. Wrap the driver where you create it

This is the only change to existing code. Every `findElement(By.xpath(…))` whose XPath is in the element file now
heals on its own:

```java
package com.acme.ui;

import heal.selenium.HealingDriver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public final class Browser {
    private Browser() {
    }

    public static WebDriver open() {
        final ChromeOptions options = new ChromeOptions();
        // Any WebDriver works: ChromeDriver, FirefoxDriver, a RemoteWebDriver on a Grid...
        return HealingDriver.wrap(new ChromeDriver(options), WebElements.repository());
    }
}
```

The wrapped driver implements the same interfaces as the original (`JavascriptExecutor`, `TakesScreenshot`, `HasCdp`
and so on), so casts to those still work. A cast to the concrete class, such as `(ChromeDriver) driver`, doesn't:
cast to the interface instead.

### 7. Write tests as usual

**JUnit 5.** Nothing in the test mentions healing:

```java
class LoginTest {
    private WebDriver driver;

    @BeforeEach
    void open() {
        driver = Browser.open();
    }

    @AfterEach
    void close() {
        driver.quit();
    }

    @Test
    void signsIn() {
        driver.get("https://app.example.test/login");
        driver.findElement(WebElements.by("Login", "usernameInput")).sendKeys("jdoe");
        driver.findElement(WebElements.by("Login", "passwordInput")).sendKeys("secret");
        driver.findElement(WebElements.by("Login", "signInButton")).click();   // heals if the XPath broke
    }
}
```

**Page objects.** A `By` held in a field heals too: after the first heal, the old XPath maps to the new one for the
rest of the run. A `@FindBy` heals when its XPath is exactly the one in the element file:

```java
public final class LoginPage {
    private static final By USERNAME = WebElements.by("Login", "usernameInput");

    @FindBy(xpath = "//button[normalize-space()='Sign in']")   // same text as Login.signInButton in the file
    private WebElement signIn;

    private final WebDriver driver;

    public LoginPage(final WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(driver, this);   // pass the wrapped driver
    }

    public void signInAs(final String user) {
        driver.findElement(USERNAME).sendKeys(user);
        signIn.click();
    }
}
```

**Cucumber 7.** Open the wrapped driver in a hook:

```java
public class Hooks {
    static WebDriver driver;

    @Before
    public void open() {
        driver = Browser.open();
    }

    @After
    public void close() {
        driver.quit();
    }
}
```

**Templates.** A Strata template entry (a table row, card or dropdown option) needs its value to heal, so look it up
through the healer:

```java
String notional = WebElements.healer(driver).find("Orders", "firstRowNotionalCell", 1).getText();   // row 1
```

**Live XPaths.** To use Strata's verified XPath directly instead of your own, see
[Live XPaths during a run](#live-xpaths-during-a-run).

### 8. Run with Locator Coverage

Right-click a test or a feature file → **Run with Locator Coverage**. For each broken locator the test output prints a
`[heal]` line, and the test carries on. When the run ends:

1. a balloon says *"N locators healed in WebElements.xml"*, and the file is already changed and saved, in one undoable
   step;
2. the **Healed Locators** section of the tool window lists each old and new XPath with its status;
3. the next run, plain or not, uses the healed XPaths directly.

On a plain **Run**, `mvn test` or CI, nothing is healed and a broken locator fails with `NoSuchElementException`, as
usual. The healer never hides a broken locator outside Locator Coverage.

**What isn't healed:**
- `findElements`: an empty list usually means "absent";
- XPaths that aren't in the element file, or appear there under more than one name;
- CSS and id locators;
- template entries looked up without a value.

### Other libraries and your own healer

**Without Selenium** (Playwright, or anything else that takes an XPath), use `Healer` with a finder for your library:

```java
// Playwright: locator() doesn't throw, so the finder checks the count itself
Healer<Locator> healer = new Healer<>(ElementFiles.open(), xpath -> {
    Locator l = page.locator("xpath=" + xpath);
    if (l.count() == 0) throw new Healer.NotFoundException(xpath);
    return l;
}, e -> e instanceof Healer.NotFoundException);

healer.find("Login", "signInButton").click();
healer.find("Orders", "firstRowNotionalCell", 1);   // a template, row 1
```

**With a healer of your own**, you only need `strata-api`: ask Strata for the live XPath, and report the heal so the
plugin writes it into your element file. A minimal version:

```java
import dev.strata.api.Strata;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public final class MyHealer {
    private MyHealer() {
    }

    /**
     * Finds xpath; if it finds nothing, retries with Strata's live XPath for module.element and reports the heal.
     *
     * @param file   your element file; the plugin rewrites oldXpath in it after the run
     * @param key    your own name for the element, shown in the Healed Locators table
     * @param module Strata's module name for the element, as in .strata/locator-coverage.xml
     * @param name   Strata's element name
     */
    public static WebElement find(final WebDriver driver, final String file, final String key, final String xpath,
            final String module, final String name) {
        try {
            return driver.findElement(By.xpath(xpath));
        } catch (NoSuchElementException broken) {
            final Optional<Strata.Locator> live = Strata.find(module, name);   // empty on a plain run
            if (live.isEmpty() || live.get().template() || live.get().xpath().equals(xpath)) {
                throw broken;
            }
            final WebElement found;
            try {
                found = driver.findElement(By.xpath(live.get().xpath()));
            } catch (NoSuchElementException alsoBroken) {
                broken.addSuppressed(alsoBroken);
                throw broken;
            }
            Strata.healed(file, xpath, live.get().xpath(), key);   // written to healed.jsonl; false on a plain run
            return found;
        }
    }
}
```

To pick the replacement by similarity instead of by a known Strata name, read every live locator on the current page
with `Strata.locators()` (key, module, name, type, strategy, stability, template flag and XPath). That is what
`heal.LocatorMatcher` does.

The plugin replaces `oldXpath` only where it appears **exactly once** as a whole value in that file (raw, XML- or
JSON-escaped). Any heal it can't apply (a missing file, an ambiguous match, a file outside the project) is skipped and
listed with the reason.

## Run output

Each run's raw output is in `.strata/runs/<yyyyMMdd-HHmmss>/`:

| File | What it is |
|---|---|
| `manifest.jsonl` | One line per captured page state |
| `usage.jsonl` | One line per element the tests looked up or interacted with |
| `failed-lookups.jsonl` | Locators that found nothing (absent if none did) |
| `healed.jsonl` | Locators the tests healed (absent if none were) |
| `dom/*.dom.html` | Redacted snapshots |
| `agent.log` | What the agent did after each command, with timings |
| `generator.log` | Elements dropped or modules merged during XPath generation |
| `locator-coverage.xml` | The page / module / xpath model for this run, also copied to `.strata/locator-coverage.xml` |

## Troubleshooting

| Symptom | Cause |
|---|---|
| No **Run with Locator Coverage** menu item | IntelliJ delegates runs to Maven. In **Settings → Build Tools → Maven → Runner**, turn off *Delegate IDE build/run actions to Maven* |
| "Locator Coverage requires Java 17+" | The run configuration's JRE is older than 17 |
| A broken locator isn't healed | Check that the driver came from `HealingDriver.wrap` (or the lookup goes through the healer), that its XPath is in the element file exactly once and that you used **Run with Locator Coverage**. Then read the `[heal]` line in the test output for the reason |
| Healed, but `WebElements.xml` didn't change | Check its status under **Healed Locators**: the old XPath may appear more than once, or `elements.file` may point at `target/` |
| `Strata.xpath` throws "agent is not attached" | It's a plain run. Use `Strata.find(...)`, which returns empty instead |

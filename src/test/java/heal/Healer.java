package heal;

import dev.strata.api.Strata;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Self-healing lookups for any UI test framework. A test asks for {@code page.element} from its element file. If that
 * XPath finds nothing, the healer picks the verified live Strata XPath of the same element, retries with it, and
 * reports the heal. When the run ends, the Strata plugin writes the healed XPath into the element file.
 *
 * <p>The healer knows nothing about the browser library: you give it a {@link Finder} that looks an XPath up, and a
 * test for the library's "not found" exception.
 *
 * <pre>{@code
 * // Selenium / Appium
 * Healer<WebElement> healer = new Healer<>(ElementFiles.open(),
 *         xpath -> driver.findElement(By.xpath(xpath)), e -> e instanceof NoSuchElementException);
 *
 * // Playwright: page.locator() doesn't throw, so the finder checks the count itself
 * Healer<Locator> healer = new Healer<>(ElementFiles.open(), xpath -> {
 *     Locator l = page.locator("xpath=" + xpath);
 *     if (l.count() == 0) throw new Healer.NotFoundException(xpath);
 *     return l;
 * }, e -> e instanceof Healer.NotFoundException);
 *
 * healer.find("Login", "signInButton").click();
 * healer.find("Orders", "notionalCell", 1);   // a Strata template entry, filled with row 1
 * }</pre>
 *
 * <p>Healing needs <b>Run with Locator Coverage</b> and the {@code strata-api} jar. The replacement is the live
 * locator that {@link LocatorMatcher} finds most like the broken entry (by name, type, the values in its XPath and
 * the page), so the element file needs no Strata mapping. If no locator clearly stands out, nothing is healed. An
 * element whose name doesn't resemble Strata's can name its Strata key instead: a {@code strata} attribute in the
 * element file, or the key passed to {@link #find(String, String, String, Object)}. On a plain run the original "not
 * found" exception reaches the test unchanged, so the broken locator still fails.
 */
public final class Healer<T> {

    /** Looks one XPath up; throws when nothing matches. */
    @FunctionalInterface
    public interface Finder<T> {
        T find(String xpath);
    }

    /** For finders whose library has no "not found" exception of its own. */
    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(final String xpath) {
            super("Nothing matches " + xpath);
        }
    }

    /** How deep this thread is inside {@link #find}; a wrapped driver stays out of the way while it is positive. */
    private static final ThreadLocal<int[]> FINDING = ThreadLocal.withInitial(() -> new int[1]);

    private final ElementRepository elements;
    private final Finder<T> finder;
    private final Predicate<? super RuntimeException> notFound;
    private final Consumer<String> log;

    public Healer(final ElementRepository elements, final Finder<T> finder,
            final Predicate<? super RuntimeException> notFound) {
        this(elements, finder, notFound, System.out::println);
    }

    public Healer(final ElementRepository elements, final Finder<T> finder,
            final Predicate<? super RuntimeException> notFound, final Consumer<String> log) {
        this.elements = elements;
        this.finder = finder;
        this.notFound = notFound;
        this.log = log;
    }

    /** {@code page.element}, healed with the most similar live locator (or its {@code strata} key) if it finds nothing. */
    public T find(final String page, final String element) {
        return find(page, element, elements.strataKey(page, element).orElse(null), null);
    }

    /** The same, for an element whose Strata entry is a template (a row, card or option), filled with {@code value}. */
    public T find(final String page, final String element, final Object value) {
        return find(page, element, elements.strataKey(page, element).orElse(null), value);
    }

    /** The same, with the Strata key ({@code Module.element}) given here instead of in the element file. */
    public T find(final String page, final String element, final String strataKey, final Object value) {
        final String xpath = elements.xpath(page, element);
        final int[] depth = FINDING.get();
        depth[0]++;
        try {
            return finder.find(xpath);
        } catch (RuntimeException broken) {
            if (!notFound.test(broken)) {
                throw broken;
            }
            return heal(page, element, xpath, strataKey, value, broken);
        } finally {
            depth[0]--;
        }
    }

    /** True while this thread is inside a {@code find}, so its lookups are healed here, not again by a wrapper. */
    public static boolean finding() {
        return FINDING.get()[0] > 0;
    }

    private T heal(final String page, final String element, final String oldXpath, final String strataKey,
            final Object value, final RuntimeException broken) {
        final String key = page + "." + element;
        if (!Strata.isActive()) {
            throw broken;   // a plain run: the broken locator fails as usual
        }
        final Optional<Choice> choice = strataKey != null
                ? byKey(key, strataKey, value)
                : bySimilarity(page, element, oldXpath, value);
        if (choice.isEmpty() || choice.get().xpath().equals(oldXpath)) {
            throw broken;
        }
        final T found;
        try {
            found = finder.find(choice.get().xpath());
        } catch (RuntimeException alsoBroken) {
            log.accept("[heal] " + key + ": " + choice.get().source() + " = " + choice.get().xpath()
                    + " finds nothing either");
            broken.addSuppressed(alsoBroken);
            throw broken;
        }
        elements.healed(page, element, choice.get().xpath());
        final boolean recorded = Strata.healed(elements.file().toString(), oldXpath, choice.get().xpath(), key);
        log.accept("[heal] " + key + " = " + oldXpath + " found nothing; healed with " + choice.get().source() + " = "
                + choice.get().xpath()
                + (recorded ? " (" + elements.file().getFileName() + " is updated when the run ends)" : ""));
        return found;
    }

    /** The replacement XPath and where it came from, for the log. */
    private record Choice(String xpath, String source) {
    }

    /** The locator the element file names in its {@code strata} attribute. */
    private Optional<Choice> byKey(final String key, final String strataKey, final Object value) {
        final int dot = strataKey.lastIndexOf('.');
        final Optional<Strata.Locator> locator = dot <= 0 ? Optional.empty()
                : Strata.find(strataKey.substring(0, dot), strataKey.substring(dot + 1));
        if (locator.isEmpty()) {
            log.accept("[heal] " + key + " can't be healed: Strata has no " + strataKey + " on this page; known: "
                    + Strata.xpaths().keySet());
            return Optional.empty();
        }
        if (locator.get().template() && value == null) {
            log.accept("[heal] " + strataKey + " is a template; pass the row, card or option to find(...)");
            return Optional.empty();
        }
        return Optional.of(new Choice(locator.get().xpath(value), strataKey));
    }

    /** The live locator most like the broken entry ({@link LocatorMatcher}), if one clearly stands out. */
    private Optional<Choice> bySimilarity(final String page, final String element, final String oldXpath,
            final Object value) {
        final String key = page + "." + element;
        final LocatorMatcher.Result result =
                LocatorMatcher.match(page, element, oldXpath, value != null, Strata.locators());
        if (result.chosen().isEmpty()) {
            log.accept("[heal] " + key + " = " + oldXpath + " found nothing and can't be healed: " + result.reason()
                    + (result.ranked().isEmpty() ? "" : "; candidates: " + result.ranked().stream().limit(3)
                            .map(LocatorMatcher.Scored::toString).collect(Collectors.joining(", "))));
            return Optional.empty();
        }
        final LocatorMatcher.Scored best = result.chosen().get();
        return Optional.of(new Choice(best.locator().xpath(value), best + result.runnerUp()
                .map(r -> " (next " + r + ")").orElse("")));
    }
}

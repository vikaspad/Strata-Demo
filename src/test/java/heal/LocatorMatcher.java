package heal;

import dev.strata.api.Strata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Picks the Strata live locator that a broken element-file entry most likely meant, so the element file needs no
 * {@code strata} mapping. Deterministic: no AI, only string similarity.
 *
 * <p>Each live locator on the current page gets a score from 0 to 1:
 * <ul>
 *   <li><b>name</b> (55%): the element's name against the locator's name, with type suffixes such as
 *       {@code Button} removed from both ({@code counterpartyInput} → {@code counterparty});</li>
 *   <li><b>type</b> (15%): the name's suffix against the locator's type. A suffix that contradicts the type
 *       ({@code ...Button} for a text input) rules the locator out;</li>
 *   <li><b>values</b> (20%): the quoted values in the broken XPath ({@code 'counterparty1'}) against those in the
 *       live one ({@code 'counterparty'});</li>
 *   <li><b>page</b> (10%): the element file's page name against the locator's module ({@code Blotter} →
 *       {@code BlotterFilterForm}).</li>
 * </ul>
 *
 * <p>The best locator is chosen only if it scores at least {@link #THRESHOLD} and beats the runner-up by at least
 * {@link #MARGIN}. Two equally good candidates (the same name in two modules) heal nothing: a wrong heal would be
 * written into the element file, while a failed test only needs a look.
 */
public final class LocatorMatcher {
    public static final double THRESHOLD = 0.70;
    public static final double MARGIN = 0.15;

    private static final double NAME = 0.55;
    private static final double TYPE = 0.15;
    private static final double VALUES = 0.20;
    private static final double PAGE = 0.10;

    /** Element-name suffix → the Strata types it can mean. Longest suffixes first, so "Textarea" wins over "Area". */
    private static final Map<String, Set<String>> SUFFIX_TYPES = new java.util.LinkedHashMap<>();

    static {
        suffix(Set.of("TEXTAREA"), "Textarea", "TextArea");
        suffix(Set.of("CHECKBOX"), "Checkbox", "CheckBox");
        suffix(Set.of("DROPDOWN"), "Dropdown", "Select", "Combo", "Combobox");
        suffix(Set.of("PARAGRAPH"), "Paragraph");
        suffix(Set.of("HEADING"), "Heading", "Title");
        suffix(Set.of("TABLE_HEADER"), "Header");
        suffix(Set.of("TEXT_INPUT", "PASSWORD", "TEXTAREA"), "Input", "Field", "Box", "Textbox", "TextBox");
        suffix(Set.of("BUTTON", "OTHER_INTERACTIVE"), "Button", "Btn");
        suffix(Set.of("OPTION"), "Option");
        suffix(Set.of("RADIO"), "Radio");
        suffix(Set.of("LINK"), "Link");
        suffix(Set.of("TABLE", "LIST"), "Table", "Grid");
        suffix(Set.of("TABLE_ROW", "LIST_ITEM", "DIV"), "Rows", "Row");
        suffix(Set.of("TABLE_CELL"), "Cell");
        suffix(Set.of("LABEL"), "Label");
        suffix(Set.of("ICON"), "Icon", "Image", "Img");
        suffix(Set.of("IFRAME"), "Frame", "Iframe");
        suffix(Set.of("LIST"), "List");
        suffix(Set.of("LIST_ITEM", "OPTION"), "Item");
        suffix(Set.of("TAB"), "Tab");
        suffix(Set.of("SPAN"), "Span");
        suffix(Set.of("DIV"), "Div", "Card", "Panel");
    }

    /** Module-name suffixes Strata adds for the module kind (CLAUDE.md §5.4). */
    private static final List<String> MODULE_SUFFIXES = List.of("Form", "Dialog", "Menu", "Grid");

    private static final Pattern LITERAL = Pattern.compile("'([^']*)'|\"([^\"]*)\"");
    private static final Pattern WORD = Pattern.compile("[A-Z]+(?![a-z])|[A-Z]?[a-z]+|[0-9]+");

    private LocatorMatcher() {
    }

    /** One candidate and its score. */
    public record Scored(Strata.Locator locator, double score) {
        @Override
        public String toString() {
            return locator.key() + " " + String.format(Locale.ROOT, "%.2f", score);
        }
    }

    /**
     * The outcome. {@link #chosen} is set only when the best candidate passes the threshold and the margin;
     * otherwise {@link #reason} says why. {@link #ranked} lists the scored candidates, best first.
     */
    public record Result(Optional<Scored> chosen, List<Scored> ranked, String reason) {
        /** The runner-up, for the log. */
        public Optional<Scored> runnerUp() {
            return ranked.size() > 1 ? Optional.of(ranked.get(1)) : Optional.empty();
        }
    }

    /**
     * Matches {@code page.element} (whose XPath {@code oldXpath} found nothing) against {@code live}. A
     * {@code template} lookup (the caller passed a row, card or option) only considers template locators, and a plain
     * one only plain locators. Locators inside frames are left out, because their XPath is relative to the frame.
     */
    public static Result match(final String page, final String element, final String oldXpath, final boolean template,
            final List<Strata.Locator> live) {
        final String suffix = suffixOf(element);
        final Set<String> types = suffix == null ? null : SUFFIX_TYPES.get(suffix);
        final List<String> base = words(strip(element, suffix));
        final List<String> pageWords = words(page);
        final List<String> oldValues = values(oldXpath);

        final List<Scored> ranked = new ArrayList<>();
        for (final Strata.Locator locator : live) {
            if (locator.template() != template || !locator.frame().isEmpty()) {
                continue;
            }
            final double type;
            if (types == null) {
                type = 0.5;
            } else if (types.contains(locator.type())) {
                type = 1;
            } else {
                continue;   // the name says it's a different kind of element
            }
            final double name = similarity(base, words(strip(locator.name(), suffixOf(locator.name()))));
            final double values = oldValues.isEmpty() ? 0.5 : bestValue(oldValues, values(locator.xpath()));
            final double module = similarity(pageWords, words(stripModule(locator.module())));
            final double score = NAME * name + TYPE * type + VALUES * values + PAGE * module;
            ranked.add(new Scored(locator, Math.round(score * 100) / 100.0));
        }
        ranked.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.locator().key()));

        if (ranked.isEmpty()) {
            return new Result(Optional.empty(), ranked, "no " + (template ? "template " : "")
                    + "locator on this page" + (types == null ? "" : " has a type that fits '" + suffix + "'"));
        }
        final Scored best = ranked.get(0);
        if (best.score() < THRESHOLD) {
            return new Result(Optional.empty(), ranked, "the best match, " + best + ", is below "
                    + String.format(Locale.ROOT, "%.2f", THRESHOLD));
        }
        if (ranked.size() > 1 && best.score() - ranked.get(1).score() < MARGIN) {
            return new Result(Optional.empty(), ranked, "ambiguous: " + best + " and " + ranked.get(1)
                    + " are too close");
        }
        return new Result(Optional.of(best), ranked, "");
    }

    /** Lower-case words of a camelCase, kebab or snake name: {@code firstRowNotional} → first, row, notional. */
    static List<String> words(final String s) {
        final List<String> out = new ArrayList<>();
        final Matcher m = WORD.matcher(s == null ? "" : s);
        while (m.find()) {
            out.add(m.group().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * How alike two names are, from 0 to 1: the better of word overlap (half Dice, half containment, so
     * {@code firstRowNotional} is close to {@code notional}) and edit distance of the joined words.
     */
    static double similarity(final List<String> a, final List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        final Set<String> common = new HashSet<>(a);
        common.retainAll(new HashSet<>(b));
        final int sa = new HashSet<>(a).size();
        final int sb = new HashSet<>(b).size();
        final double dice = 2.0 * common.size() / (sa + sb);
        final double containment = (double) common.size() / Math.min(sa, sb);
        return Math.max((dice + containment) / 2, text(String.join("", a), String.join("", b)));
    }

    /** Edit-distance similarity; a word of 4+ letters contained in the other counts at least 0.8. */
    static double text(final String a, final String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        final double edit = 1 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        final String shorter = a.length() <= b.length() ? a : b;
        final String longer = shorter == a ? b : a;
        return shorter.length() >= 4 && longer.contains(shorter) ? Math.max(edit, 0.8) : edit;
    }

    private static double bestValue(final List<String> old, final List<String> live) {
        double best = 0;
        for (final String o : old) {
            for (final String l : live) {
                best = Math.max(best, text(o, l));
            }
        }
        return best;
    }

    /** The quoted values in an XPath, lower-case letters and digits only; numbers and placeholders are left out. */
    static List<String> values(final String xpath) {
        final List<String> out = new ArrayList<>();
        final Matcher m = LITERAL.matcher(xpath == null ? "" : xpath);
        while (m.find()) {
            final String raw = m.group(1) != null ? m.group(1) : m.group(2);
            final String v = raw.replace("%s", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!v.isEmpty() && !v.chars().allMatch(Character::isDigit)) {
                out.add(v);
            }
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    private static String suffixOf(final String name) {
        for (final String suffix : SUFFIX_TYPES.keySet()) {
            if (name.endsWith(suffix) || name.equalsIgnoreCase(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static String strip(final String name, final String suffix) {
        if (suffix == null || name.length() < suffix.length()) {
            return name;
        }
        return name.substring(0, name.length() - suffix.length());
    }

    private static String stripModule(final String module) {
        for (final String suffix : MODULE_SUFFIXES) {
            if (module.endsWith(suffix) && module.length() > suffix.length()) {
                return module.substring(0, module.length() - suffix.length());
            }
        }
        return module;
    }

    private static int levenshtein(final String a, final String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            final int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static void suffix(final Set<String> types, final String... suffixes) {
        for (final String s : suffixes) {
            SUFFIX_TYPES.put(s, types);
        }
    }
}

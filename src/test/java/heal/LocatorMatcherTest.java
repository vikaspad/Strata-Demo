package heal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.strata.api.Strata;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link LocatorMatcher} on the live locators Strata reports for the demo's pages. */
class LocatorMatcherTest {

    private static Strata.Locator loc(final String module, final String name, final String type, final String xpath) {
        final boolean template = xpath.contains("%s");
        return new Strata.Locator(module + "." + name, module, name, type, template ? "ROW_TEMPLATE" : "ID", "HIGH",
                template, xpath, "");
    }

    /** The Trade Blotter page, as in the demo's .strata/locator-coverage.xml. */
    private static final List<Strata.Locator> BLOTTER = List.of(
            loc("MainNav", "blotterLink", "LINK", "//nav[@id='main-nav']//a[normalize-space()='Blotter']"),
            loc("Main", "h1Heading", "HEADING", "(//main//h1)[1]"),
            loc("BlotterFilterForm", "tradeDateInput", "TEXT_INPUT",
                    "//form[@id='blotter-filter']//input[@id=//label[normalize-space()='Trade date']/@for]"),
            loc("BlotterFilterForm", "accountDropdown", "DROPDOWN", "//form[@id='blotter-filter']//select[@name='account']"),
            loc("BlotterFilterForm", "counterpartyInput", "TEXT_INPUT",
                    "//form[@id='blotter-filter']//input[@name='counterparty']"),
            loc("BlotterFilterForm", "notesTextarea", "TEXTAREA", "//form[@id='blotter-filter']//textarea[@name='notes']"),
            loc("BlotterFilterForm", "applyFiltersButton", "BUTTON",
                    "//form[@id='blotter-filter']//button[@aria-label='Apply filters']"),
            loc("BlotterFilterForm", "resetButton", "BUTTON", "//form[@id='blotter-filter']//button[normalize-space()='Reset']"),
            loc("TradeGrid", "tradesHeading", "HEADING", "//section[@data-testid='trade-grid']//h2[normalize-space()='Trades']"),
            loc("TradeGrid", "exportButton", "BUTTON", "//section[@data-testid='trade-grid']//button[@aria-label='Export']"),
            loc("Trades", "notionalCell", "TABLE_CELL",
                    "//table[@id='trades']/tbody/tr[%s]/td[count(ancestor::table[1]/thead/tr/th[normalize-space()='Notional']/preceding-sibling::th)+1]"),
            loc("Allocations", "quantityInput", "TEXT_INPUT", "//section[@id='allocations']//input[@id='alloc-qty']"),
            loc("Allocations", "allocateButton", "BUTTON", "//section[@id='allocations']//button[normalize-space()='Allocate']"));

    private static final List<Strata.Locator> LOGIN = List.of(
            loc("LoginForm", "usernameInput", "TEXT_INPUT", "//form[@id='login-form']//input[@id='username']"),
            loc("LoginForm", "passwordInput", "PASSWORD", "//form[@id='login-form']//input[@id='password']"),
            loc("LoginForm", "deskInput", "TEXT_INPUT", "//form[@id='login-form']//input[@id=//label[normalize-space()='Desk']/@for]"),
            loc("LoginForm", "rememberMeCheckbox", "CHECKBOX", "//form[@id='login-form']//input[@id='remember']"),
            loc("LoginForm", "signInButton", "BUTTON", "//form[@id='login-form']//button[normalize-space()='Sign in']"));

    private static final String GRID = "//div[@id='orders']";
    private static final String ROW = GRID + "//div[@role='row'][@row-index=%s-1]";
    private static final List<Strata.Locator> ORDERS_GRID = List.of(
            loc("OrdersGrid", "orderIdHeader", "TABLE_HEADER", GRID + "//div[@role='columnheader'][@col-id='orderId']"),
            loc("OrdersGrid", "notionalHeader", "TABLE_HEADER", GRID + "//div[@role='columnheader'][@col-id='notional']"),
            loc("OrdersGrid", "ordersRow", "TABLE_ROW", ROW),
            loc("OrdersGrid", "orderIdCell", "TABLE_CELL", ROW + "//div[@role='gridcell'][@col-id='orderId']"),
            loc("OrdersGrid", "sideCell", "TABLE_CELL", ROW + "//div[@role='gridcell'][@col-id='side']"),
            loc("OrdersGrid", "notionalCell", "TABLE_CELL", ROW + "//div[@role='gridcell'][@col-id='notional']"),
            loc("OrdersGrid", "statusCell", "TABLE_CELL", ROW + "//div[@role='gridcell'][@col-id='status']"),
            loc("OrdersGrid", "cancelButton", "BUTTON",
                    ROW + "//div[@role='gridcell'][@col-id='actions']//button[normalize-space()='Cancel']"));

    private static String chosen(final LocatorMatcher.Result result) {
        return result.chosen().map(s -> s.locator().key()).orElse("none: " + result.reason());
    }

    @Test
    void renamedAttributeHealsToTheSameNamedInput() {
        final LocatorMatcher.Result r = LocatorMatcher.match("Blotter", "counterpartyInput",
                "//*[@name='counterparty1']", false, BLOTTER);
        assertEquals("BlotterFilterForm.counterpartyInput", chosen(r));
        assertTrue(r.chosen().get().score() >= 0.9, r.ranked().toString());
    }

    @Test
    void elementFilePageNameNeedNotMatchTheModule() {
        // The demo's HealingDemo entries: the page name says nothing about the module.
        assertEquals("LoginForm.signInButton",
                chosen(LocatorMatcher.match("HealingDemo", "signInButton", "//*[@id='sign-in-button']", false, LOGIN)));
    }

    @Test
    void templateCellHealsFromAPartialName() {
        final LocatorMatcher.Result r = LocatorMatcher.match("HealingDemo", "firstRowNotionalCell",
                "//*[@row-index='0']//*[@col-id='notionalAmount']", true, ORDERS_GRID);
        assertEquals("OrdersGrid.notionalCell", chosen(r));
    }

    @Test
    void plainLookupsNeverHealToATemplateAndTemplateLookupsNeverToAPlainOne() {
        // The blotter's only notionalCell is a row template, so a lookup without a row can't use it.
        assertTrue(LocatorMatcher.match("Blotter", "notionalCell", "//td[@data-col='notional']", false, BLOTTER)
                .chosen().isEmpty());
        assertTrue(LocatorMatcher.match("Blotter", "counterpartyInput", "//*[@name='x']", true, BLOTTER)
                .chosen().isEmpty());
    }

    @Test
    void aSuffixThatContradictsTheTypeRulesTheLocatorOut() {
        // "counterpartyButton" must not become the counterparty text input.
        final LocatorMatcher.Result r = LocatorMatcher.match("Blotter", "counterpartyButton",
                "//*[@name='counterparty1']", false, BLOTTER);
        assertTrue(r.chosen().isEmpty(), chosen(r));
        assertTrue(r.ranked().stream().allMatch(s -> s.locator().type().equals("BUTTON")), r.ranked().toString());
    }

    @Test
    void theSameNameInTwoModulesIsAmbiguous() {
        final List<Strata.Locator> live = new ArrayList<>(BLOTTER);
        live.add(loc("ExportDialog", "counterpartyInput", "TEXT_INPUT", "//div[@id='export-dialog']//input[@name='counterparty']"));
        final LocatorMatcher.Result r = LocatorMatcher.match("Trades", "counterpartyInput",
                "//*[@name='counterparty1']", false, live);
        assertTrue(r.chosen().isEmpty(), chosen(r));
        assertTrue(r.reason().startsWith("ambiguous"), r.reason());
    }

    @Test
    void nothingSimilarEnoughHealsNothing() {
        final LocatorMatcher.Result r = LocatorMatcher.match("Blotter", "settlementDateInput",
                "//*[@id='settle-date']", false, BLOTTER);
        assertTrue(r.chosen().isEmpty(), chosen(r));
        assertTrue(r.reason().contains("below"), r.reason());
    }

    @Test
    void aGenericNameIsNeverEnoughOnItsOwn() {
        // Blotter.heading (//h1): every heading is as good as the next.
        assertTrue(LocatorMatcher.match("Blotter", "heading", "//h2", false, BLOTTER).chosen().isEmpty());
    }

    @Test
    void frameLocatorsAreLeftOut() {
        final List<Strata.Locator> live = List.of(new Strata.Locator("Help/HelpForm.searchInput", "HelpForm",
                "searchInput", "TEXT_INPUT", "NAME", "HIGH", false, "//form//input[@name='q']", "Help"));
        assertTrue(LocatorMatcher.match("Help", "searchInput", "//*[@name='query']", false, live).ranked().isEmpty());
    }

    @Test
    void wordsAndValues() {
        assertEquals(List.of("first", "row", "notional", "cell"), LocatorMatcher.words("firstRowNotionalCell"));
        assertEquals(List.of("ui", "id", "7"), LocatorMatcher.words("UIId7"));
        assertEquals(List.of("counterparty1"), LocatorMatcher.values("//*[@name='counterparty1'][@row-index='0']"));
        assertEquals(List.of("signin"), LocatorMatcher.values("//button[normalize-space()=\"Sign in\"]"));
    }
}

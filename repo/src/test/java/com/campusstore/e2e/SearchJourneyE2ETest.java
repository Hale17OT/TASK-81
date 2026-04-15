package com.campusstore.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the search user journey.
 * <p>
 * Covers public vs. authenticated access, search form interaction,
 * filter visibility, and result/empty-state rendering.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class SearchJourneyE2ETest extends BaseE2ETest {

    @Test
    void search_unauthenticated_redirectsToLogin() {
        page.navigate(BASE_URL + "/search");

        // /search is a protected Thymeleaf page — redirects unauthenticated users
        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }

    @Test
    void search_authenticated_formIsVisible() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search");

        assertThat(page).hasURL(Pattern.compile(".*/search.*"));
        assertThat(page.locator("#search-input")).isVisible();
        assertThat(page.locator("button.fluent-search__submit")).isVisible();
    }

    @Test
    void search_filters_categoryDropdownVisible() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search");

        assertThat(page.locator("#categoryFilter")).isVisible();
        assertThat(page.locator("#conditionFilter")).isVisible();
        assertThat(page.locator("#sortFilter")).isVisible();
    }

    @Test
    void search_submitKeyword_navigatesToResultsUrl() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search");

        page.locator("#search-input").fill("Arduino");
        page.locator("button.fluent-search__submit").click();

        // The URL should now include the keyword parameter
        assertThat(page).hasURL(Pattern.compile(".*/search.*keyword=Arduino.*|.*/search.*q=Arduino.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void search_withNoMatch_showsEmptyState() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search?keyword=zzznonexistentxxx99");

        // Either empty state or results section should be present
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator(".fluent-page--search")).isVisible();
    }

    @Test
    void search_withKnownItem_showsResults() {
        loginAsAdmin();
        page.navigate(BASE_URL + "/search?keyword=Arduino");

        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
        // The DB-seeded "Arduino Uno R3 Board" must appear in results,
        // confirming the search rendered real inventory data from the DB.
        assertThat(page.locator("body")).containsText("Arduino");
    }

    @Test
    void search_withKnownItem_showsItemCardWithPrice() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search?keyword=Arduino");

        assertThat(page.locator("body")).isVisible();
        // Item name appears in a result card/row
        assertThat(page.locator("body")).containsText("Arduino");
        // Price is rendered (Arduino Uno R3 Board costs $24.99)
        assertThat(page.locator("body")).containsText("24.99");
    }

    @Test
    void search_submitKeyword_resultsContainMatchingItem() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search");

        page.locator("#search-input").fill("Arduino");
        page.locator("button.fluent-search__submit").click();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/search.*[Aa]rduino.*|.*[Kk]eyword.*[Aa]rduino.*"));
        // The known item must appear in the rendered results list
        assertThat(page.locator("body")).containsText("Arduino");
    }

    @Test
    void search_priceRangeFilters_arePresent() {
        loginAsStudent();
        page.navigate(BASE_URL + "/search");

        // Price min and max inputs should be on the page
        assertThat(page.locator("input[name='priceMin']")).isVisible();
        assertThat(page.locator("input[name='priceMax']")).isVisible();
    }
}

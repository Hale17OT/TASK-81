package com.campusstore.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the student user journey.
 * <p>
 * Covers home page, search, my requests, profile navigation, and
 * verification that admin pages are inaccessible to students.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class StudentJourneyE2ETest extends BaseE2ETest {

    @BeforeEach
    void loginAsStudentUser() {
        loginAsStudent();
        // Verify login succeeded before running each test
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*error.*"));
    }

    @Test
    void student_homePageLoads_withWelcomeContent() {
        page.navigate(BASE_URL + "/");

        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator(".fluent-nav__brand")).containsText("CampusStore");
    }

    @Test
    void student_navShowsMyRequests_notAdminLinks() {
        page.navigate(BASE_URL + "/");

        // Students see My Requests
        assertThat(page.locator("a[href*='/requests/mine']")).isVisible();

        // Students do NOT see admin nav items
        assertThat(page.locator("a[href*='/admin/inventory']")).not().isVisible();
        assertThat(page.locator("a[href*='/admin/users']")).not().isVisible();
    }

    @Test
    void student_canNavigateToSearchPage() {
        page.locator("a[href*='/search']").first().click();

        assertThat(page).hasURL(Pattern.compile(".*/search.*"));
        assertThat(page.locator("#search-input")).isVisible();
    }

    @Test
    void student_canNavigateToMyRequestsPage() {
        page.locator("a[href*='/requests/mine']").click();

        assertThat(page).hasURL(Pattern.compile(".*/requests/mine.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void student_canNavigateToProfilePage() {
        page.locator("a[href*='/profile']").first().click();

        assertThat(page).hasURL(Pattern.compile(".*/profile.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void student_accessingAdminInventoryPage_seesError() {
        page.navigate(BASE_URL + "/admin/inventory");

        // Spring Security should return 403 for students accessing admin pages
        // The page either shows a 403 error page or redirects
        assertThat(page.locator("body")).isVisible();
        // The URL should not have successfully loaded admin inventory
        // (either 403 error page or redirect)
        String bodyText = page.locator("body").textContent();
        boolean isForbiddenPage = page.url().contains("/login")
                || bodyText.contains("403")
                || bodyText.contains("Forbidden")
                || bodyText.contains("Access Denied");
        assert isForbiddenPage : "Student should not be able to access admin pages, got URL: " + page.url();
    }

    @Test
    void student_homePageHasSearchBar() {
        page.navigate(BASE_URL + "/");

        // The home page has a search bar in the search section
        assertThat(page.locator(".fluent-section--search")).isVisible();
    }
}

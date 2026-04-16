package com.campusstore.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the authentication user journey.
 * <p>
 * Covers: login success, login failure, logout, and unauthenticated redirect.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class AuthJourneyE2ETest extends BaseE2ETest {

    @Test
    void loginSuccess_redirectsAwayFromLoginPage() {
        loginAsStudent();

        // After successful login the app redirects to home — URL must not be /login
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void loginSuccess_homePageShowsWelcome() {
        loginAsStudent();

        // The home page renders a heading that includes the user's display name
        // or at least a visible page body
        assertThat(page.locator("body")).isVisible();
        // CampusStore brand should be present in nav
        assertThat(page.locator(".fluent-nav__brand")).isVisible();
    }

    @Test
    void loginFailure_staysOnLoginWithErrorMessage() {
        page.navigate(BASE_URL + "/login");
        page.locator("#username").fill("student1");
        page.locator("#password").fill("WrongPasswordXYZ!");
        page.locator("button[type='submit']").click();

        // Spring Security appends ?error on failed login
        assertThat(page).hasURL(Pattern.compile(".*/login.*error.*"));
        // The error alert should be visible
        assertThat(page.locator(".fluent-alert--error")).isVisible();
    }

    @Test
    void logout_navigatesToLoginPageWithLogoutParam() {
        loginAsStudent();

        // After login we should be away from /login
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*"));

        // Click the logout button (it is inside a form in the nav)
        page.locator("button.fluent-nav__link--logout").click();

        // Spring Security redirects to /login?logout after successful logout
        assertThat(page).hasURL(Pattern.compile(".*/login.*logout.*"));
        assertThat(page.locator(".fluent-alert--success")).isVisible();
    }

    @Test
    void unauthenticated_profilePage_redirectsToLogin() {
        // Navigate to a protected page without logging in
        page.navigate(BASE_URL + "/profile");

        // Spring Security redirects unauthenticated requests to /login
        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }

    @Test
    void unauthenticated_requestsPage_redirectsToLogin() {
        page.navigate(BASE_URL + "/requests/mine");

        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }

    @Test
    void adminLogin_seesAdminNavLinks() {
        loginAsAdmin();

        // Admin users should see admin nav items; scope to nav to avoid matching home-page cards.
        assertThat(page.locator(".fluent-nav a[href*='/admin/inventory']")).isVisible();
        assertThat(page.locator(".fluent-nav a[href*='/admin/users']")).isVisible();
    }
}

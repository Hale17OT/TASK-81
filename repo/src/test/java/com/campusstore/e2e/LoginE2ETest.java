package com.campusstore.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E skeleton tests for the login page.
 * <p>
 * These tests require the full Docker environment to be running
 * (started by {@code run_tests.sh} or {@code docker compose up}).
 * They are tagged "e2e" so they only execute during the E2E phase.
 */
@Tag("e2e")
class LoginE2ETest extends BaseE2ETest {

    @Test
    void loginPageRenders() {
        page.navigate(BASE_URL + "/login");

        // The page should have loaded successfully (title present)
        assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*CampusStore.*|.*Login.*"));

        // The page body should contain visible content
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void loginFormExistsWithUsernameAndPasswordFields() {
        page.navigate(BASE_URL + "/login");

        // A form element should exist on the page
        Locator form = page.locator("form");
        assertThat(form).isVisible();

        // Username input field should be present
        Locator usernameField = page.locator("input[name='username']");
        assertThat(usernameField).isVisible();

        // Password input field should be present
        Locator passwordField = page.locator("input[name='password']");
        assertThat(passwordField).isVisible();

        // A submit button should exist
        Locator submitButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(
                java.util.regex.Pattern.compile("(?i)login|sign.?in|submit")));
        assertThat(submitButton).isVisible();
    }
}

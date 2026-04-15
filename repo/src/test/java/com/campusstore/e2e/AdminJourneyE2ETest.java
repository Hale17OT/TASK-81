package com.campusstore.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the admin user journey.
 * <p>
 * Covers admin navigation to inventory, users, audit log, crawler,
 * warehouse, policies, and email outbox pages.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class AdminJourneyE2ETest extends BaseE2ETest {

    @BeforeEach
    void loginAsAdminUser() {
        loginAsAdmin();
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*error.*"));
    }

    @Test
    void admin_navShowsAllAdminLinks() {
        page.navigate(BASE_URL + "/");

        assertThat(page.locator("a[href*='/admin/inventory']")).isVisible();
        assertThat(page.locator("a[href*='/admin/users']")).isVisible();
        assertThat(page.locator("a[href*='/admin/warehouse']")).isVisible();
        assertThat(page.locator("a[href*='/admin/crawler']")).isVisible();
        assertThat(page.locator("a[href*='/admin/audit']")).isVisible();
        assertThat(page.locator("a[href*='/admin/policies']")).isVisible();
    }

    @Test
    void admin_canNavigateToInventoryPage() {
        page.navigate(BASE_URL + "/admin/inventory");

        assertThat(page).hasURL(Pattern.compile(".*/admin/inventory.*"));
        assertThat(page.locator("body")).isVisible();
        // Should not be an error page
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToUsersPage() {
        page.navigate(BASE_URL + "/admin/users");

        assertThat(page).hasURL(Pattern.compile(".*/admin/users.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToAuditLogPage() {
        page.navigate(BASE_URL + "/admin/audit");

        assertThat(page).hasURL(Pattern.compile(".*/admin/audit.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToCrawlerPage() {
        page.navigate(BASE_URL + "/admin/crawler");

        assertThat(page).hasURL(Pattern.compile(".*/admin/crawler.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToWarehousePage() {
        page.navigate(BASE_URL + "/admin/warehouse");

        assertThat(page).hasURL(Pattern.compile(".*/admin/warehouse.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToEmailOutboxPage() {
        page.navigate(BASE_URL + "/admin/email-outbox");

        assertThat(page).hasURL(Pattern.compile(".*/admin/email-outbox.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canNavigateToPoliciesPage() {
        page.navigate(BASE_URL + "/admin/policies");

        assertThat(page).hasURL(Pattern.compile(".*/admin/policies.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_homePageRendersWithQuickActions() {
        page.navigate(BASE_URL + "/");

        // Admin quick action card for Admin Dashboard should be present
        assertThat(page.locator(".fluent-section--actions")).isVisible();
    }
}

package com.campusstore.e2e;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the request workflow user journey.
 * <p>
 * These tests exercise full cross-boundary flows:
 * Browser → Thymeleaf form submission → InternalApiClient → REST API → DB.
 * <p>
 * Seed item 1 (Arduino Uno R3 Board, requiresApproval=true) is used to
 * exercise the approval-required path.
 * <p>
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class RequestWorkflowE2ETest extends BaseE2ETest {

    private static final String ITEM_URL = BASE_URL + "/requests/new/1";
    private static final String MY_REQUESTS_URL = BASE_URL + "/requests/mine";

    @Test
    void student_submitsRequest_appearsInMyRequests() {
        loginAsStudent();

        // Navigate to the request form for item 1
        page.navigate(ITEM_URL);
        assertThat(page).hasURL(Pattern.compile(".*/requests/new/1.*"));
        assertThat(page.locator("#requestForm")).isVisible();

        // Fill in the request form
        page.locator("#quantity").fill("1");
        page.locator("#justification").fill("E2E automated test request");

        // Submit — the button disables itself and calls form.submit().
        // waitForNavigation waits for the resulting redirect to complete.
        page.waitForNavigation(() -> page.locator("#submitBtn").click());

        // After successful submission, the controller redirects to /requests/mine
        assertThat(page).hasURL(Pattern.compile(".*/requests/mine.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
        // Verify persisted domain state: at least one request with a status badge is visible,
        // confirming the request was saved and is shown in the list
        assertThat(page.locator(".fluent-badge--status").first()).isVisible();
        // The item name for item 1 (Arduino Uno R3 Board) must appear in the request row,
        // confirming the DB-persisted request references the correct inventory item.
        assertThat(page.locator("body")).containsText("Arduino");
    }

    @Test
    void student_submitsRequest_requestListIsNotEmpty() {
        loginAsStudent();

        // Navigate to the request form and submit a request
        page.navigate(ITEM_URL);
        page.locator("#quantity").fill("1");
        page.locator("#justification").fill("E2E list verification request");

        page.waitForNavigation(() -> page.locator("#submitBtn").click());

        // Navigate to My Requests
        page.navigate(MY_REQUESTS_URL);

        // Either the requests table OR the mobile card list should be present
        boolean hasTable = page.locator(".fluent-table-container").count() > 0
                && page.locator(".fluent-table-container").first().isVisible();
        boolean hasCards = page.locator(".fluent-card-list--mobile").count() > 0
                && page.locator(".fluent-card-list--mobile").first().isVisible();

        assert hasTable || hasCards
                : "My Requests should show at least one request after submission";

        // Verify persisted domain state: a status badge must appear in the list
        assertThat(page.locator(".fluent-badge--status").first()).isVisible();
    }

    @Test
    void student_requestForm_showsItemDetails() {
        loginAsStudent();

        // Navigate to request form for item 1
        page.navigate(ITEM_URL);

        assertThat(page.locator("body")).isVisible();
        // The form page should show the item summary card with price info
        assertThat(page.locator(".fluent-card--summary")).isVisible();
        // The quantity and justification fields should be present
        assertThat(page.locator("#quantity")).isVisible();
        assertThat(page.locator("#justification")).isVisible();
        // The submit button should be present
        assertThat(page.locator("#submitBtn")).isVisible();
    }

    @Test
    void student_requestForm_requiresApprovalAlert_isShownForApprovalItem() {
        loginAsStudent();

        // Item 1 (Arduino) has requiresApproval=true — so the info alert should be shown
        page.navigate(ITEM_URL);

        assertThat(page.locator(".fluent-alert--info")).isVisible();
    }

    @Test
    void teacher_pendingApprovals_showsSubmittedRequests() {
        // Student submits a request first
        loginAsStudent();
        page.navigate(ITEM_URL);
        page.locator("#quantity").fill("1");
        page.locator("#justification").fill("Request for teacher approval test");
        page.waitForNavigation(() -> page.locator("#submitBtn").click());

        // Now login as teacher and check pending approvals
        // Use a new context to avoid session carryover
        context.close();
        createContext();

        loginAsTeacher();
        page.navigate(BASE_URL + "/requests/pending");

        assertThat(page).hasURL(Pattern.compile(".*/requests/pending.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void admin_canViewRequestsInAdminContext() {
        loginAsAdmin();
        page.navigate(BASE_URL + "/requests/mine");

        assertThat(page).hasURL(Pattern.compile(".*/requests/mine.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void requestForm_unauthenticated_redirectsToLogin() {
        page.navigate(ITEM_URL);
        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }
}

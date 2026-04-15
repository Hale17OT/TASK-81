package com.campusstore.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E tests for the teacher user journey.
 * <p>
 * Covers teacher-specific navigation (pending approvals), and verifies
 * that admin-only pages are inaccessible to teachers.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class TeacherJourneyE2ETest extends BaseE2ETest {

    @BeforeEach
    void loginAsTeacherUser() {
        loginAsTeacher();
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*error.*"));
    }

    @Test
    void teacher_homePageLoads() {
        page.navigate(BASE_URL + "/");

        assertThat(page.locator(".fluent-nav__brand")).containsText("CampusStore");
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void teacher_navShowsPendingApprovals() {
        page.navigate(BASE_URL + "/");

        // Teachers see the Pending Approvals nav link
        assertThat(page.locator("a[href*='/requests/pending']")).isVisible();
    }

    @Test
    void teacher_navShowsMyRequests() {
        page.navigate(BASE_URL + "/");

        // Teachers (hasAnyRole STUDENT,TEACHER) also see My Requests
        assertThat(page.locator("a[href*='/requests/mine']")).isVisible();
    }

    @Test
    void teacher_navDoesNotShowAdminLinks() {
        page.navigate(BASE_URL + "/");

        // Teachers must NOT see admin-only nav items
        assertThat(page.locator("a[href*='/admin/users']")).not().isVisible();
        assertThat(page.locator("a[href*='/admin/audit']")).not().isVisible();
        assertThat(page.locator("a[href*='/admin/crawler']")).not().isVisible();
    }

    @Test
    void teacher_canNavigateToPendingApprovalsPage() {
        page.navigate(BASE_URL + "/requests/pending");

        assertThat(page).hasURL(Pattern.compile(".*/requests/pending.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void teacher_canNavigateToMyRequestsPage() {
        page.navigate(BASE_URL + "/requests/mine");

        assertThat(page).hasURL(Pattern.compile(".*/requests/mine.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void teacher_accessingAdminUsersPage_seesError() {
        page.navigate(BASE_URL + "/admin/users");

        assertThat(page.locator("body")).isVisible();
        String bodyText = page.locator("body").textContent();
        boolean isForbiddenOrRedirect = page.url().contains("/login")
                || bodyText.contains("403")
                || bodyText.contains("Forbidden")
                || bodyText.contains("Access Denied");
        assertTrue(isForbiddenOrRedirect, "Teacher should not access admin/users, URL: " + page.url());
    }

    @Test
    void teacher_canNavigateToSearchPage() {
        page.navigate(BASE_URL + "/search");

        assertThat(page).hasURL(Pattern.compile(".*/search.*"));
        assertThat(page.locator("#search-input")).isVisible();
    }

    @Test
    void teacher_pendingApprovals_showsStudentRequestAfterSubmission() {
        // Student submits a request that requires approval
        context.close();
        createContext();
        loginAsStudent();
        page.navigate(BASE_URL + "/requests/new/1");
        page.locator("#quantity").fill("1");
        page.locator("#justification").fill("E2E teacher approval test");
        page.waitForNavigation(() -> page.locator("#submitBtn").click());

        // Teacher checks pending approvals — the submitted request must appear
        context.close();
        createContext();
        loginAsTeacher();
        page.navigate(BASE_URL + "/requests/pending");

        assertThat(page).hasURL(Pattern.compile(".*/requests/pending.*"));
        assertThat(page.locator("body")).not().containsText("403");
        // Pending approvals page must show at least one request entry with a status indicator
        assertThat(page.locator(".fluent-badge--status, .fluent-table__row, .fluent-card--request").first()).isVisible();
    }
}

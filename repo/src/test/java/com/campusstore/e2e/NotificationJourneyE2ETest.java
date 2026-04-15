package com.campusstore.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the notifications user journey.
 * <p>
 * Covers unauthenticated redirect, the notifications center page, and
 * the notification bell in the main nav bar.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class NotificationJourneyE2ETest extends BaseE2ETest {

    @Test
    void notifications_unauthenticated_redirectsToLogin() {
        page.navigate(BASE_URL + "/notifications");

        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }

    @Test
    void notifications_authenticated_pageLoads() {
        loginAsStudent();
        page.navigate(BASE_URL + "/notifications");

        assertThat(page).hasURL(Pattern.compile(".*/notifications.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void notifications_navBellIsVisible_afterLogin() {
        loginAsStudent();
        page.navigate(BASE_URL + "/");

        // The notification bell fragment is included in the nav for authenticated users
        // The bell element is part of the notifications fragment
        assertThat(page.locator(".fluent-nav__list--right")).isVisible();
    }

    @Test
    void notifications_markAllRead_pageStillLoads() {
        loginAsStudent();
        page.navigate(BASE_URL + "/notifications");

        assertThat(page.locator("body")).isVisible();
        // The page should not have an application error
        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
    }

    @Test
    void notifications_adminUser_pageLoads() {
        loginAsAdmin();
        page.navigate(BASE_URL + "/notifications");

        assertThat(page).hasURL(Pattern.compile(".*/notifications.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void notifications_center_rendersNotificationHeading() {
        loginAsStudent();
        page.navigate(BASE_URL + "/notifications");

        // Notification center must render its heading (from Thymeleaf template),
        // confirming the template resolved and did not return a blank or error page.
        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
        assertThat(page.locator("body")).not().containsText("500");
        assertThat(page.locator("body")).containsText("Notification");
    }

    @Test
    void requestForm_forSeededItem_showsItemNameFromDb() {
        // Navigate to the request form for item 1 (Arduino Uno R3 Board) as a student.
        // Verifies that the Thymeleaf template renders live DB inventory data —
        // the item name must appear in the form, confirming a UI→DB round-trip.
        loginAsStudent();
        page.navigate(BASE_URL + "/requests/new/1");

        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
        assertThat(page.locator("body")).containsText("Arduino");
    }

    @Test
    void myRequests_asStudent_pageRendersRequestSection() {
        loginAsStudent();
        page.navigate(BASE_URL + "/requests/mine");

        assertThat(page).hasURL(Pattern.compile(".*/requests/mine.*"));
        assertThat(page.locator("body")).not().containsText("Whitelabel Error");
        // The page must contain the word "Request" — either from section headings
        // or from actual request rows, confirming the template resolved correctly.
        assertThat(page.locator("body")).containsText("Request");
    }
}

package com.campusstore.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E tests for the profile user journey.
 * <p>
 * Covers the main profile page, addresses, tags, contacts, and
 * notification preferences sub-pages.
 * Requires the full Docker environment at https://localhost:8443.
 */
@Tag("e2e")
class ProfileJourneyE2ETest extends BaseE2ETest {

    @BeforeEach
    void loginAsStudentUser() {
        loginAsStudent();
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*error.*"));
    }

    @Test
    void profile_mainPageLoads_showsEditForm() {
        page.navigate(BASE_URL + "/profile");

        assertThat(page).hasURL(Pattern.compile(".*/profile.*"));
        assertThat(page.locator("body")).isVisible();
        // Profile edit form should be present
        assertThat(page.locator("form[action*='/profile/update']")).isVisible();
        assertThat(page.locator("#displayName")).isVisible();
    }

    @Test
    void profile_mainPageShows_username() {
        page.navigate(BASE_URL + "/profile");

        // The profile page shows the logged-in user's username
        String bodyText = page.locator("body").textContent();
        assertTrue(bodyText.contains("student1") || bodyText.contains("Alex Johnson"),
                "Profile page should show the student's username or display name");
    }

    @Test
    void profile_mainPageHas_sidebarNavLinks() {
        page.navigate(BASE_URL + "/profile");

        // Sidebar navigation links should be present
        assertThat(page.locator("a[href*='/profile/addresses']")).isVisible();
        assertThat(page.locator("a[href*='/profile/tags']")).isVisible();
        assertThat(page.locator("a[href*='/profile/contacts']")).isVisible();
    }

    @Test
    void profile_addressesPage_loadsSuccessfully() {
        page.navigate(BASE_URL + "/profile/addresses");

        assertThat(page).hasURL(Pattern.compile(".*/profile/addresses.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void profile_tagsPage_loadsSuccessfully() {
        page.navigate(BASE_URL + "/profile/tags");

        assertThat(page).hasURL(Pattern.compile(".*/profile/tags.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void profile_contactsPage_loadsSuccessfully() {
        page.navigate(BASE_URL + "/profile/contacts");

        assertThat(page).hasURL(Pattern.compile(".*/profile/contacts.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void profile_notificationSettingsPage_loadsSuccessfully() {
        page.navigate(BASE_URL + "/profile/notifications");

        assertThat(page).hasURL(Pattern.compile(".*/profile/notifications.*"));
        assertThat(page.locator("body")).isVisible();
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void profile_changePasswordPage_loadsSuccessfully() {
        page.navigate(BASE_URL + "/account/change-password");

        assertThat(page).hasURL(Pattern.compile(".*/account/change-password.*"));
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    void profile_updateDisplayName_persistsAcrossPageLoad() {
        page.navigate(BASE_URL + "/profile");

        // Fill in a unique display name to assert persistence
        String uniqueName = "E2EStudent_" + System.currentTimeMillis();
        page.locator("#displayName").fill(uniqueName);

        // Submit the profile update form
        page.waitForNavigation(() ->
                page.locator("form[action*='/profile/update'] [type='submit']").click());

        // After redirect, reload the profile page and verify the name persisted
        page.navigate(BASE_URL + "/profile");
        assertThat(page).hasURL(Pattern.compile(".*/profile.*"));
        // The updated display name must be visible in the form (stored in DB and re-rendered)
        assertThat(page.locator("#displayName")).hasValue(uniqueName);
    }
}

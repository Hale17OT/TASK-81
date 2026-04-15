package com.campusstore.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

/**
 * Base class for Playwright E2E tests.
 * <p>
 * Configures headless Chromium and manages lifecycle of
 * Playwright, Browser, BrowserContext, and Page instances.
 * <p>
 * Subclasses get a fresh {@link Page} per test method.
 * The browser targets the Docker-hosted app at https://localhost:8443
 * and ignores the self-signed TLS certificate.
 */
@Tag("e2e")
public abstract class BaseE2ETest {

    protected static final String BASE_URL = "https://localhost:8443";

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContext() {
        context = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true)
        );
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (page != null) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
    }

    /**
     * Performs a login using the standard Thymeleaf login form at {@code /login}.
     * After this call the page will have navigated away from /login on success,
     * or remain on /login?error on failure.
     */
    protected void loginWith(String username, String password) {
        page.navigate(BASE_URL + "/login");
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("button[type='submit']").click();
    }

    /** Logs in as the Docker seed admin user. */
    protected void loginAsAdmin() {
        loginWith("admin", "admin123");
    }

    /** Logs in as a Docker seed teacher user. */
    protected void loginAsTeacher() {
        loginWith("teacher1", "teacher123");
    }

    /** Logs in as a Docker seed student user. */
    protected void loginAsStudent() {
        loginWith("student1", "student123");
    }
}

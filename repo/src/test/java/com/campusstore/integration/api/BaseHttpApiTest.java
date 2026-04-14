package com.campusstore.integration.api;

import com.campusstore.api.dto.LoginRequest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.HttpCookie;
import java.util.List;

/**
 * Abstract base class for HTTP black-box API integration tests.
 * <p>
 * Boots the full application on a random port with an H2 database.
 * Uses {@link TestRestTemplate} for real HTTP calls (no MockMvc, no mocking).
 * Test data is seeded by {@link TestDataConfig} on application startup.
 * CSRF is disabled for these tests via a property override so that
 * {@link TestRestTemplate} can issue POST/PUT/DELETE without CSRF tokens.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "campusstore.csrf.enabled=false")
@Import(TestDataConfig.class)
abstract class BaseHttpApiTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @LocalServerPort
    protected int port;

    /**
     * Authenticates via POST /api/auth/login and returns an {@link HttpClient}
     * wrapper that injects the session cookie on every subsequent request.
     */
    protected HttpClient loginAs(String username, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", entity, String.class);

        // Extract session cookies from Set-Cookie headers
        List<String> setCookies = loginResponse.getHeaders().get("Set-Cookie");
        StringBuilder cookieHeader = new StringBuilder();
        if (setCookies != null) {
            for (String setCookie : setCookies) {
                List<HttpCookie> cookies = HttpCookie.parse(setCookie);
                for (HttpCookie cookie : cookies) {
                    if (cookieHeader.length() > 0) {
                        cookieHeader.append("; ");
                    }
                    cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());
                }
            }
        }

        return new HttpClient(restTemplate, cookieHeader.toString());
    }

    protected HttpClient adminClient() {
        return loginAs("testadmin", "Admin123!");
    }

    protected HttpClient teacherClient() {
        return loginAs("testteacher", "Teacher123!");
    }

    protected HttpClient studentClient() {
        return loginAs("teststudent", "Student123!");
    }

    /**
     * Lightweight authenticated HTTP client that delegates to {@link TestRestTemplate}
     * and injects session cookies on every request.
     */
    static class HttpClient {

        private final TestRestTemplate delegate;
        private final String cookie;

        HttpClient(TestRestTemplate delegate, String cookie) {
            this.delegate = delegate;
            this.cookie = cookie;
        }

        private HttpHeaders headersWithCookie() {
            HttpHeaders h = new HttpHeaders();
            if (cookie != null && !cookie.isEmpty()) {
                h.set("Cookie", cookie);
            }
            return h;
        }

        public <T> ResponseEntity<T> get(String url, Class<T> responseType, Object... urlVariables) {
            HttpEntity<?> entity = new HttpEntity<>(headersWithCookie());
            return delegate.exchange(url, HttpMethod.GET, entity, responseType, urlVariables);
        }

        public <T> ResponseEntity<T> post(String url, Object body, Class<T> responseType, Object... urlVariables) {
            HttpHeaders h = headersWithCookie();
            h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(body, h);
            return delegate.exchange(url, HttpMethod.POST, entity, responseType, urlVariables);
        }

        public <T> ResponseEntity<T> put(String url, Object body, Class<T> responseType, Object... urlVariables) {
            HttpHeaders h = headersWithCookie();
            h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(body, h);
            return delegate.exchange(url, HttpMethod.PUT, entity, responseType, urlVariables);
        }

        public <T> ResponseEntity<T> delete(String url, Class<T> responseType, Object... urlVariables) {
            HttpEntity<?> entity = new HttpEntity<>(headersWithCookie());
            return delegate.exchange(url, HttpMethod.DELETE, entity, responseType, urlVariables);
        }
    }
}

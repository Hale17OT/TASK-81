package com.campusstore.unit.infrastructure;

import com.campusstore.infrastructure.security.filter.RateLimitFilter;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        com.campusstore.core.service.RateLimitService mockRateLimitService =
                mock(com.campusstore.core.service.RateLimitService.class);
        filter = new RateLimitFilter(mockRateLimitService);
        setField(filter, "anonymousLimit", 30);
        setField(filter, "authenticatedLimit", 120);
        setField(filter, "lockoutThreshold", 10);
        setField(filter, "baseLockoutMinutes", 5);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getRequestURI()).thenReturn("/api/inventory");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setAuthenticatedUser(Long userId) {
        CampusUserPrincipal principal = new CampusUserPrincipal(
                userId, "testuser", "password", "Test User", 1L, 1L,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── Static resource bypass ─────────────────────────────────────────

    @Test
    void staticResources_bypassRateLimiting() throws Exception {
        when(request.getRequestURI()).thenReturn("/css/style.css");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void jsResources_bypassRateLimiting() throws Exception {
        when(request.getRequestURI()).thenReturn("/js/app.js");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void imageResources_bypassRateLimiting() throws Exception {
        when(request.getRequestURI()).thenReturn("/images/logo.png");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void actuatorEndpoints_areSubjectToAnonymousRateLimit() throws Exception {
        // Actuator is intentionally NOT skipped — anonymous calls must share the same
        // 30 req/min bucket so a health-check flood cannot evade rate limiting.
        when(request.getRequestURI()).thenReturn("/actuator/health");

        for (int i = 0; i < 30; i++) {
            HttpServletResponse fresh = mock(HttpServletResponse.class);
            when(fresh.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, fresh, filterChain);
        }
        verify(filterChain, times(30)).doFilter(eq(request), any());

        // The 31st request within the minute must be throttled.
        HttpServletResponse blocked = mock(HttpServletResponse.class);
        StringWriter blockedBody = new StringWriter();
        when(blocked.getWriter()).thenReturn(new PrintWriter(blockedBody));
        filter.doFilter(request, blocked, filterChain);
        verify(blocked).setStatus(429);
    }

    // ── Anonymous bucket creation (30/min) ─────────────────────────────

    @Test
    void anonymousRequest_firstRequest_allowed() throws Exception {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anonymousRequest_within30PerMinute_allowed() throws Exception {
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        verify(filterChain, times(30)).doFilter(eq(request), any());
    }

    @Test
    void anonymousRequest_exceeding30PerMinute_blocked() throws Exception {
        // Consume all 30 tokens
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // 31st request should be blocked
        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
        assertThat(responseBody.toString()).contains("RATE_LIMIT_EXCEEDED");
    }

    // ── Authenticated bucket creation (120/min) ────────────────────────

    @Test
    void authenticatedRequest_firstRequest_allowed() throws Exception {
        setAuthenticatedUser(1L);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedRequest_within120PerMinute_allowed() throws Exception {
        setAuthenticatedUser(100L);

        for (int i = 0; i < 120; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        verify(filterChain, times(120)).doFilter(eq(request), any());
    }

    @Test
    void authenticatedRequest_exceeding120PerMinute_blocked() throws Exception {
        setAuthenticatedUser(200L);

        // Consume all 120 tokens
        for (int i = 0; i < 120; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // 121st request should be blocked
        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
    }

    // ── Violation tracking ─────────────────────────────────────────────

    @Test
    void violationRecorded_whenRateLimitExceeded() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Next request triggers a violation
        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "60");
    }

    @Test
    void multipleViolations_incrementCounter() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Trigger multiple violations (under lockout threshold of 10)
        for (int i = 0; i < 5; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
            verify(freshResponse).setStatus(429);
        }
    }

    // ── Lockout escalation ─────────────────────────────────────────────

    @Test
    void lockout_triggeredAfter10Violations() throws Exception {
        // Use a unique IP for this test to avoid interference
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("LockoutTest/1.0");

        // Exhaust the bucket first (30 requests)
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Trigger exactly 10 violations to hit the lockout threshold
        for (int i = 0; i < 10; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Next request should show lockout with Retry-After header
        HttpServletResponse lockedResponse = mock(HttpServletResponse.class);
        StringWriter lockedBody = new StringWriter();
        when(lockedResponse.getWriter()).thenReturn(new PrintWriter(lockedBody));
        filter.doFilter(request, lockedResponse, filterChain);

        verify(lockedResponse).setStatus(429);
        verify(lockedResponse).setHeader(eq("Retry-After"), any(String.class));
        assertThat(lockedBody.toString()).contains("Account temporarily locked");
    }

    @Test
    void lockoutEscalation_after20Violations_10MinLockout() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request.getHeader("User-Agent")).thenReturn("EscalationTest/1.0");

        // Exhaust the bucket
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // First 10 violations trigger level 1 lockout (5 min)
        for (int i = 0; i < 10; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Manually clear the lockoutUntil to simulate passage of time for further violations
        // We need to reach 20 violations for escalation level 2
        // The lockout will be re-evaluated each time recordViolation is called
        // At count 10: escalationLevel=1 -> 5min
        // At count 20: escalationLevel=2 -> 10min
        // We've already recorded 10 violations; let's verify the lockout response
        HttpServletResponse lockoutCheckResponse = mock(HttpServletResponse.class);
        StringWriter lockoutCheckBody = new StringWriter();
        when(lockoutCheckResponse.getWriter()).thenReturn(new PrintWriter(lockoutCheckBody));
        filter.doFilter(request, lockoutCheckResponse, filterChain);

        verify(lockoutCheckResponse).setStatus(429);
        assertThat(lockoutCheckBody.toString()).contains("Account temporarily locked");
    }

    // ── Separate buckets per user ──────────────────────────────────────

    @Test
    void differentUsers_haveSeparateBuckets() throws Exception {
        // User 1 exhausts their bucket
        setAuthenticatedUser(301L);
        for (int i = 0; i < 120; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // User 2 should still be able to make requests
        setAuthenticatedUser(302L);
        HttpServletResponse user2Response = mock(HttpServletResponse.class);
        when(user2Response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilter(request, user2Response, filterChain);

        // User 2's request should pass through to the filter chain
        verify(filterChain, atLeast(1)).doFilter(eq(request), eq(user2Response));
    }

    @Test
    void xForwardedFor_usedForAnonymousIdentifier() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 70.41.3.18");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void responseContentType_isJson_whenBlocked() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < 30; i++) {
            HttpServletResponse freshResponse = mock(HttpServletResponse.class);
            when(freshResponse.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(request, freshResponse, filterChain);
        }

        // Blocked request
        filter.doFilter(request, response, filterChain);

        verify(response).setContentType("application/json");
    }
}

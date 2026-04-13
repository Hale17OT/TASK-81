package com.campusstore.infrastructure.security.filter;

import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ViolationTracker> violations = new ConcurrentHashMap<>();
    private final com.campusstore.core.service.RateLimitService rateLimitService;

    @Value("${campusstore.rate-limit.anonymous-requests-per-minute:30}")
    private int anonymousLimit;

    @Value("${campusstore.rate-limit.authenticated-requests-per-minute:120}")
    private int authenticatedLimit;

    @Value("${campusstore.rate-limit.lockout-threshold:10}")
    private int lockoutThreshold;

    @Value("${campusstore.rate-limit.lockout-duration-minutes:5}")
    private int baseLockoutMinutes;

    public RateLimitFilter(com.campusstore.core.service.RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Skip static resources. Actuator is intentionally NOT skipped — health and
        // metrics endpoints are reachable anonymously and must be bounded by the same
        // 30 req/min anonymous policy the rest of the surface uses.
        String path = request.getRequestURI();
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip internal loopback calls from the web layer's RestClient. A single page
        // render fans out to several /api/** calls and they must not drain the user's
        // bucket. The shared secret is generated at startup and known only to this JVM.
        String loopback = request.getHeader(com.campusstore.web.client.RestClientConfig.LOOPBACK_HEADER);
        if (loopback != null && constantTimeEquals(loopback,
                com.campusstore.web.client.RestClientConfig.getLoopbackSecret())) {
            filterChain.doFilter(request, response);
            return;
        }

        String identifier = resolveIdentifier(request);
        boolean authenticated = isAuthenticated();

        // Check lockout — first in-memory, then DB-backed (restart-resilient)
        ViolationTracker tracker = violations.get(identifier);
        boolean lockedOut = (tracker != null && tracker.lockoutUntil != null
                && tracker.lockoutUntil.isAfter(LocalDateTime.now()));
        if (!lockedOut) {
            try { lockedOut = rateLimitService.isLockedOut(identifier); }
            catch (Exception e) { /* DB unavailable — rely on in-memory only */ }
        }
        if (lockedOut) {
            long retryAfterSeconds = (tracker != null && tracker.lockoutUntil != null)
                    ? Duration.between(LocalDateTime.now(), tracker.lockoutUntil).getSeconds()
                    : 300; // default 5 min if only DB-locked
            log.warn("Rate limit lockout active for identifier: {}", identifier.substring(0, Math.min(8, identifier.length())) + "...");
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Account temporarily locked. Retry after " + retryAfterSeconds + " seconds.\"}}");
            return;
        }

        Bucket bucket = getOrCreateBucket(identifier, authenticated);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            recordViolation(identifier);
            log.info("Rate limit exceeded for identifier: {}", identifier.substring(0, Math.min(8, identifier.length())) + "...");
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Please wait and try again.\"}}");
        }
    }

    private String resolveIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CampusUserPrincipal principal) {
            return "user:" + principal.getUserId();
        }
        // Anonymous: hash IP + User-Agent to handle NAT/proxy environments
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown";
        return sha256(ip + ":" + userAgent);
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CampusUserPrincipal;
    }

    private Bucket getOrCreateBucket(String key, boolean authenticated) {
        return buckets.computeIfAbsent(key, k -> {
            int limit = authenticated ? authenticatedLimit : anonymousLimit;
            Bandwidth bandwidth = Bandwidth.builder()
                    .capacity(limit)
                    .refillGreedy(limit, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(bandwidth).build();
        });
    }

    private void recordViolation(String identifier) {
        // In-memory tracking for fast path
        ViolationTracker tracker = violations.computeIfAbsent(identifier, k -> new ViolationTracker());
        int count = tracker.count.incrementAndGet();

        // Persist to DB for restart-resilient governance
        try {
            String type = identifier.startsWith("user:") ? "AUTHENTICATED" : "ANONYMOUS";
            rateLimitService.recordViolation(identifier, type);
        } catch (Exception e) {
            log.debug("Failed to persist rate limit violation to DB (non-critical): {}", e.getMessage());
        }

        if (count >= lockoutThreshold) {
            int escalationLevel = count / lockoutThreshold;
            int lockoutMinutes = switch (escalationLevel) {
                case 1 -> baseLockoutMinutes;       // 5 min
                case 2 -> baseLockoutMinutes * 2;    // 10 min
                case 3 -> baseLockoutMinutes * 6;    // 30 min
                default -> baseLockoutMinutes * 12;   // 60 min
            };
            tracker.lockoutUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            // Persist lockout to DB for restart resilience
            try {
                rateLimitService.setLockoutUntil(identifier,
                        java.time.Instant.now().plus(lockoutMinutes, java.time.temporal.ChronoUnit.MINUTES));
            } catch (Exception e) {
                log.debug("Failed to persist lockout to DB: {}", e.getMessage());
            }
            log.warn("Escalating lockout for {}: {} minutes (violations: {})",
                    identifier.substring(0, Math.min(8, identifier.length())) + "...", lockoutMinutes, count);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Constant-time string comparison so the loopback secret isn't probeable via timing. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }

    private static class ViolationTracker {
        final AtomicInteger count = new AtomicInteger(0);
        volatile LocalDateTime lockoutUntil;
    }
}

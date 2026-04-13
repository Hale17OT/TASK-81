package com.campusstore.web.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;

/**
 * RestClient configuration for the Thymeleaf web layer to consume the application's own
 * REST API over HTTP loopback.
 *
 * Design:
 * - Target: {@code https://localhost:${server.port}} (or HTTP if SSL is disabled, e.g. test profile).
 * - TLS: trust-all context — the server uses a self-signed certificate; we're talking to
 *   ourselves over the loopback interface, so certificate validation has no threat model here.
 * - Auth: a {@link ClientHttpRequestInterceptor} reads the inbound {@code HttpServletRequest}
 *   via {@link RequestContextHolder} and forwards its {@code JSESSIONID} cookie so the API
 *   call authenticates as the same user. For state-changing methods (POST/PUT/DELETE/PATCH)
 *   it also forwards the CSRF token as the {@code X-XSRF-TOKEN} header (the CSRF repository
 *   is cookie-backed).
 * - Loopback identification: every outbound request carries a per-process shared-secret header
 *   {@code X-Internal-Loopback} whose value is {@link #getLoopbackSecret()}. The rate-limit
 *   filter checks this header and skips its bucket accounting for confirmed loopback calls,
 *   so a page render that fans out to several API endpoints does not drain the user's bucket.
 */
@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    /**
     * Header name used to identify an internal loopback API call. Set on every request
     * from the web-layer RestClient; verified by {@code RateLimitFilter} using a
     * constant-time comparison against {@link #getLoopbackSecret()}.
     */
    public static final String LOOPBACK_HEADER = "X-Internal-Loopback";

    /**
     * Per-process secret regenerated on each startup. Because the web client and the
     * rate-limit filter live in the same JVM, they share the value directly — no external
     * rotation or store is needed.
     */
    private static final String LOOPBACK_SECRET = UUID.randomUUID().toString();

    /** Accessor so {@code RateLimitFilter} can validate incoming loopback headers. */
    public static String getLoopbackSecret() {
        return LOOPBACK_SECRET;
    }

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Bean
    public RestClient internalApiRestClient() throws Exception {
        HttpClient.Builder httpBuilder = HttpClient.newBuilder();
        if (sslEnabled) {
            httpBuilder.sslContext(trustAllSslContext());
        }
        HttpClient httpClient = httpBuilder.build();

        String scheme = sslEnabled ? "https" : "http";
        String baseUrl = scheme + "://localhost:" + serverPort;
        log.info("Initialising internal REST client with base URL {}", baseUrl);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .requestInterceptor(loopbackInterceptor())
                .build();
    }

    /**
     * Intercepts every outbound call to inject the loopback secret, the inbound session
     * cookie, and (for state-changing methods) the CSRF token pulled from the inbound
     * cookies. This is the mechanism that stitches loopback HTTP calls to the user's
     * already-authenticated web session.
     */
    private ClientHttpRequestInterceptor loopbackInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().set(LOOPBACK_HEADER, LOOPBACK_SECRET);

            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest inbound = attrs.getRequest();
                Cookie[] cookies = inbound.getCookies();
                String xsrf = null;
                StringBuilder cookieHeader = new StringBuilder();
                if (cookies != null) {
                    for (Cookie c : cookies) {
                        if ("JSESSIONID".equals(c.getName()) || "XSRF-TOKEN".equals(c.getName())) {
                            if (cookieHeader.length() > 0) cookieHeader.append("; ");
                            cookieHeader.append(c.getName()).append('=').append(c.getValue());
                        }
                        if ("XSRF-TOKEN".equals(c.getName())) {
                            xsrf = c.getValue();
                        }
                    }
                }
                if (cookieHeader.length() > 0) {
                    request.getHeaders().add("Cookie", cookieHeader.toString());
                }
                String method = request.getMethod().name();
                boolean stateChanging = !("GET".equals(method) || "HEAD".equals(method)
                        || "OPTIONS".equals(method) || "TRACE".equals(method));
                if (stateChanging && xsrf != null) {
                    request.getHeaders().set("X-XSRF-TOKEN", xsrf);
                }
            }

            return execution.execute(request, body);
        };
    }

    /**
     * Trust-all SSL context for the loopback call. This is safe here because the client
     * connects only to {@code localhost} and the "server" on the other end is this same
     * JVM's Tomcat instance using a self-signed certificate generated at build time.
     */
    private static SSLContext trustAllSslContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }};
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }
}

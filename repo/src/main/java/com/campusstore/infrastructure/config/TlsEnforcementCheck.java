package com.campusstore.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Startup guard: enforce TLS in non-test profiles.
 *
 * The platform's offline / on-prem deployment promise requires forced TLS for all local
 * traffic. Disabling SSL via {@code SERVER_SSL_ENABLED=false} is only acceptable inside the
 * {@code test} profile (where MockMvc / H2 integration tests run plaintext). If anyone tries
 * to bring up the server with SSL disabled in any other profile, refuse to start so the
 * misconfiguration is impossible to ship.
 */
@Component
public class TlsEnforcementCheck {

    private static final Logger log = LoggerFactory.getLogger(TlsEnforcementCheck.class);

    /** Profiles where plaintext is allowed (unit/integration tests). */
    private static final Set<String> PLAINTEXT_ALLOWED_PROFILES = Set.of("test");

    private final Environment environment;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    public TlsEnforcementCheck(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        boolean isTestProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PLAINTEXT_ALLOWED_PROFILES::contains);
        if (!sslEnabled && !isTestProfile) {
            throw new IllegalStateException(
                    "TLS is disabled (server.ssl.enabled=false) outside the 'test' profile. "
                  + "CampusStore requires TLS for all on-prem traffic. Refusing to start.");
        }
        if (sslEnabled) {
            log.info("TLS enforcement check passed: server.ssl.enabled=true");
        } else {
            log.warn("TLS disabled — allowed only because active profile is 'test'");
        }
    }
}

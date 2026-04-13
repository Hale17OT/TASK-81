package com.campusstore.infrastructure.config;

import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * On first startup, seed data contains raw BCrypt hashes stored as BINARY.
 * This initializer detects them and AES-encrypts them using the runtime master key.
 * After encryption, the data is stored as proper AES-GCM ciphertext.
 *
 * Detection: raw BCrypt hashes start with "$2a$" when decoded as UTF-8.
 * AES-GCM ciphertext will not decode to a valid BCrypt prefix.
 */
@Component
@org.springframework.core.annotation.Order(1) // Run before any other ApplicationRunner
public class SeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);
    private static final String BCRYPT_PREFIX = "$2a$";

    /**
     * Profiles in which the automatic bootstrap-password <em>generator</em> may run.
     * Operator-supplied {@code ADMIN_INITIAL_PASSWORD} is allowed in any profile —
     * that path is deliberate and never writes plaintext anywhere, so it is safe for
     * production (see README "Production / controlled deploy").
     */
    private static final Set<String> GENERATOR_ALLOWED_PROFILES = Set.of("dev", "local", "test");

    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public SeedDataInitializer(UserRepository userRepository, AesEncryptionService aesEncryptionService,
                                PasswordEncoder passwordEncoder, Environment environment) {
        this.userRepository = userRepository;
        this.aesEncryptionService = aesEncryptionService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<UserEntity> users = userRepository.findAll();
        int migrated = 0;

        for (UserEntity user : users) {
            byte[] stored = user.getPasswordHashEncrypted();
            if (stored == null) continue;

            // Check if stored value is a raw BCrypt hash (not yet AES-encrypted)
            String asString = new String(stored, StandardCharsets.UTF_8);
            if (asString.startsWith(BCRYPT_PREFIX)) {
                // This is a raw BCrypt hash — encrypt it with AES
                byte[] encrypted = aesEncryptionService.encrypt(asString);
                user.setPasswordHashEncrypted(encrypted);
                userRepository.save(user);
                migrated++;
                log.info("Migrated password for user '{}' from raw BCrypt to AES-encrypted", user.getUsername());
            }
            // If it doesn't start with $2a$, it's already AES-encrypted — skip
        }

        if (migrated > 0) {
            log.info("Password migration complete: {} users updated", migrated);
        }

        // Optional bootstrap: supply an explicit admin password or generate one.
        // Runs idempotently — only overrides the admin account, and only when the
        // operator opted in via env var.
        applyAdminBootstrapIfRequested();
    }

    private void applyAdminBootstrapIfRequested() {
        String explicit = System.getenv("ADMIN_INITIAL_PASSWORD");
        boolean generate = "true".equalsIgnoreCase(System.getenv("CAMPUSSTORE_GENERATE_BOOTSTRAP_PASSWORD"));
        boolean hasExplicit = explicit != null && !explicit.isBlank();
        if (!hasExplicit && !generate) return;

        // The generator writes plaintext to a file — only allow it in profiles where
        // that trade-off is acceptable. Operator-supplied ADMIN_INITIAL_PASSWORD is
        // a deliberate production bootstrap path and is permitted in any profile.
        if (generate && !hasExplicit) {
            boolean generatorAllowed = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(GENERATOR_ALLOWED_PROFILES::contains);
            if (!generatorAllowed) {
                log.warn("CAMPUSSTORE_GENERATE_BOOTSTRAP_PASSWORD set but active profile {} is not in {}; "
                                + "ignoring (use ADMIN_INITIAL_PASSWORD for production).",
                        Arrays.toString(environment.getActiveProfiles()), GENERATOR_ALLOWED_PROFILES);
                return;
            }
        }

        Optional<UserEntity> adminOpt = userRepository.findByUsername("admin");
        if (adminOpt.isEmpty()) return;
        UserEntity admin = adminOpt.get();

        String password;
        if (hasExplicit) {
            password = explicit;
            log.warn("Bootstrap admin password overridden from ADMIN_INITIAL_PASSWORD env var "
                    + "(rotation still required on first login).");
        } else {
            password = generateReviewablePassword();
            // Persistent logs get a REDACTED marker only. The plaintext goes to a separate
            // one-time credential file that an operator must read, chmod-protect, and delete.
            log.warn("Generated one-time admin bootstrap password: [REDACTED — see "
                    + "bootstrap-admin-password.txt in the working directory]. "
                    + "Rotate immediately at /account/change-password.");
            writeOneTimeCredentialFile(password);
        }

        String bcryptHash = passwordEncoder.encode(password);
        admin.setPasswordHashEncrypted(aesEncryptionService.encrypt(bcryptHash));
        admin.setPasswordChangeRequired(true);
        userRepository.save(admin);
        // Intentionally no stdout/log emission of the plaintext. The only copy lives in
        // the owner-read-only bootstrap-admin-password.txt file; operators read it,
        // rotate, and delete the file.
    }

    /**
     * Write the generated password to a one-time credential file co-located with the
     * app. Overwrites any existing file; operators are expected to read, rotate via
     * {@code /account/change-password}, and delete.
     */
    private void writeOneTimeCredentialFile(String password) {
        try {
            Path out = Path.of("bootstrap-admin-password.txt");
            Files.writeString(out,
                    "username=admin\npassword=" + password
                            + "\nrotate_at=/account/change-password\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.setPosixFilePermissions(out,
                        java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystem (Windows): permissions are best-effort.
            }
        } catch (Exception e) {
            log.warn("Could not write one-time credential file: {}", e.getMessage());
        }
    }

    /** 20 chars, url-safe alphanumerics. Long enough to be safe at-rest in the log. */
    private static String generateReviewablePassword() {
        SecureRandom rng = new SecureRandom();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}

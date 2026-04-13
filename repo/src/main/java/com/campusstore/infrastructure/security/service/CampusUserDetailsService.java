package com.campusstore.infrastructure.security.service;

import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class CampusUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CampusUserDetailsService.class);

    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;

    public CampusUserDetailsService(UserRepository userRepository,
                                     AesEncryptionService aesEncryptionService) {
        this.userRepository = userRepository;
        this.aesEncryptionService = aesEncryptionService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for username: {}", username);
                    return new UsernameNotFoundException("Invalid username or password");
                });

        if (!"ACTIVE".equals(user.getAccountStatus().name())) {
            log.warn("Login attempt for non-active account: {} (status: {})", username, user.getAccountStatus());
            throw new UsernameNotFoundException("Invalid username or password");
        }

        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(UserRoleEntity::getRole)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        // Decrypt AES-encrypted BCrypt hash for Spring Security password matching.
        // Compatibility: if the stored value is still a raw BCrypt hash (pre-migration),
        // detect it by checking the UTF-8 prefix and use it directly.
        byte[] storedHash = user.getPasswordHashEncrypted();
        String decryptedBcryptHash;
        String rawAttempt = new String(storedHash, java.nio.charset.StandardCharsets.UTF_8);
        if (rawAttempt.startsWith("$2a$") || rawAttempt.startsWith("$2b$") || rawAttempt.startsWith("$2y$")) {
            // Raw BCrypt hash not yet AES-encrypted (seed data before migration)
            decryptedBcryptHash = rawAttempt;
        } else {
            // AES-GCM encrypted BCrypt hash — decrypt it
            decryptedBcryptHash = aesEncryptionService.decrypt(storedHash);
        }

        boolean mustChange = Boolean.TRUE.equals(user.getPasswordChangeRequired());
        return new CampusUserPrincipal(
                user.getId(),
                user.getUsername(),
                decryptedBcryptHash,
                user.getDisplayName(),
                user.getDepartmentId(),
                user.getHomeZoneId(),
                mustChange,
                authorities
        );
    }
}

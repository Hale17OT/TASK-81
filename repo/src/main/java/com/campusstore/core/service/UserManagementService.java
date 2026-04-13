package com.campusstore.core.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@Transactional
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AesEncryptionService aesEncryptionService;
    private final AuditService auditService;

    public UserManagementService(UserRepository userRepository,
                                 UserRoleRepository userRoleRepository,
                                 DepartmentRepository departmentRepository,
                                 PasswordEncoder passwordEncoder,
                                 AesEncryptionService aesEncryptionService,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.aesEncryptionService = aesEncryptionService;
        this.auditService = auditService;
    }

    public UserEntity createUser(String username, String rawPassword, String displayName,
                                 String email, String phone, Role initialRole, Long createdByUserId) {
        return createUser(username, rawPassword, displayName, email, phone, initialRole, null, createdByUserId);
    }

    public UserEntity createUser(String username, String rawPassword, String displayName,
                                 String email, String phone, Role initialRole,
                                 Long departmentId, Long createdByUserId) {
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(rawPassword, "Password must not be null");

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists: " + username);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        // BCrypt encode, then AES encrypt the hash for at-rest protection
        String bcryptHash = passwordEncoder.encode(rawPassword);
        user.setPasswordHashEncrypted(aesEncryptionService.encrypt(bcryptHash));
        user.setDisplayName(displayName);
        user.setEmailEncrypted(aesEncryptionService.encrypt(email));
        user.setPhoneEncrypted(aesEncryptionService.encrypt(phone));
        user.setPhoneLast4(AesEncryptionService.extractLast4(phone));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPersonalizationEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        // Department FK is mapped via the `department` relationship. The scalar
        // `department_id` column is insertable=false/updatable=false, so writing
        // through `setDepartment(...)` is the only path that persists.
        if (departmentId != null) {
            DepartmentEntity dept = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
            user.setDepartment(dept);
        }

        UserEntity saved = userRepository.save(user);

        if (initialRole != null) {
            UserRoleEntity roleEntity = new UserRoleEntity();
            roleEntity.setUser(saved);
            roleEntity.setRole(initialRole);
            roleEntity.setGrantedAt(Instant.now());
            roleEntity.setGrantedBy(createdByUserId);
            userRoleRepository.save(roleEntity);
        }

        auditService.log(createdByUserId, "CREATE_USER", "User", saved.getId(),
                "{\"username\":\"" + username + "\"}");
        log.info("User created with id={}", saved.getId());
        return saved;
    }

    public UserEntity updateUser(Long userId, String displayName, String email, String phone,
                                  Long departmentId, Long updatedByUserId) {
        Objects.requireNonNull(userId, "User ID must not be null");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (displayName != null) {
            user.setDisplayName(displayName);
        }
        if (email != null) {
            user.setEmailEncrypted(aesEncryptionService.encrypt(email));
        }
        if (phone != null) {
            user.setPhoneEncrypted(aesEncryptionService.encrypt(phone));
            user.setPhoneLast4(AesEncryptionService.extractLast4(phone));
        }
        if (departmentId != null) {
            DepartmentEntity dept = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
            // Write through the @ManyToOne relationship — the scalar `department_id`
            // column is insertable/updatable=false and cannot persist on its own.
            user.setDepartment(dept);
        }
        user.setUpdatedAt(Instant.now());

        UserEntity saved = userRepository.save(user);

        auditService.log(updatedByUserId, "UPDATE_USER", "User", userId,
                "{\"updatedFields\":\"profile,department\"}");
        log.info("User updated id={}", userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public UserEntity getUserById(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional(readOnly = true)
    public Page<UserEntity> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public UserEntity changeAccountStatus(Long userId, AccountStatus newStatus, Long changedByUserId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(newStatus, "New status must not be null");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        AccountStatus currentStatus = user.getAccountStatus();
        validateStatusTransition(currentStatus, newStatus);

        user.setAccountStatus(newStatus);
        if (newStatus == AccountStatus.DISABLED || newStatus == AccountStatus.BLACKLISTED) {
            user.setDisabledAt(Instant.now());
        } else {
            user.setDisabledAt(null);
        }
        user.setUpdatedAt(Instant.now());

        UserEntity saved = userRepository.save(user);

        auditService.log(changedByUserId, "CHANGE_ACCOUNT_STATUS", "User", userId,
                "{\"from\":\"" + currentStatus + "\",\"to\":\"" + newStatus + "\"}");
        log.info("Account status changed for user id={} from {} to {}", userId, currentStatus, newStatus);
        return saved;
    }

    public UserRoleEntity assignRole(Long userId, Role role, Long grantedByUserId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(role, "Role must not be null");

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        if (userRoleRepository.existsByUserIdAndRole(userId, role)) {
            throw new ConflictException("User already has role: " + role);
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserRoleEntity roleEntity = new UserRoleEntity();
        roleEntity.setUser(user);
        roleEntity.setRole(role);
        roleEntity.setGrantedAt(Instant.now());
        roleEntity.setGrantedBy(grantedByUserId);

        UserRoleEntity saved = userRoleRepository.save(roleEntity);

        auditService.log(grantedByUserId, "ASSIGN_ROLE", "User", userId,
                "{\"role\":\"" + role + "\"}");
        log.info("Role {} assigned to user id={}", role, userId);
        return saved;
    }

    public void revokeRole(Long userId, Role role, Long revokedByUserId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(role, "Role must not be null");

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        if (!userRoleRepository.existsByUserIdAndRole(userId, role)) {
            throw new ResourceNotFoundException("User role " + role + " not found for user " + userId);
        }

        userRoleRepository.deleteByUserIdAndRole(userId, role);

        auditService.log(revokedByUserId, "REVOKE_ROLE", "User", userId,
                "{\"role\":\"" + role + "\"}");
        log.info("Role {} revoked from user id={}", role, userId);
    }

    @Transactional(readOnly = true)
    public UserEntity getUserProfile(Long targetUserId, Long requestingUserId) {
        Objects.requireNonNull(targetUserId, "Target user ID must not be null");

        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        if (targetUserId.equals(requestingUserId)) {
            // Own profile: decrypt PII for the caller
            // The caller will have access to the decrypted data via the entity
            // Decryption happens on demand when the caller reads PII fields
            log.debug("User id={} accessing own profile", requestingUserId);
        } else {
            // Viewing another user's profile: mask PII
            // Clear encrypted fields so caller cannot access them
            user.setEmailEncrypted(null);
            user.setPhoneEncrypted(null);
            log.debug("User id={} accessing profile of user id={} (masked)", requestingUserId, targetUserId);
        }

        return user;
    }

    private void validateStatusTransition(AccountStatus current, AccountStatus target) {
        if (current == target) {
            throw new ConflictException("Account is already in status: " + current);
        }
        // ACTIVE -> DISABLED or BLACKLISTED allowed
        // DISABLED -> ACTIVE or BLACKLISTED allowed
        // BLACKLISTED -> DISABLED allowed (rehabilitation path)
        boolean valid = switch (current) {
            case ACTIVE -> target == AccountStatus.DISABLED || target == AccountStatus.BLACKLISTED;
            case DISABLED -> target == AccountStatus.ACTIVE || target == AccountStatus.BLACKLISTED;
            case BLACKLISTED -> target == AccountStatus.DISABLED;
        };
        if (!valid) {
            throw new ConflictException("Invalid status transition from " + current + " to " + target);
        }
    }
}

package com.campusstore.unit.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserManagementService} covering duplicate-username detection,
 * credential encryption on create, and not-found handling on lookup.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AesEncryptionService aesEncryptionService;
    @Mock private AuditService auditService;

    @InjectMocks
    private UserManagementService userManagementService;

    // ── createUser ────────────────────────────────────────────────────

    @Test
    void createUser_duplicateUsername_throwsConflictException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                userManagementService.createUser(
                        "alice", "Pass123!", "Alice", "alice@test.com",
                        "5550001234", Role.STUDENT, 1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_validInput_encryptsCredentials() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("Pass123!")).thenReturn("$2a$bcrypt");
        when(aesEncryptionService.encrypt("$2a$bcrypt")).thenReturn("enc_hash".getBytes());
        when(aesEncryptionService.encrypt("bob@test.com")).thenReturn("enc_email".getBytes());
        when(aesEncryptionService.encrypt("5550001234")).thenReturn("enc_phone".getBytes());
        UserEntity saved = new UserEntity();
        saved.setId(10L);
        saved.setUsername("bob");
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        UserEntity result = userManagementService.createUser(
                "bob", "Pass123!", "Bob", "bob@test.com",
                "5550001234", Role.STUDENT, 1L);

        assertEquals("bob", result.getUsername());
        verify(passwordEncoder).encode("Pass123!");
        verify(aesEncryptionService).encrypt("$2a$bcrypt");
        verify(aesEncryptionService).encrypt("bob@test.com");
        verify(auditService).log(eq(1L), eq("CREATE_USER"), eq("User"), eq(10L), anyString());
    }

    @Test
    void createUser_withRole_assignsRoleRecord() {
        when(userRepository.existsByUsername("charlie")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(aesEncryptionService.encrypt(anyString())).thenReturn("enc".getBytes());
        UserEntity saved = new UserEntity();
        saved.setId(20L);
        saved.setUsername("charlie");
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        userManagementService.createUser(
                "charlie", "Pass123!", "Charlie", "c@test.com",
                "5550001111", Role.TEACHER, 1L);

        verify(userRoleRepository).save(any());
    }

    @Test
    void createUser_nullUsername_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                userManagementService.createUser(
                        null, "Pass123!", "Name", "email@test.com",
                        "5550001234", Role.STUDENT, 1L));
    }

    // ── getUserById ───────────────────────────────────────────────────

    @Test
    void getUserById_notFound_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userManagementService.getUserById(99L));
    }

    @Test
    void getUserById_found_returnsUser() {
        UserEntity user = new UserEntity();
        user.setId(5L);
        user.setUsername("alice");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserEntity result = userManagementService.getUserById(5L);

        assertEquals(5L, result.getId());
        assertEquals("alice", result.getUsername());
    }
}

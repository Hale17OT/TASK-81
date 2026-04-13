package com.campusstore.unit.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.ProfileService;
import com.campusstore.infrastructure.persistence.entity.UserAddressEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.UserAddressRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserTagRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests object-level authorization for address operations in ProfileService:
 * - Non-owner cannot delete someone else's address
 * - Non-owner cannot update someone else's address
 * - Owner can manage their own address
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProfileServiceAuthorizationTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAddressRepository addressRepository;
    @Mock private UserTagRepository tagRepository;
    @Mock private UserPreferenceRepository preferenceRepository;
    @Mock private AesEncryptionService encryptionService;
    @Mock private AuditService auditService;

    @InjectMocks
    private ProfileService profileService;

    private UserAddressEntity address;

    @BeforeEach
    void setUp() {
        address = new UserAddressEntity();
        address.setId(10L);
        address.setUserId(1L);
        address.setLabel("Home");
    }

    @Test
    void deleteAddress_nonOwner_throwsAccessDenied() {
        Long nonOwnerId = 99L;

        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        assertThrows(AccessDeniedException.class,
                () -> profileService.deleteAddress(nonOwnerId, 10L));

        verify(addressRepository, never()).delete(any());
    }

    @Test
    void deleteAddress_owner_succeeds() {
        Long ownerId = 1L;

        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        profileService.deleteAddress(ownerId, 10L);

        verify(addressRepository).delete(address);
    }

    @Test
    void updateAddress_nonOwner_throwsAccessDenied() {
        Long nonOwnerId = 99L;

        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        assertThrows(AccessDeniedException.class,
                () -> profileService.updateAddress(nonOwnerId, 10L, "Work", null, null, null, null));
    }

    @Test
    void updateAddress_owner_succeeds() {
        Long ownerId = 1L;

        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAddressEntity result = profileService.updateAddress(ownerId, 10L, "Work", null, null, null, null);

        assertEquals("Work", result.getLabel());
    }
}

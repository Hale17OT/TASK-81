package com.campusstore.core.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.infrastructure.persistence.entity.UserAddressEntity;
import com.campusstore.infrastructure.persistence.entity.UserContactEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.persistence.entity.UserTagEntity;
import com.campusstore.infrastructure.persistence.repository.UserAddressRepository;
import com.campusstore.infrastructure.persistence.repository.UserContactRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserTagRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final UserContactRepository contactRepository;
    private final UserTagRepository tagRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final AesEncryptionService encryptionService;
    private final AuditService auditService;

    public ProfileService(UserRepository userRepository, UserAddressRepository addressRepository,
                          UserContactRepository contactRepository,
                          UserTagRepository tagRepository, UserPreferenceRepository preferenceRepository,
                          AesEncryptionService encryptionService, AuditService auditService) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.contactRepository = contactRepository;
        this.tagRepository = tagRepository;
        this.preferenceRepository = preferenceRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserEntity getProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional
    public UserEntity updateProfile(Long userId, String displayName, String email, String phone) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (displayName != null) user.setDisplayName(displayName);
        if (email != null) user.setEmailEncrypted(encryptionService.encrypt(email));
        if (phone != null) {
            user.setPhoneEncrypted(encryptionService.encrypt(phone));
            user.setPhoneLast4(AesEncryptionService.extractLast4(phone));
        }

        UserEntity saved = userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        auditService.log(userId, "PROFILE.UPDATE", "User", userId, "{}");
        return saved;
    }

    // Address management
    @Transactional(readOnly = true)
    public List<UserAddressEntity> getAddresses(Long userId) {
        return addressRepository.findByUserId(userId);
    }

    @Transactional
    public UserAddressEntity addAddress(Long userId, String label, String street, String city,
                                         String state, String zipCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserAddressEntity addr = new UserAddressEntity();
        addr.setUser(user);
        addr.setLabel(label);
        addr.setStreetEncrypted(encryptionService.encrypt(street));
        addr.setCityEncrypted(encryptionService.encrypt(city));
        addr.setStateEncrypted(encryptionService.encrypt(state));
        addr.setZipCodeEncrypted(encryptionService.encrypt(zipCode));
        addr.setIsPrimary(false);
        addr.setCreatedAt(Instant.now());

        UserAddressEntity saved = addressRepository.save(addr);
        log.info("Address added for user {}", userId);
        return saved;
    }

    @Transactional
    public UserAddressEntity updateAddress(Long userId, Long addressId, String label, String street,
                                            String city, String state, String zipCode) {
        UserAddressEntity addr = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (!addr.getUserId().equals(userId)) {
            throw new AccessDeniedException("Cannot update another user's address");
        }

        if (label != null) addr.setLabel(label);
        if (street != null) addr.setStreetEncrypted(encryptionService.encrypt(street));
        if (city != null) addr.setCityEncrypted(encryptionService.encrypt(city));
        if (state != null) addr.setStateEncrypted(encryptionService.encrypt(state));
        if (zipCode != null) addr.setZipCodeEncrypted(encryptionService.encrypt(zipCode));

        return addressRepository.save(addr);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddressEntity addr = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));

        if (!addr.getUserId().equals(userId)) {
            throw new AccessDeniedException("Cannot delete another user's address");
        }

        addressRepository.delete(addr);
        log.info("Address {} deleted for user {}", addressId, userId);
    }

    // Contacts management — encrypted at rest; every write is ownership-checked so a
    // user can never touch another user's contacts (same pattern as addresses).
    @Transactional(readOnly = true)
    public List<UserContactEntity> getContacts(Long userId) {
        return contactRepository.findByUserId(userId);
    }

    @Transactional
    public UserContactEntity addContact(Long userId, String label, String relationship,
                                         String name, String email, String phone, String notes) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserContactEntity c = new UserContactEntity();
        c.setUser(user);
        c.setLabel(label);
        c.setRelationship(relationship);
        if (name != null) c.setNameEncrypted(encryptionService.encrypt(name));
        if (email != null) c.setEmailEncrypted(encryptionService.encrypt(email));
        if (phone != null) {
            c.setPhoneEncrypted(encryptionService.encrypt(phone));
            c.setPhoneLast4(AesEncryptionService.extractLast4(phone));
        }
        if (notes != null) c.setNotesEncrypted(encryptionService.encrypt(notes));
        c.setIsPrimary(false);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());

        UserContactEntity saved = contactRepository.save(c);
        log.info("Contact added for user {}", userId);
        auditService.log(userId, "CONTACT.CREATE", "UserContact", saved.getId(), "{}");
        return saved;
    }

    @Transactional
    public UserContactEntity updateContact(Long userId, Long contactId, String label,
                                            String relationship, String name, String email,
                                            String phone, String notes) {
        UserContactEntity c = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("UserContact", contactId));
        Long ownerId = c.getUser() != null ? c.getUser().getId() : c.getUserId();
        if (!userId.equals(ownerId)) {
            throw new AccessDeniedException("Cannot update another user's contact");
        }
        if (label != null) c.setLabel(label);
        if (relationship != null) c.setRelationship(relationship);
        if (name != null) c.setNameEncrypted(encryptionService.encrypt(name));
        if (email != null) c.setEmailEncrypted(encryptionService.encrypt(email));
        if (phone != null) {
            c.setPhoneEncrypted(encryptionService.encrypt(phone));
            c.setPhoneLast4(AesEncryptionService.extractLast4(phone));
        }
        if (notes != null) c.setNotesEncrypted(encryptionService.encrypt(notes));
        c.setUpdatedAt(Instant.now());
        UserContactEntity saved = contactRepository.save(c);
        auditService.log(userId, "CONTACT.UPDATE", "UserContact", contactId, "{}");
        return saved;
    }

    @Transactional
    public void deleteContact(Long userId, Long contactId) {
        UserContactEntity c = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("UserContact", contactId));
        Long ownerId = c.getUser() != null ? c.getUser().getId() : c.getUserId();
        if (!userId.equals(ownerId)) {
            throw new AccessDeniedException("Cannot delete another user's contact");
        }
        contactRepository.delete(c);
        auditService.log(userId, "CONTACT.DELETE", "UserContact", contactId, "{}");
    }

    // Tag management
    @Transactional(readOnly = true)
    public List<UserTagEntity> getTags(Long userId) {
        return tagRepository.findByUserId(userId);
    }

    @Transactional
    public UserTagEntity addTag(Long userId, String tag) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserTagEntity tagEntity = new UserTagEntity();
        tagEntity.setUser(user);
        tagEntity.setTag(tag);
        tagEntity.setCreatedAt(Instant.now());
        return tagRepository.save(tagEntity);
    }

    @Transactional
    public void removeTag(Long userId, String tag) {
        tagRepository.deleteByUserIdAndTag(userId, tag);
    }

    // Preference management
    @Transactional(readOnly = true)
    public UserPreferenceEntity getPreferences(Long userId) {
        Optional<UserPreferenceEntity> pref = preferenceRepository.findByUserId(userId);
        return pref.orElseGet(() -> {
            UserPreferenceEntity newPref = new UserPreferenceEntity();
            newPref.setUserId(userId);
            newPref.setPersonalizationEnabled(true);
            return newPref;
        });
    }

    private UserPreferenceEntity findOrCreatePreference(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                    UserPreferenceEntity newPref = new UserPreferenceEntity();
                    newPref.setUser(user);
                    newPref.setPersonalizationEnabled(true);
                    return newPref;
                });
    }

    @Transactional
    public UserPreferenceEntity updateDnd(Long userId, LocalTime startTime, LocalTime endTime) {
        UserPreferenceEntity pref = findOrCreatePreference(userId);

        pref.setDndStartTime(startTime);
        pref.setDndEndTime(endTime);
        UserPreferenceEntity saved = preferenceRepository.save(pref);
        log.info("DND updated for user {}: {} - {}", userId, startTime, endTime);
        return saved;
    }

    @Transactional
    public UserPreferenceEntity togglePersonalization(Long userId, boolean enabled) {
        UserPreferenceEntity pref = findOrCreatePreference(userId);

        pref.setPersonalizationEnabled(enabled);
        UserPreferenceEntity saved = preferenceRepository.save(pref);
        log.info("Personalization toggled for user {}: {}", userId, enabled);
        return saved;
    }
}

package com.campusstore.api.controller;

import com.campusstore.api.dto.AddAddressRequest;
import com.campusstore.api.dto.AddContactRequest;
import com.campusstore.api.dto.AddTagRequest;
import com.campusstore.api.dto.AddressResponse;
import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.ContactResponse;
import com.campusstore.api.dto.ProfileResponse;
import com.campusstore.api.dto.UpdateProfileRequest;
import com.campusstore.core.service.ProfileService;
import com.campusstore.infrastructure.persistence.entity.UserAddressEntity;
import com.campusstore.infrastructure.persistence.entity.UserContactEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserTagEntity;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final AesEncryptionService aesEncryptionService;

    public ProfileController(ProfileService profileService,
                              AesEncryptionService aesEncryptionService) {
        this.profileService = profileService;
        this.aesEncryptionService = aesEncryptionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        UserEntity u = profileService.getProfile(principal.getUserId());
        ProfileResponse r = new ProfileResponse();
        r.setId(u.getId());
        r.setUsername(u.getUsername());
        r.setDisplayName(u.getDisplayName());
        r.setEmail(u.getEmailEncrypted() != null
                ? aesEncryptionService.decrypt(u.getEmailEncrypted()) : null);
        String phone = u.getPhoneEncrypted() != null
                ? aesEncryptionService.decrypt(u.getPhoneEncrypted()) : null;
        r.setPhone(phone);
        r.setPhoneLast4(phone != null && phone.length() >= 4
                ? phone.substring(phone.length() - 4) : null);
        r.setHomeZoneId(u.getHomeZoneId());
        r.setDepartmentId(u.getDepartmentId());
        r.setPasswordChangeRequired(u.getPasswordChangeRequired());
        return ResponseEntity.ok(ApiResponse.success(r));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        profileService.updateProfile(
                principal.getUserId(),
                request.getDisplayName(),
                request.getEmail(),
                request.getPhone()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listAddresses(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        List<UserAddressEntity> addresses = profileService.getAddresses(principal.getUserId());
        List<AddressResponse> out = addresses.stream().map(a -> {
            AddressResponse r = new AddressResponse();
            r.setId(a.getId());
            r.setLabel(a.getLabel());
            r.setStreet(a.getStreetEncrypted() != null
                    ? aesEncryptionService.decrypt(a.getStreetEncrypted()) : null);
            r.setCity(a.getCityEncrypted() != null
                    ? aesEncryptionService.decrypt(a.getCityEncrypted()) : null);
            r.setState(a.getStateEncrypted() != null
                    ? aesEncryptionService.decrypt(a.getStateEncrypted()) : null);
            r.setZipCode(a.getZipCodeEncrypted() != null
                    ? aesEncryptionService.decrypt(a.getZipCodeEncrypted()) : null);
            r.setIsPrimary(a.getIsPrimary());
            return r;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Map<String, Long>>> addAddress(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody AddAddressRequest request) {
        UserAddressEntity created = profileService.addAddress(
                principal.getUserId(),
                request.getLabel(),
                request.getStreet(),
                request.getCity(),
                request.getState(),
                request.getZipCode()
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<Void>> updateAddress(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddAddressRequest request) {
        profileService.updateAddress(
                principal.getUserId(),
                id,
                request.getLabel(),
                request.getStreet(),
                request.getCity(),
                request.getState(),
                request.getZipCode()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        profileService.deleteAddress(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<String>>> listTags(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        List<UserTagEntity> tags = profileService.getTags(principal.getUserId());
        List<String> tagStrings = tags.stream()
                .map(UserTagEntity::getTag)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(tagStrings));
    }

    @PostMapping("/tags")
    public ResponseEntity<ApiResponse<Void>> addTag(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody AddTagRequest request) {
        profileService.addTag(principal.getUserId(), request.getTag());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/tags/{tag}")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable String tag) {
        profileService.removeTag(principal.getUserId(), tag);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // ── Contacts ─────────────────────────────────────────────────────
    // All writes flow through ProfileService.* which enforces owner-only access.

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> listContacts(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        List<UserContactEntity> contacts = profileService.getContacts(principal.getUserId());
        List<ContactResponse> out = contacts.stream().map(c -> {
            ContactResponse r = new ContactResponse();
            r.setId(c.getId());
            r.setLabel(c.getLabel());
            r.setRelationship(c.getRelationship());
            r.setName(c.getNameEncrypted() != null
                    ? aesEncryptionService.decrypt(c.getNameEncrypted()) : null);
            r.setEmail(c.getEmailEncrypted() != null
                    ? aesEncryptionService.decrypt(c.getEmailEncrypted()) : null);
            r.setPhone(c.getPhoneEncrypted() != null
                    ? aesEncryptionService.decrypt(c.getPhoneEncrypted()) : null);
            r.setPhoneLast4(c.getPhoneLast4());
            r.setNotes(c.getNotesEncrypted() != null
                    ? aesEncryptionService.decrypt(c.getNotesEncrypted()) : null);
            r.setIsPrimary(c.getIsPrimary());
            return r;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @PostMapping("/contacts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> addContact(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody AddContactRequest request) {
        UserContactEntity created = profileService.addContact(
                principal.getUserId(),
                request.getLabel(),
                request.getRelationship(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getNotes());
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PutMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<Void>> updateContact(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddContactRequest request) {
        profileService.updateContact(
                principal.getUserId(),
                id,
                request.getLabel(),
                request.getRelationship(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getNotes());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        profileService.deleteContact(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}

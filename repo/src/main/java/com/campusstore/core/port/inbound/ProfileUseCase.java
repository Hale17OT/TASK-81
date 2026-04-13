package com.campusstore.core.port.inbound;

import org.springframework.data.domain.Page;

import java.time.LocalTime;
import java.util.List;

/**
 * Inbound port for user-profile and preference operations.
 */
public interface ProfileUseCase {

    /**
     * Retrieve a user's profile.
     *
     * @param userId the user id
     * @return the profile view
     */
    ProfileView getProfile(Long userId);

    /**
     * Update a user's profile fields.
     *
     * @param userId  the user id
     * @param command the fields to update
     */
    void updateProfile(Long userId, UpdateProfileCommand command);

    // ── Addresses ──────────────────────────────────────────────────────

    /**
     * List all addresses for a user.
     *
     * @param userId the user id
     * @return list of addresses
     */
    List<AddressView> getAddresses(Long userId);

    /**
     * Add a new address for a user.
     *
     * @param userId  the user id
     * @param command the address details
     * @return the id of the new address
     */
    Long addAddress(Long userId, AddAddressCommand command);

    /**
     * Update an existing address.
     *
     * @param userId    the user id
     * @param addressId the address id
     * @param command   the updated address details
     */
    void updateAddress(Long userId, Long addressId, AddAddressCommand command);

    /**
     * Delete an address.
     *
     * @param userId    the user id
     * @param addressId the address id
     */
    void deleteAddress(Long userId, Long addressId);

    // ── Tags ───────────────────────────────────────────────────────────

    /**
     * Get all tags for a user.
     *
     * @param userId the user id
     * @return list of tag strings
     */
    List<String> getTags(Long userId);

    /**
     * Add a tag to a user.
     *
     * @param userId the user id
     * @param tag    the tag value
     */
    void addTag(Long userId, String tag);

    /**
     * Remove a tag from a user.
     *
     * @param userId the user id
     * @param tag    the tag value
     */
    void removeTag(Long userId, String tag);

    // ── Preferences ────────────────────────────────────────────────────

    /**
     * Get a user's preferences.
     *
     * @param userId the user id
     * @return the preferences view
     */
    PreferencesView getPreferences(Long userId);

    /**
     * Update do-not-disturb window.
     *
     * @param userId the user id
     * @param start  DND start time
     * @param end    DND end time
     */
    void updateDnd(Long userId, LocalTime start, LocalTime end);

    /**
     * Toggle search personalization for a user.
     *
     * @param userId  the user id
     * @param enabled whether personalization is enabled
     */
    void togglePersonalization(Long userId, boolean enabled);

    // ── Command types ──────────────────────────────────────────────────

    record UpdateProfileCommand(
            String displayName,
            String email,
            String phone,
            Long homeZoneId,
            Long departmentId
    ) {
    }

    record AddAddressCommand(
            String label,
            String street,
            String city,
            String state,
            String zipCode,
            boolean primary
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record ProfileView(
            Long userId,
            String username,
            String displayName,
            String email,
            String phone,
            Long homeZoneId,
            Long departmentId,
            boolean personalizationEnabled
    ) {
    }

    record AddressView(
            Long id,
            String label,
            String street,
            String city,
            String state,
            String zipCode,
            boolean primary
    ) {
    }

    record PreferencesView(
            LocalTime dndStart,
            LocalTime dndEnd,
            boolean personalizationEnabled
    ) {
    }
}

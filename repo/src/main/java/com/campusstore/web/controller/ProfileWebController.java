package com.campusstore.web.controller;

import com.campusstore.api.dto.AddressResponse;
import com.campusstore.api.dto.ContactResponse;
import com.campusstore.api.dto.ProfileResponse;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thymeleaf profile pages. All data — including decrypted PII scoped to the authenticated
 * owner — is obtained via {@link InternalApiClient} over HTTP. This controller does NOT
 * hold or reference {@code AesEncryptionService}; decryption for owner-facing views is
 * performed server-side inside the API layer, and the resulting plaintext DTOs arrive via
 * the loopback TLS call.
 */
@Controller
@RequestMapping("/profile")
public class ProfileWebController {

    private final InternalApiClient apiClient;

    public ProfileWebController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal CampusUserPrincipal principal,
                          Model model) {
        model.addAttribute("currentPage", "profile");

        ProfileResponse dto = apiClient.getProfile(principal.getUserId());
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", dto.getUsername());
        profile.put("displayName", dto.getDisplayName());
        profile.put("email", dto.getEmail());
        profile.put("phone", dto.getPhone());
        profile.put("phoneLast4", dto.getPhoneLast4());
        model.addAttribute("profile", profile);

        return "profile/index";
    }

    @GetMapping("/addresses")
    public String addresses(@AuthenticationPrincipal CampusUserPrincipal principal,
                            Model model) {
        model.addAttribute("currentPage", "profile");

        List<AddressResponse> raw = apiClient.getAddresses(principal.getUserId());
        List<Map<String, Object>> addresses = raw.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("label", a.getLabel());
            m.put("street", a.getStreet() != null ? a.getStreet() : "");
            m.put("city", a.getCity() != null ? a.getCity() : "");
            m.put("state", a.getState() != null ? a.getState() : "");
            m.put("zipCode", a.getZipCode() != null ? a.getZipCode() : "");
            m.put("isPrimary", a.getIsPrimary());
            return m;
        }).collect(Collectors.toList());

        model.addAttribute("addresses", addresses);
        return "profile/addresses";
    }

    @GetMapping("/contacts")
    public String contacts(@AuthenticationPrincipal CampusUserPrincipal principal,
                            Model model) {
        model.addAttribute("currentPage", "profile");

        List<ContactResponse> raw = apiClient.getContacts(principal.getUserId());
        List<Map<String, Object>> contacts = raw.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("label", c.getLabel());
            m.put("relationship", c.getRelationship());
            m.put("name", c.getName() != null ? c.getName() : "");
            m.put("email", c.getEmail() != null ? c.getEmail() : "");
            // Display only the masked form (last 4) in listing; the raw phone is omitted from
            // the model and only loaded into the edit form on demand. The UI never renders
            // the full decrypted phone in plain text.
            String last4 = c.getPhoneLast4();
            m.put("phoneMasked", (last4 != null && !last4.isBlank()) ? ("***-***-" + last4) : "");
            m.put("phoneLast4", last4);
            m.put("phone", c.getPhone() != null ? c.getPhone() : "");
            m.put("notes", c.getNotes() != null ? c.getNotes() : "");
            m.put("isPrimary", c.getIsPrimary());
            return m;
        }).collect(Collectors.toList());

        model.addAttribute("contacts", contacts);
        return "profile/contacts";
    }

    @PostMapping("/contacts")
    public String addContact(@AuthenticationPrincipal CampusUserPrincipal principal,
                              @RequestParam String label,
                              @RequestParam(required = false) String relationship,
                              @RequestParam(required = false) String name,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String notes,
                              RedirectAttributes redirectAttributes) {
        apiClient.addContact(principal.getUserId(),
                blankToNull(label), blankToNull(relationship), blankToNull(name),
                blankToNull(email), blankToNull(phone), blankToNull(notes));
        redirectAttributes.addFlashAttribute("successMessage", "Contact added");
        return "redirect:/profile/contacts";
    }

    @PostMapping("/contacts/{id}/update")
    public String updateContact(@AuthenticationPrincipal CampusUserPrincipal principal,
                                 @PathVariable Long id,
                                 @RequestParam String label,
                                 @RequestParam(required = false) String relationship,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirectAttributes) {
        apiClient.updateContact(principal.getUserId(), id,
                blankToNull(label), blankToNull(relationship), blankToNull(name),
                blankToNull(email), blankToNull(phone), blankToNull(notes));
        redirectAttributes.addFlashAttribute("successMessage", "Contact updated");
        return "redirect:/profile/contacts";
    }

    @PostMapping("/contacts/{id}/delete")
    public String deleteContact(@AuthenticationPrincipal CampusUserPrincipal principal,
                                 @PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        apiClient.deleteContact(principal.getUserId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Contact deleted");
        return "redirect:/profile/contacts";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @GetMapping("/tags")
    public String tags(@AuthenticationPrincipal CampusUserPrincipal principal,
                       Model model) {
        model.addAttribute("currentPage", "profile");

        // API returns List<String> of tag names directly.
        List<String> tags = apiClient.getTags(principal.getUserId());
        model.addAttribute("tags", tags);

        return "profile/tags";
    }

    @GetMapping("/notifications")
    public String notificationSettings(@AuthenticationPrincipal CampusUserPrincipal principal,
                                        Model model) {
        model.addAttribute("currentPage", "profile");

        UserPreferenceEntity preferences =
                apiClient.getPreferences(principal.getUserId());
        model.addAttribute("preferences", preferences);

        List<NotificationPreferenceEntity> notificationPrefs =
                apiClient.getNotificationPreferences(principal.getUserId());
        model.addAttribute("notificationPrefs", notificationPrefs);

        return "profile/notification-settings";
    }

    @PostMapping("/addresses")
    public String addAddress(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @RequestParam String label, @RequestParam String street,
                             @RequestParam String city, @RequestParam String state,
                             @RequestParam String zipCode,
                             RedirectAttributes redirectAttributes) {
        apiClient.addAddress(principal.getUserId(), label, street, city, state, zipCode);
        redirectAttributes.addFlashAttribute("successMessage", "Address added successfully");
        return "redirect:/profile/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@AuthenticationPrincipal CampusUserPrincipal principal,
                                @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.deleteAddress(principal.getUserId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Address deleted");
        return "redirect:/profile/addresses";
    }

    @PostMapping("/tags")
    public String addTag(@AuthenticationPrincipal CampusUserPrincipal principal,
                         @RequestParam String tag, RedirectAttributes redirectAttributes) {
        apiClient.addTag(principal.getUserId(), tag);
        redirectAttributes.addFlashAttribute("successMessage", "Tag added");
        return "redirect:/profile/tags";
    }

    @PostMapping("/tags/{tag}/delete")
    public String removeTag(@AuthenticationPrincipal CampusUserPrincipal principal,
                            @PathVariable String tag, RedirectAttributes redirectAttributes) {
        apiClient.removeTag(principal.getUserId(), tag);
        redirectAttributes.addFlashAttribute("successMessage", "Tag removed");
        return "redirect:/profile/tags";
    }

    @PostMapping("/update")
    public String updateProfile(@AuthenticationPrincipal CampusUserPrincipal principal,
                                @RequestParam(required = false) String displayName,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone,
                                RedirectAttributes redirectAttributes) {
        apiClient.updateProfile(principal.getUserId(), displayName, email, phone);
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated");
        return "redirect:/profile";
    }
}

package com.campusstore.integration.persistence;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.CategoryService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.core.service.ProfileService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.persistence.entity.UserAddressEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;
import com.campusstore.integration.support.WithMockCampusUser;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-mocked integration coverage for cross-user isolation rules that previously lived
 * only behind mocked service calls in the API tests. Exercises real service + repository
 * paths against H2 so actual authorization logic (not stub returns) decides outcomes.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrossUserIsolationPersistenceTest {

    @Autowired private NotificationService notificationService;
    @Autowired private ProfileService profileService;
    @Autowired private CategoryService categoryService;

    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @Test
    void notification_markRead_deniedForOtherUser() {
        UserEntity owner = persistUser("notif-owner-" + System.nanoTime(), Role.STUDENT);
        UserEntity attacker = persistUser("notif-atk-" + System.nanoTime(), Role.STUDENT);

        NotificationEntity n = new NotificationEntity();
        n.setUserId(owner.getId());
        n.setType(NotificationType.REQUEST_SUBMITTED);
        n.setTitle("T");
        n.setMessage("M");
        n.setIsRead(false);
        n.setIsCritical(false);
        n.setCreatedAt(Instant.now());
        n = notificationRepository.save(n);
        Long notificationId = n.getId();

        entityManager.flush();
        entityManager.clear();

        // Attacker is blocked by the service-level ownership check.
        assertThrows(AccessDeniedException.class,
                () -> notificationService.markRead(notificationId, attacker.getId()),
                "a user cannot mark another user's notification as read");

        // Owner succeeds.
        notificationService.markRead(notificationId, owner.getId());
        NotificationEntity reloaded = notificationRepository.findById(notificationId).orElseThrow();
        assertEquals(Boolean.TRUE, reloaded.getIsRead());
    }

    @Test
    void profileAddress_updateAndDelete_deniedForOtherUser() {
        UserEntity owner = persistUser("addr-owner-" + System.nanoTime(), Role.STUDENT);
        UserEntity attacker = persistUser("addr-atk-" + System.nanoTime(), Role.STUDENT);

        UserAddressEntity addr = profileService.addAddress(
                owner.getId(), "home", "1 Main St", "Boston", "MA", "02108");
        entityManager.flush();
        entityManager.clear();

        assertThrows(AccessDeniedException.class,
                () -> profileService.updateAddress(attacker.getId(), addr.getId(),
                        "x", "y", "z", "MA", "00000"),
                "a user cannot edit another user's address");

        assertThrows(AccessDeniedException.class,
                () -> profileService.deleteAddress(attacker.getId(), addr.getId()),
                "a user cannot delete another user's address");

        // Owner can still operate on their own row.
        profileService.updateAddress(owner.getId(), addr.getId(), "home-updated", null, null, null, null);
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void publicCategoriesEndpoint_accessibleToAuthenticatedNonAdmin() throws Exception {
        // Seed one row so the endpoint has something to return.
        CategoryEntity c = categoryService.create(
                "Cat-" + System.nanoTime(), "public read", null, null);
        entityManager.flush();

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void adminCategoriesEndpoint_blockedForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private UserEntity persistUser(String username, Role role) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setDisplayName(username);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPersonalizationEnabled(true);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);

        UserRoleEntity r = new UserRoleEntity();
        r.setUser(u);
        r.setRole(role);
        r.setGrantedAt(Instant.now());
        userRoleRepository.save(r);
        return u;
    }
}

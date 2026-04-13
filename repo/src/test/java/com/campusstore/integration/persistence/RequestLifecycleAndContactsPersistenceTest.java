package com.campusstore.integration.persistence;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.ProfileService;
import com.campusstore.core.service.RequestService;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.UserContactEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-mocked integration coverage for:
 * <ul>
 *   <li>Full request lifecycle: create → approve → start picking → ready-for-pickup →
 *       picked-up, with assertions on every state transition + pick task status sync.</li>
 *   <li>Contacts feature (V4): encryption-at-rest and cross-user isolation against
 *       the real persistence layer.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequestLifecycleAndContactsPersistenceTest {

    @Autowired private RequestService requestService;
    @Autowired private ProfileService profileService;
    @Autowired private AesEncryptionService aesEncryptionService;

    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private PickTaskRepository pickTaskRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void requestLifecycle_traversesEveryStateAndSyncsPickTask() {
        Ctx ctx = seed("life-" + System.nanoTime());

        // 1) Pending-approval with an assigned approver (the only teacher).
        ItemRequestEntity created = requestService.createRequest(
                ctx.student.getId(), ctx.item.getId(), 1, "coursework");
        assertEquals(RequestStatus.PENDING_APPROVAL, created.getStatus());
        assertNotNull(created.getApprover(), "assignment model must pick an approver");
        assertEquals(ctx.teacher.getId(), created.getApprover().getId());

        entityManager.flush();
        entityManager.clear();

        // 2) Approval by the assigned teacher → APPROVED + pick task PENDING.
        ItemRequestEntity approved = requestService.approveRequest(created.getId(), ctx.teacher.getId());
        assertEquals(RequestStatus.APPROVED, approved.getStatus());
        entityManager.flush();
        List<PickTaskEntity> tasks = pickTaskRepository.findByRequestId(created.getId());
        assertEquals(1, tasks.size());
        assertEquals(PickStatus.PENDING, tasks.get(0).getStatus());

        entityManager.clear();

        // 3) Staff starts picking → PICKING + pick task IN_PROGRESS.
        ItemRequestEntity picking = requestService.startPicking(created.getId(), ctx.admin.getId());
        assertEquals(RequestStatus.PICKING, picking.getStatus());
        entityManager.flush();
        tasks = pickTaskRepository.findByRequestId(created.getId());
        assertEquals(PickStatus.IN_PROGRESS, tasks.get(0).getStatus());
        assertNotNull(tasks.get(0).getStartedAt(), "startedAt must be recorded");

        entityManager.clear();

        // 4) Staff marks ready for pickup → READY_FOR_PICKUP + pick task COMPLETED.
        ItemRequestEntity ready = requestService.markReadyForPickup(created.getId(), ctx.admin.getId());
        assertEquals(RequestStatus.READY_FOR_PICKUP, ready.getStatus());
        entityManager.flush();
        tasks = pickTaskRepository.findByRequestId(created.getId());
        assertEquals(PickStatus.COMPLETED, tasks.get(0).getStatus());
        assertNotNull(tasks.get(0).getCompletedAt(), "completedAt must be recorded");

        entityManager.clear();

        // 5) Picked up → PICKED_UP (terminal).
        ItemRequestEntity pickedUp = requestService.markPickedUp(created.getId(), ctx.admin.getId());
        assertEquals(RequestStatus.PICKED_UP, pickedUp.getStatus());
        assertNotNull(pickedUp.getPickedUpAt());

        // Terminal transitions can no longer advance — e.g. cannot re-start picking.
        assertThrows(BusinessException.class,
                () -> requestService.startPicking(created.getId(), ctx.admin.getId()),
                "cannot restart picking after PICKED_UP");
    }

    @Test
    void contacts_roundTripAndCrossUserIsolation() {
        UserEntity owner = persistUser("contact-owner-" + System.nanoTime(), Role.STUDENT);
        UserEntity other = persistUser("contact-other-" + System.nanoTime(), Role.STUDENT);

        UserContactEntity saved = profileService.addContact(
                owner.getId(),
                "Emergency",
                "parent",
                "Jane Doe",
                "jane@example.edu",
                "+1-555-867-5309",
                "primary call during hours");
        entityManager.flush();
        entityManager.clear();

        // Contact persists with every PII field encrypted-at-rest (ciphertext, not plaintext).
        UserContactEntity reloaded = profileService.getContacts(owner.getId()).stream()
                .filter(c -> c.getId().equals(saved.getId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(reloaded.getNameEncrypted());
        assertEquals("Jane Doe", aesEncryptionService.decrypt(reloaded.getNameEncrypted()));
        assertEquals("jane@example.edu", aesEncryptionService.decrypt(reloaded.getEmailEncrypted()));
        assertEquals("+1-555-867-5309", aesEncryptionService.decrypt(reloaded.getPhoneEncrypted()));
        assertEquals("5309", reloaded.getPhoneLast4());
        assertEquals("primary call during hours", aesEncryptionService.decrypt(reloaded.getNotesEncrypted()));

        // Cross-user isolation: another user cannot update or delete.
        assertThrows(AccessDeniedException.class,
                () -> profileService.updateContact(other.getId(), saved.getId(),
                        "Hacked", null, null, null, null, null),
                "another user must not be able to update this contact");
        assertThrows(AccessDeniedException.class,
                () -> profileService.deleteContact(other.getId(), saved.getId()),
                "another user must not be able to delete this contact");

        // Other user's contact list is empty — no enumeration leak.
        assertTrue(profileService.getContacts(other.getId()).isEmpty());

        // Owner can update + delete.
        profileService.updateContact(owner.getId(), saved.getId(),
                "Primary Guardian", null, null, null, null, null);
        entityManager.flush();
        entityManager.clear();
        UserContactEntity updated = profileService.getContacts(owner.getId()).get(0);
        assertEquals("Primary Guardian", updated.getLabel());

        profileService.deleteContact(owner.getId(), saved.getId());
        entityManager.flush();
        assertTrue(profileService.getContacts(owner.getId()).isEmpty());
    }

    // ── fixture helpers ───────────────────────────────────────────────

    private record Ctx(UserEntity student, UserEntity teacher, UserEntity admin,
                        InventoryItemEntity item) {}

    private Ctx seed(String tag) {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setName("Dept-" + tag);
        dept = departmentRepository.save(dept);

        UserEntity student = persistUser("student-" + tag, Role.STUDENT);
        UserEntity teacher = persistUser("teacher-" + tag, dept, Role.TEACHER);
        UserEntity admin = persistUser("admin-" + tag, Role.ADMIN);

        ZoneEntity zone = new ZoneEntity();
        zone.setName("Zone-" + tag);
        zone = zoneRepository.save(zone);

        StorageLocationEntity loc = new StorageLocationEntity();
        loc.setName("Loc-" + tag);
        loc.setXCoord(BigDecimal.ZERO);
        loc.setYCoord(BigDecimal.ZERO);
        loc.setLevel(0);
        loc.setZone(zone);
        loc.setCapacity(10);
        loc.setCurrentOccupancy(0);
        loc = storageLocationRepository.save(loc);

        InventoryItemEntity item = new InventoryItemEntity();
        item.setName("Item-" + tag);
        item.setIsActive(true);
        item.setQuantityAvailable(5);
        item.setRequiresApproval(true);
        item.setItemCondition(ItemCondition.NEW);
        item.setPriceUsd(BigDecimal.valueOf(10));
        item.setLocation(loc);
        item.setDepartment(dept);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        item = inventoryItemRepository.save(item);

        entityManager.flush();
        return new Ctx(student, teacher, admin, item);
    }

    private UserEntity persistUser(String username, Role role) {
        return persistUser(username, null, role);
    }

    private UserEntity persistUser(String username, DepartmentEntity dept, Role role) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setDisplayName(username);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPersonalizationEnabled(true);
        if (dept != null) u.setDepartment(dept);
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

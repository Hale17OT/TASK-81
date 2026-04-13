package com.campusstore.integration.persistence;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.RequestService;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;
import com.campusstore.integration.support.WithMockCampusUser;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-mocked integration coverage for three sensitive paths the audit called out:
 *
 * <ol>
 *   <li>Strict pending-request visibility: a teacher who is NOT the assigned approver
 *       must not see the request, even if they share the item's department.</li>
 *   <li>Assigned-approver scope: only the teacher set as {@code approver} at creation
 *       time can approve/reject.</li>
 *   <li>Forced-password gate: an authenticated user with
 *       {@code password_change_required=true} gets redirected to
 *       {@code /account/change-password} for any other page request.</li>
 * </ol>
 *
 * Runs with real services + real repositories against the H2 test DB — no
 * {@code @MockitoBean} on core services.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationAndGatePersistenceTest {

    @Autowired private RequestService requestService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @Test
    void pendingVisibility_onlyAssignedTeacherSeesTheRequest() {
        Ctx ctx = seedTwoTeachersAndRequester("pend-" + System.nanoTime());

        // Create a pending request as the student. The lower-id teacher (ctx.teacher1)
        // gets assigned as the approver per RequestService.findAssignedApprover semantics.
        ItemRequestEntity request = requestService.createRequest(
                ctx.student.getId(), ctx.item.getId(), 1, "need for class");
        assertEquals(RequestStatus.PENDING_APPROVAL, request.getStatus());
        // approver_id scalar is insertable=false, so in-memory may be null until reload;
        // the @ManyToOne relationship is authoritative within the same transaction.
        assertNotNull(request.getApprover(),
                "createRequest must auto-assign an approver under the explicit-assignment model");
        assertEquals(ctx.teacher1.getId(), request.getApprover().getId(),
                "deterministic assignment: the lowest-id eligible teacher");

        entityManager.flush();
        entityManager.clear();

        // Assigned teacher sees the request in their pending queue.
        Page<ItemRequestEntity> teacher1Pending = requestService.listPendingApprovals(
                ctx.teacher1.getId(), PageRequest.of(0, 20));
        assertTrue(teacher1Pending.getContent().stream().anyMatch(r -> r.getId().equals(request.getId())),
                "assigned teacher must see the pending request");

        // Non-assigned teacher (same department) no longer sees it.
        Page<ItemRequestEntity> teacher2Pending = requestService.listPendingApprovals(
                ctx.teacher2.getId(), PageRequest.of(0, 20));
        assertTrue(teacher2Pending.getContent().stream().noneMatch(r -> r.getId().equals(request.getId())),
                "non-assigned teacher must NOT see the pending request");

        // Non-assigned teacher is also blocked from the detail view.
        assertThrows(AccessDeniedException.class,
                () -> requestService.getRequest(request.getId(), ctx.teacher2.getId()),
                "non-assigned teacher must be denied access to request detail");
    }

    @Test
    void approveRequest_onlyAssignedTeacherCanAct() {
        Ctx ctx = seedTwoTeachersAndRequester("approve-" + System.nanoTime());
        ItemRequestEntity request = requestService.createRequest(
                ctx.student.getId(), ctx.item.getId(), 1, "need for class");
        assertEquals(ctx.teacher1.getId(), request.getApprover().getId());

        entityManager.flush();
        entityManager.clear();

        assertThrows(AccessDeniedException.class,
                () -> requestService.approveRequest(request.getId(), ctx.teacher2.getId()),
                "only the assigned teacher may approve under the explicit-assignment model");

        ItemRequestEntity approved = requestService.approveRequest(request.getId(), ctx.teacher1.getId());
        assertEquals(RequestStatus.APPROVED, approved.getStatus());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"}, passwordChangeRequired = true)
    void forcedPasswordGate_redirectsBrowserToChangePasswordPage() throws Exception {
        mockMvc.perform(get("/admin/warehouse"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/account/change-password")));
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"}, passwordChangeRequired = true)
    void forcedPasswordGate_returnsStructured403ForApiRequests() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"}, passwordChangeRequired = true)
    void forcedPasswordGate_allowsTheRotationFlowItself() throws Exception {
        // The change-password page must stay reachable even while gated, otherwise the
        // forced-rotation workflow could not be completed.
        mockMvc.perform(get("/account/change-password"))
                .andExpect(status().isOk());
    }

    // ── fixture helpers ───────────────────────────────────────────────

    private record Ctx(UserEntity student, UserEntity teacher1, UserEntity teacher2,
                        InventoryItemEntity item) {}

    private Ctx seedTwoTeachersAndRequester(String tag) {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setName("Dept-" + tag);
        dept = departmentRepository.save(dept);

        UserEntity student = persistUser("student-" + tag, null, Role.STUDENT);
        UserEntity teacher1 = persistUser("teacher1-" + tag, dept, Role.TEACHER);
        UserEntity teacher2 = persistUser("teacher2-" + tag, dept, Role.TEACHER);

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
        return new Ctx(student, teacher1, teacher2, item);
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

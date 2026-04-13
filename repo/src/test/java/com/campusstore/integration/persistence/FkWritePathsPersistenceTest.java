package com.campusstore.integration.persistence;

import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.CategoryService;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.core.service.ZoneService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneDistanceEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.CategoryRepository;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneDistanceRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-backed integration tests for the FK write-path fixes
 * (user→department, category→parent, zone_distance→zones).
 *
 * These drive the REAL services against the in-memory H2 DB (no mocks for core services
 * or repositories) so that reference-vs-value ORM mistakes — previously silent due to
 * {@code insertable=false/updatable=false} on scalar FK columns — get caught here instead
 * of in production.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FkWritePathsPersistenceTest {

    @Autowired private UserManagementService userManagementService;
    @Autowired private CategoryService categoryService;
    @Autowired private ZoneService zoneService;

    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private ZoneDistanceRepository zoneDistanceRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void userDepartment_setOnCreateAndUpdate_actuallyPersists() {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setName("PhysicsDept-" + System.nanoTime());
        DepartmentEntity savedDept = departmentRepository.save(dept);

        UserEntity created = userManagementService.createUser(
                "fkuser-" + System.nanoTime(),
                "correctpass1",
                "FK User",
                "fk@campus.edu",
                null,
                Role.STUDENT,
                savedDept.getId(),
                null);

        // Flush + clear so the next fetch round-trips through the DB, not the session cache.
        entityManager.flush();
        entityManager.clear();

        UserEntity reloaded = userRepository.findById(created.getId()).orElseThrow();
        assertNotNull(reloaded.getDepartment(),
                "department relationship must persist on create");
        assertEquals(savedDept.getId(), reloaded.getDepartment().getId());

        // Update to a different department.
        DepartmentEntity other = new DepartmentEntity();
        other.setName("MathDept-" + System.nanoTime());
        DepartmentEntity otherSaved = departmentRepository.save(other);

        userManagementService.updateUser(reloaded.getId(), null, null, null,
                otherSaved.getId(), null);

        entityManager.flush();
        entityManager.clear();

        UserEntity reloaded2 = userRepository.findById(created.getId()).orElseThrow();
        assertEquals(otherSaved.getId(), reloaded2.getDepartment().getId(),
                "department reassignment must persist on update");
    }

    @Test
    void categoryParent_setOnCreate_actuallyPersists() {
        CategoryEntity parent = categoryService.create(
                "ParentCat-" + System.nanoTime(), "top-level", null, null);

        CategoryEntity child = categoryService.create(
                "ChildCat-" + System.nanoTime(), "under parent", parent.getId(), null);

        entityManager.flush();
        entityManager.clear();

        CategoryEntity reloaded = categoryRepository.findById(child.getId()).orElseThrow();
        assertNotNull(reloaded.getParent(),
                "category.parent relationship must persist when parentId is provided");
        assertEquals(parent.getId(), reloaded.getParent().getId());
    }

    @Test
    void zoneDistance_setDistance_persistsBothDirections() {
        ZoneEntity zoneA = new ZoneEntity();
        zoneA.setName("A-" + System.nanoTime());
        ZoneEntity zoneB = new ZoneEntity();
        zoneB.setName("B-" + System.nanoTime());
        ZoneEntity savedA = zoneRepository.save(zoneA);
        ZoneEntity savedB = zoneRepository.save(zoneB);

        zoneService.setDistance(savedA.getId(), savedB.getId(), BigDecimal.valueOf(3.5), null);

        entityManager.flush();
        entityManager.clear();

        Optional<ZoneDistanceEntity> forward = zoneDistanceRepository
                .findByFromZoneIdAndToZoneId(savedA.getId(), savedB.getId());
        Optional<ZoneDistanceEntity> reverse = zoneDistanceRepository
                .findByFromZoneIdAndToZoneId(savedB.getId(), savedA.getId());

        assertTrue(forward.isPresent(), "forward direction must persist");
        assertTrue(reverse.isPresent(), "reverse direction must persist (bidirectional upsert)");
        assertEquals(0, forward.get().getWeight().compareTo(BigDecimal.valueOf(3.5)));
        assertNotNull(forward.get().getFromZone(), "fromZone relationship must be populated");
        assertNotNull(forward.get().getToZone(), "toZone relationship must be populated");
        assertEquals(savedA.getId(), forward.get().getFromZone().getId());
        assertEquals(savedB.getId(), forward.get().getToZone().getId());

        // Upsert: updating the same pair must not create a duplicate row.
        zoneService.setDistance(savedA.getId(), savedB.getId(), BigDecimal.valueOf(4.2), null);
        entityManager.flush();
        entityManager.clear();

        ZoneDistanceEntity updatedForward = zoneDistanceRepository
                .findByFromZoneIdAndToZoneId(savedA.getId(), savedB.getId()).orElseThrow();
        assertEquals(0, updatedForward.getWeight().compareTo(BigDecimal.valueOf(4.2)),
                "weight must update in place, not create a new row");
        assertEquals(forward.get().getId(), updatedForward.getId(),
                "same row should be reused for the same (from,to) pair");
    }
}

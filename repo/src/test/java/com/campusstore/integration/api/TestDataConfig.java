package com.campusstore.integration.api;

import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import com.campusstore.core.service.CategoryService;
import com.campusstore.core.service.DepartmentService;
import com.campusstore.core.service.InventoryService;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.core.service.ZoneService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
import com.campusstore.infrastructure.persistence.entity.DataRetentionPolicyEntity;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.DataRetentionPolicyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;

/**
 * Seeds repeatable test data for HTTP black-box integration tests.
 * Runs as an {@link ApplicationRunner} with {@code @Order(10)} so it
 * executes after the production {@code SeedDataInitializer} (order 1).
 */
@TestConfiguration
public class TestDataConfig {

    private static final Logger log = LoggerFactory.getLogger(TestDataConfig.class);

    @Bean
    @Order(10)
    public ApplicationRunner testDataInitializer(
            UserManagementService userManagementService,
            DepartmentService departmentService,
            CategoryService categoryService,
            ZoneService zoneService,
            InventoryService inventoryService,
            WarehouseService warehouseService,
            DataRetentionPolicyRepository policyRepository) {
        return args -> {
            log.info("Seeding test data for HTTP integration tests");

            // 1. Create a test department
            DepartmentEntity dept = departmentService.create(
                    "Test Department", "Department for integration tests", null);
            log.info("Test department created id={}", dept.getId());

            // 2. Create a test category
            CategoryEntity cat = categoryService.create(
                    "Test Category", "Category for integration tests", null, null);
            log.info("Test category created id={}", cat.getId());

            // 3. Create a test zone
            ZoneEntity zone = zoneService.create(
                    "Test Zone", "Zone for integration tests", "Main Building", 1, null);
            log.info("Test zone created id={}", zone.getId());

            // 4. Create a storage location in the test zone
            StorageLocationEntity location = warehouseService.createLocation(
                    "LOC-TEST-001", zone.getId(),
                    BigDecimal.valueOf(1.0), BigDecimal.valueOf(2.0), 0,
                    TemperatureZone.AMBIENT, SecurityLevel.STANDARD, 100, null);
            log.info("Test storage location created id={}", location.getId());

            // 5. Create a test inventory item
            InventoryItemEntity item = inventoryService.createItem(
                    "Test Item", "A test inventory item", "SKU-TEST-001",
                    cat.getId(), dept.getId(), location.getId(),
                    BigDecimal.valueOf(9.99), 50, false, null,
                    ItemCondition.NEW, null);
            log.info("Test inventory item created id={}", item.getId());

            // 6. Create admin user
            userManagementService.createUser(
                    "testadmin", "Admin123!", "Test Admin",
                    "testadmin@campus.edu", "5551110001",
                    Role.ADMIN, dept.getId(), null);
            log.info("Test admin user created");

            // 7. Create teacher user
            userManagementService.createUser(
                    "testteacher", "Teacher123!", "Test Teacher",
                    "testteacher@campus.edu", "5551110002",
                    Role.TEACHER, dept.getId(), null);
            log.info("Test teacher user created");

            // 8. Create student user
            userManagementService.createUser(
                    "teststudent", "Student123!", "Test Student",
                    "teststudent@campus.edu", "5551110003",
                    Role.STUDENT, dept.getId(), null);
            log.info("Test student user created");

            // 9. Seed data retention policies needed by PolicyAdminApiHttpTest
            DataRetentionPolicyEntity policy = new DataRetentionPolicyEntity();
            policy.setEntityType("search_log");
            policy.setRetentionDays(365);
            policy.setDescription("Search log entries retained for 1 year");
            policyRepository.save(policy);
            log.info("Test data retention policy seeded");

            log.info("Test data seeding complete");
        };
    }
}

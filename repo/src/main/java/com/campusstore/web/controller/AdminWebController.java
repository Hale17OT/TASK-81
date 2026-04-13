package com.campusstore.web.controller;

import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import com.campusstore.infrastructure.persistence.entity.AuditLogEntity;
import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import com.campusstore.infrastructure.persistence.entity.EmailOutboxEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebController {

    private final InternalApiClient apiClient;

    public AdminWebController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // -- Inventory Management --

    @GetMapping("/inventory")
    public String inventoryList(@RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "active", required = false) Boolean active,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @RequestParam(value = "size", defaultValue = "20") int size,
                                Model model) {
        model.addAttribute("currentPage", "inventory");

        PagedResponse<InventoryItemEntity> items = apiClient.listItems(PageRequest.of(page, size));
        model.addAttribute("items", items);
        model.addAttribute("keyword", keyword);
        model.addAttribute("activeFilter", active);

        return "admin/inventory";
    }

    @GetMapping("/inventory/new")
    public String newItemForm(Model model) {
        model.addAttribute("currentPage", "inventory");
        model.addAttribute("editMode", false);
        model.addAttribute("categories", apiClient.listCategories());
        model.addAttribute("departments", apiClient.listDepartments());
        model.addAttribute("locations", apiClient.listLocations(PageRequest.of(0, 1000)).getContent());
        return "admin/inventory-form";
    }

    @GetMapping("/inventory/edit/{id}")
    public String editItemForm(@PathVariable Long id, Model model) {
        model.addAttribute("currentPage", "inventory");
        model.addAttribute("editMode", true);

        InventoryItemEntity item = apiClient.getItem(id);
        model.addAttribute("item", item);
        model.addAttribute("categories", apiClient.listCategories());
        model.addAttribute("departments", apiClient.listDepartments());
        model.addAttribute("locations", apiClient.listLocations(PageRequest.of(0, 1000)).getContent());

        return "admin/inventory-form";
    }

    // -- User Management --

    @GetMapping("/users")
    public String userList(@RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "size", defaultValue = "20") int size,
                           Model model) {
        model.addAttribute("currentPage", "users");

        PagedResponse<UserEntity> users = apiClient.listUsers(PageRequest.of(page, size));
        model.addAttribute("users", users);

        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("currentPage", "users");
        model.addAttribute("roles", Role.values());
        model.addAttribute("departments", apiClient.listDepartments());
        return "admin/user-form";
    }

    @GetMapping("/users/{id}")
    public String editUserPage(@PathVariable Long id, Model model) {
        model.addAttribute("currentPage", "users");
        model.addAttribute("editMode", true);
        UserEntity user = apiClient.getUserById(id);
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        model.addAttribute("departments", apiClient.listDepartments());
        return "admin/user-form";
    }

    @PostMapping("/inventory")
    public String createItem(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @RequestParam String name, @RequestParam(required = false) String description,
                             @RequestParam(required = false) String sku, @RequestParam Long categoryId,
                             @RequestParam Long departmentId, @RequestParam(required = false) Long locationId,
                             @RequestParam BigDecimal priceUsd,
                             @RequestParam(required = false) String condition,
                             @RequestParam(defaultValue = "0") int quantityTotal,
                             @RequestParam(defaultValue = "false") boolean requiresApproval,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expirationDate,
                             RedirectAttributes redirectAttributes) {
        com.campusstore.core.domain.model.ItemCondition cond = condition != null
                ? com.campusstore.core.domain.model.ItemCondition.valueOf(condition)
                : com.campusstore.core.domain.model.ItemCondition.NEW;
        apiClient.createItem(name, description, sku, categoryId, departmentId, locationId,
                priceUsd, quantityTotal, requiresApproval, principal.getUserId(), cond, expirationDate);
        redirectAttributes.addFlashAttribute("successMessage", "Item created successfully");
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/{id}")
    public String updateItem(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @PathVariable Long id,
                             @RequestParam String name, @RequestParam(required = false) String description,
                             @RequestParam BigDecimal priceUsd,
                             @RequestParam(defaultValue = "0") Integer quantityTotal,
                             @RequestParam(defaultValue = "false") Boolean requiresApproval,
                             RedirectAttributes redirectAttributes) {
        apiClient.updateItem(id, name, description, priceUsd, quantityTotal,
                requiresApproval, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Item updated successfully");
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/{id}/deactivate")
    public String deactivateItem(@AuthenticationPrincipal CampusUserPrincipal principal,
                                 @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.deactivateItem(id, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Item deactivated");
        return "redirect:/admin/inventory";
    }

    @PostMapping("/users")
    public String createUser(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @RequestParam String username, @RequestParam String password,
                             @RequestParam String displayName, @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) List<String> roles,
                             @RequestParam(required = false) Long departmentId,
                             RedirectAttributes redirectAttributes) {
        // Create user with first role, then add additional roles
        Role firstRole = (roles != null && !roles.isEmpty()) ? Role.valueOf(roles.get(0)) : Role.STUDENT;
        UserEntity created = apiClient.createUser(username, password, displayName, email, phone,
                firstRole, principal.getUserId());

        // Assign additional roles beyond the first
        if (roles != null && roles.size() > 1) {
            for (int i = 1; i < roles.size(); i++) {
                apiClient.assignRole(created.getId(), Role.valueOf(roles.get(i)), principal.getUserId());
            }
        }

        // Set department if provided
        if (departmentId != null) {
            apiClient.updateUser(created.getId(), null, null, null, departmentId, principal.getUserId());
        }

        redirectAttributes.addFlashAttribute("successMessage", "User created successfully");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @PathVariable Long id,
                             @RequestParam String displayName,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) Long departmentId,
                             RedirectAttributes redirectAttributes) {
        apiClient.updateUser(id, displayName, email, phone, departmentId, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "User updated successfully");
        return "redirect:/admin/users";
    }

    @PostMapping("/email-outbox/export")
    public ResponseEntity<byte[]> exportOutbox() {
        byte[] zip = apiClient.exportOutbox();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=email-outbox.zip")
                .header("Content-Type", "application/zip")
                .body(zip);
    }

    // -- Warehouse Dashboard --

    @GetMapping("/warehouse")
    public String warehouseDashboard(@RequestParam(value = "page", defaultValue = "0") int page,
                                      @RequestParam(value = "size", defaultValue = "20") int size,
                                      Model model) {
        model.addAttribute("currentPage", "warehouse");

        PagedResponse<StorageLocationEntity> locations =
                apiClient.listLocations(PageRequest.of(page, size));
        model.addAttribute("locations", locations);
        model.addAttribute("temperatureZones", TemperatureZone.values());
        model.addAttribute("securityLevels", SecurityLevel.values());

        // Populate items for the putaway selector. Cap at 500 so the dropdown stays usable;
        // larger catalogs should drive a search-style picker, but this unblocks the core flow.
        PagedResponse<InventoryItemEntity> items = apiClient.listItems(PageRequest.of(0, 500));
        model.addAttribute("items", items.getContent());

        return "warehouse/index";
    }

    // -- Crawler Dashboard --

    @GetMapping("/crawler")
    public String crawlerDashboard(Model model) {
        model.addAttribute("currentPage", "crawler");

        List<CrawlerJobEntity> jobs = apiClient.listCrawlerJobs();
        model.addAttribute("jobs", jobs);

        return "crawler/index";
    }

    // -- Audit Log --

    @GetMapping("/audit")
    public String auditLog(@RequestParam(value = "action", required = false) String action,
                           @RequestParam(value = "entityType", required = false) String entityType,
                           @RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "size", defaultValue = "20") int size,
                           Model model) {
        model.addAttribute("currentPage", "audit");

        Instant fromInstant = from != null ? from.toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.toInstant(ZoneOffset.UTC) : null;

        PagedResponse<AuditLogEntity> entries =
                apiClient.queryAudit(action, entityType, null, null, fromInstant, toInstant,
                        PageRequest.of(page, size));
        model.addAttribute("entries", entries);
        model.addAttribute("action", action);
        model.addAttribute("entityType", entityType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin/audit";
    }

    // -- Governance Policies --
    // Surfaces the data_retention_policy table so admins can view and edit retention
    // windows from the UI. Required by the prompt's "Administrators manage ... policies"
    // capability — previously only adjustable via direct SQL.

    @GetMapping("/policies")
    public String policies(Model model) {
        model.addAttribute("currentPage", "policies");
        model.addAttribute("policies", apiClient.listPolicies());
        return "admin/policies";
    }

    @PostMapping("/policies/{entityType}")
    public String updatePolicy(@PathVariable String entityType,
                                @RequestParam("retentionDays") int retentionDays,
                                @RequestParam(value = "description", required = false) String description,
                                RedirectAttributes redirectAttributes) {
        apiClient.updatePolicy(entityType, retentionDays, description);
        redirectAttributes.addFlashAttribute("successMessage",
                "Policy updated for " + entityType);
        return "redirect:/admin/policies";
    }

    // -- Email Outbox --

    @GetMapping("/email-outbox")
    public String emailOutbox(@RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              @RequestParam(value = "size", defaultValue = "20") int size,
                              Model model) {
        model.addAttribute("currentPage", "email-outbox");

        PagedResponse<EmailOutboxEntity> emails = apiClient.listOutbox(PageRequest.of(page, size));
        model.addAttribute("emails", emails);
        model.addAttribute("statusFilter", status);

        return "admin/email-outbox";
    }
}

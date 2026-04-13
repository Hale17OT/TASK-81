package com.campusstore.integration.security;

import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.CategoryService;
import com.campusstore.core.service.CrawlerService;
import com.campusstore.core.service.DepartmentService;
import com.campusstore.core.service.EmailOutboxService;
import com.campusstore.core.service.InventoryService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.core.service.ProfileService;
import com.campusstore.core.service.RequestService;
import com.campusstore.core.service.SearchService;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.core.service.ZoneService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.campusstore.integration.support.WithMockCampusUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private RequestService requestService;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private CrawlerService crawlerService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private EmailOutboxService emailOutboxService;

    @MockitoBean
    private ZoneService zoneService;

    // ── Login ──────────────────────────────────────────────────────────

    @Test
    void login_withInvalidCredentials_redirectsToLoginError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "nonexistent")
                        .param("password", "wrongpassword")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    // ── Logout ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser", roles = "STUDENT")
    void logout_redirectsToLoginLogout() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"));
    }

    // ── Protected page redirect ────────────────────────────────────────

    @Test
    void accessProtectedPage_withoutLogin_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void accessProtectedInventoryPage_withoutLogin_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/inventory"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ── CSRF protection ────────────────────────────────────────────────

    @Test
    void post_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "testuser")
                        .param("password", "password"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void apiPost_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType("application/json")
                        .content("{\"itemId\":1,\"quantity\":1,\"justification\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Login page accessibility ───────────────────────────────────────

    @Test
    void loginPage_isAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }
}

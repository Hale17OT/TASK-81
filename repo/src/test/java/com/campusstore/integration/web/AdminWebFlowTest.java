package com.campusstore.integration.web;

import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.integration.support.WithMockCampusUser;
import com.campusstore.web.client.InternalApiClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises Thymeleaf admin pages through MockMvc. The Thymeleaf layer now consumes the
 * REST API over HTTP (see {@code InternalApiClient} / {@code RestClientConfig}), so in
 * MockMvc tests (no real servlet listening) we mock {@code InternalApiClient} directly
 * to satisfy the web layer's dependencies without issuing actual HTTP calls. Full
 * HTTP round-trips are covered by the {@code integration.api.*} tests, which target
 * {@code /api/**} endpoints.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWebFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private InternalApiClient apiClient;

    @Test
    @WithMockCampusUser(roles = "ADMIN")
    void adminCanAccessNewUserForm() throws Exception {
        when(apiClient.listDepartments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/users/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"));
    }

    @Test
    @WithMockCampusUser(roles = "ADMIN")
    void adminCanCreateUser() throws Exception {
        UserEntity mockUser = new UserEntity();
        mockUser.setId(10L);
        mockUser.setUsername("newuser");
        mockUser.setDisplayName("New User");
        mockUser.setRoles(Collections.emptyList());
        when(apiClient.createUser(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mockUser);

        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("username", "newuser")
                        .param("password", "password123")
                        .param("displayName", "New User")
                        .param("roles", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockCampusUser(roles = "ADMIN")
    void adminCanAccessEditUserForm() throws Exception {
        UserEntity mockUser = new UserEntity();
        mockUser.setId(5L);
        mockUser.setUsername("existinguser");
        mockUser.setDisplayName("Existing User");
        mockUser.setDepartmentId(1L);
        UserRoleEntity role = new UserRoleEntity();
        role.setRole(Role.TEACHER);
        mockUser.setRoles(List.of(role));

        when(apiClient.getUserById(5L)).thenReturn(mockUser);
        when(apiClient.listDepartments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/users/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().attribute("editMode", true));
    }

    @Test
    @WithMockCampusUser(roles = "ADMIN")
    void adminCanUpdateUser() throws Exception {
        UserEntity mockUser = new UserEntity();
        mockUser.setId(5L);
        mockUser.setDisplayName("Updated Name");
        when(apiClient.updateUser(eq(5L), anyString(), any(), any(), any(), any()))
                .thenReturn(mockUser);

        mockMvc.perform(post("/admin/users/5")
                        .with(csrf())
                        .param("displayName", "Updated Name")
                        .param("departmentId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockCampusUser(roles = "STUDENT")
    void studentCannotAccessAdminPages() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }
}

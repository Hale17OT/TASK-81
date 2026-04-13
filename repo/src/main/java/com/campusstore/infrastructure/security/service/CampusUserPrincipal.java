package com.campusstore.infrastructure.security.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Extended UserDetails principal that carries additional user context
 * needed for authorization decisions (departmentId, homeZoneId).
 */
public class CampusUserPrincipal extends User {

    private final Long userId;
    private final String displayName;
    private final Long departmentId;
    private final Long homeZoneId;
    private final boolean passwordChangeRequired;

    public CampusUserPrincipal(Long userId, String username, String password,
                                String displayName, Long departmentId, Long homeZoneId,
                                Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, displayName, departmentId, homeZoneId,
                false, authorities);
    }

    public CampusUserPrincipal(Long userId, String username, String password,
                                String displayName, Long departmentId, Long homeZoneId,
                                boolean passwordChangeRequired,
                                Collection<? extends GrantedAuthority> authorities) {
        super(username, password, true, true, true, true, authorities);
        this.userId = userId;
        this.displayName = displayName;
        this.departmentId = departmentId;
        this.homeZoneId = homeZoneId;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public Long getHomeZoneId() {
        return homeZoneId;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public boolean hasRole(String role) {
        return getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isTeacher() {
        return hasRole("TEACHER");
    }

    public boolean isStudent() {
        return hasRole("STUDENT");
    }
}

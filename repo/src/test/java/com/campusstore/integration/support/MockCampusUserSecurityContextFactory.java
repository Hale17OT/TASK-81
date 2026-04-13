package com.campusstore.integration.support;

import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.List;

public class MockCampusUserSecurityContextFactory implements WithSecurityContextFactory<WithMockCampusUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCampusUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        List<SimpleGrantedAuthority> authorities = Arrays.stream(annotation.roles())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        CampusUserPrincipal principal = new CampusUserPrincipal(
                annotation.userId(),
                annotation.username(),
                "password",
                annotation.displayName(),
                annotation.departmentId(),
                annotation.homeZoneId(),
                annotation.passwordChangeRequired(),
                authorities
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, "password", authorities
        );

        context.setAuthentication(auth);
        return context;
    }
}

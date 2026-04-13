package com.campusstore.infrastructure.config;

import com.campusstore.infrastructure.security.service.CampusUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * When an authenticated user still has {@code password_change_required=true}, block
 * every non-essential request and redirect the browser to {@code /account/change-password}.
 * Static assets, auth endpoints, the change-password flow itself, and logout remain
 * accessible so the forced-rotation flow can actually complete.
 */
@Component
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    /** Paths that must stay reachable even when the user is gated on rotation. */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/account/change-password",
            "/logout",
            "/login",
            "/css/",
            "/js/",
            "/images/",
            "/favicon",
            "/actuator/health",
            "/api/auth/password"  // API-side password change is the programmatic equivalent
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;
        Object p = auth.getPrincipal();
        if (!(p instanceof CampusUserPrincipal principal)) return true;
        if (!principal.isPasswordChangeRequired()) return true;

        String path = request.getRequestURI();
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }

        // For API requests return a structured 403 so the caller doesn't follow a redirect
        // to an HTML page. Browser requests get a clean redirect to the rotation form.
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                  + "\"message\":\"Password change required. Rotate via PUT /api/auth/password before "
                  + "using other endpoints.\"}}");
            return false;
        }
        response.sendRedirect("/account/change-password");
        return false;
    }
}

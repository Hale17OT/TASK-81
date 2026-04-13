package com.campusstore.infrastructure.config;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Exception handler for web (Thymeleaf) controllers.
 * Catches domain exceptions and renders appropriate error pages or redirects with messages.
 */
@ControllerAdvice(basePackages = "com.campusstore.web")
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex) {
        log.warn("Web access denied: {}", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex) {
        log.info("Web resource not found: {}", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessError(BusinessException ex, RedirectAttributes redirectAttributes) {
        log.warn("Web business error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public String handleAuthorizationDenied(
            org.springframework.security.authorization.AuthorizationDeniedException ex) {
        log.warn("Web authorization denied: {}", ex.getMessage());
        return "error/403";
    }
}

package com.campusstore.web.controller;

import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Minimal self-service change-password flow that satisfies the "force rotation on first
 * login" governance control. Seeded accounts carry {@code password_change_required=true}
 * and {@link ForcePasswordChangeInterceptor} redirects them here until the flag clears.
 */
@Controller
@RequestMapping("/account")
public class ChangePasswordWebController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AesEncryptionService aesEncryptionService;

    public ChangePasswordWebController(UserRepository userRepository,
                                        PasswordEncoder passwordEncoder,
                                        AesEncryptionService aesEncryptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.aesEncryptionService = aesEncryptionService;
    }

    @GetMapping("/change-password")
    public String form(@AuthenticationPrincipal CampusUserPrincipal principal, Model model) {
        model.addAttribute("currentPage", "change-password");
        model.addAttribute("forceChange", principal != null && principal.isPasswordChangeRequired());
        return "account/change-password";
    }

    @PostMapping("/change-password")
    @Transactional
    public String submit(@AuthenticationPrincipal CampusUserPrincipal principal,
                          @RequestParam String oldPassword,
                          @RequestParam String newPassword,
                          @RequestParam String confirmPassword,
                          RedirectAttributes redirect,
                          HttpServletRequest request) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (newPassword == null || newPassword.length() < 8) {
            redirect.addFlashAttribute("errorMessage", "New password must be at least 8 characters");
            return "redirect:/account/change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("errorMessage", "New password confirmation does not match");
            return "redirect:/account/change-password";
        }

        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getUserId()));

        String currentBcryptHash = aesEncryptionService.decrypt(user.getPasswordHashEncrypted());
        if (!passwordEncoder.matches(oldPassword, currentBcryptHash)) {
            throw new BusinessException("Current password is incorrect");
        }

        String newBcryptHash = passwordEncoder.encode(newPassword);
        user.setPasswordHashEncrypted(aesEncryptionService.encrypt(newBcryptHash));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);

        // Invalidate the session so the stale in-memory principal (passwordChangeRequired=true)
        // is discarded. The transaction commits on method return, so the next login reads the
        // updated DB value and ForcePasswordChangeInterceptor will not intercept again.
        // Flash attributes cannot survive session invalidation, so use the standard
        // Spring Security ?logout param which renders the success banner on the login page.
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?logout=true";
    }
}

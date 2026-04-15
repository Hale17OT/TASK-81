package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.ChangePasswordRequest;
import com.campusstore.api.dto.LoginRequest;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AesEncryptionService aesEncryptionService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AesEncryptionService aesEncryptionService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.aesEncryptionService = aesEncryptionService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

            CampusUserPrincipal principal = (CampusUserPrincipal) authentication.getPrincipal();
            List<String> roles = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("userId", principal.getUserId());
            data.put("username", principal.getUsername());
            data.put("displayName", principal.getDisplayName());
            data.put("roles", roles);

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid username or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", principal.getUserId());
        data.put("username", principal.getUsername());
        data.put("displayName", principal.getDisplayName());
        data.put("roles", roles);
        data.put("departmentId", principal.getDepartmentId());
        data.put("homeZoneId", principal.getHomeZoneId());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new com.campusstore.core.domain.event.ResourceNotFoundException("User", principal.getUserId()));

        // Decrypt the AES-encrypted BCrypt hash to verify old password
        String currentBcryptHash = aesEncryptionService.decrypt(user.getPasswordHashEncrypted());
        if (!passwordEncoder.matches(request.getOldPassword(), currentBcryptHash)) {
            throw new com.campusstore.core.domain.event.BusinessException("Current password is incorrect");
        }

        // BCrypt encode new password, then AES encrypt for at-rest protection
        String newBcryptHash = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHashEncrypted(aesEncryptionService.encrypt(newBcryptHash));
        // Clear the force-rotation flag; the account is no longer using a bootstrap credential.
        user.setPasswordChangeRequired(false);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success());
    }
}

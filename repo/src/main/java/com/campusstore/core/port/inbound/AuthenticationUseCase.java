package com.campusstore.core.port.inbound;

/**
 * Inbound port for authentication operations.
 */
public interface AuthenticationUseCase {

    /**
     * Authenticate a user with username and password.
     *
     * @param username the username
     * @param password the raw password
     * @return an authentication result containing the token and user info
     */
    AuthResult login(String username, String password);

    /**
     * Invalidate the current user's session/token.
     */
    void logout();

    /**
     * Change a user's password after verifying the old password.
     *
     * @param userId      the user id
     * @param oldPassword the current password for verification
     * @param newPassword the new password
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * Retrieve the currently authenticated user's information.
     *
     * @return the current user details
     */
    CurrentUser getCurrentUser();

    // ── Result types ───────────────────────────────────────────────────

    record AuthResult(Long userId, String username, String displayName, String token) {
    }

    record CurrentUser(Long userId, String username, String displayName, java.util.List<String> roles) {
    }
}

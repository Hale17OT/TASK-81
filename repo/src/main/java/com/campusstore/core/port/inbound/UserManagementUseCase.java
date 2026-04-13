package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Inbound port for user administration operations.
 */
public interface UserManagementUseCase {

    /**
     * Create a new user account.
     *
     * @param command the user creation details
     * @return the id of the newly created user
     */
    Long createUser(CreateUserCommand command);

    /**
     * Update an existing user's profile fields.
     *
     * @param id      the user id
     * @param command the fields to update
     */
    void updateUser(Long id, UpdateUserCommand command);

    /**
     * Retrieve a user by id.
     *
     * @param id the user id
     * @return the user view
     */
    UserView getUserById(Long id);

    /**
     * List users with pagination.
     *
     * @param pageable pagination information
     * @return a page of user views
     */
    Page<UserView> listUsers(Pageable pageable);

    /**
     * Change a user's account status (active, disabled, blacklisted).
     *
     * @param id     the user id
     * @param status the new account status
     */
    void changeAccountStatus(Long id, AccountStatus status);

    /**
     * Assign a role to a user.
     *
     * @param userId    the user id
     * @param role      the role to assign
     * @param grantedBy the id of the user granting the role
     */
    void assignRole(Long userId, Role role, Long grantedBy);

    /**
     * Revoke a role from a user.
     *
     * @param userId the user id
     * @param role   the role to revoke
     */
    void revokeRole(Long userId, Role role);

    // ── Command types ──────────────────────────────────────────────────

    record CreateUserCommand(
            String username,
            String password,
            String displayName,
            String email,
            String phone,
            List<Role> roles,
            Long departmentId
    ) {
    }

    record UpdateUserCommand(
            String displayName,
            String email,
            String phone,
            Long departmentId
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record UserView(
            Long id,
            String username,
            String displayName,
            String email,
            String phone,
            AccountStatus accountStatus,
            List<Role> roles,
            Long departmentId
    ) {
    }
}

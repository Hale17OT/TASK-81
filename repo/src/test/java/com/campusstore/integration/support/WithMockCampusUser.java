package com.campusstore.integration.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = MockCampusUserSecurityContextFactory.class)
public @interface WithMockCampusUser {
    long userId() default 1L;
    String username() default "testuser";
    String displayName() default "Test User";
    long departmentId() default 1L;
    long homeZoneId() default 1L;
    String[] roles() default {"STUDENT"};
    /**
     * When true the authenticated principal is gated on forced password rotation.
     * Used to exercise the {@code ForcePasswordChangeInterceptor} without priming the DB.
     */
    boolean passwordChangeRequired() default false;
}

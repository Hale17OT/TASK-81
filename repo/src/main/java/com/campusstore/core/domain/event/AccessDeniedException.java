package com.campusstore.core.domain.event;

public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(message);
    }
}

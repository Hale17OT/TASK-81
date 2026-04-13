package com.campusstore.core.domain.event;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(message);
    }
}

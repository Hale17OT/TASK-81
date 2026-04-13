package com.campusstore.core.domain.event;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String entityType, Object id) {
        super(entityType + " not found with id: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

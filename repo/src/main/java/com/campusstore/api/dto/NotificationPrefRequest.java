package com.campusstore.api.dto;

import com.campusstore.core.domain.model.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class NotificationPrefRequest {

    @NotNull(message = "Preferences list is required")
    @Valid
    private List<NotificationPrefEntry> preferences;

    public NotificationPrefRequest() {
    }

    public List<NotificationPrefEntry> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<NotificationPrefEntry> preferences) {
        this.preferences = preferences;
    }

    public static class NotificationPrefEntry {

        @NotNull(message = "Notification type is required")
        private NotificationType type;

        private boolean enabled;

        private boolean emailEnabled;

        public NotificationPrefEntry() {
        }

        public NotificationType getType() {
            return type;
        }

        public void setType(NotificationType type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEmailEnabled() {
            return emailEnabled;
        }

        public void setEmailEnabled(boolean emailEnabled) {
            this.emailEnabled = emailEnabled;
        }
    }
}

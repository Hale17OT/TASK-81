/**
 * CampusStore — Notification Center Interactions
 */
(function() {
    'use strict';

    var notificationList = document.getElementById('notification-list');
    var markAllReadBtn = document.getElementById('mark-all-read');

    // Mark single notification as read
    window.markNotificationRead = function(notificationId, element) {
        campusApi.put('/api/notifications/' + notificationId + '/read')
            .then(function(response) {
                if (response.success) {
                    if (element) {
                        element.classList.add('notification-read');
                        var readBtn = element.querySelector('.mark-read-btn');
                        if (readBtn) readBtn.remove();
                    }
                    // Update badge
                    updateBadge();
                }
            })
            .catch(function() {
                showAlert('Failed to mark notification as read', 'error');
            });
    };

    // Mark all as read
    if (markAllReadBtn) {
        markAllReadBtn.addEventListener('click', function() {
            campusApi.put('/api/notifications/read-all')
                .then(function(response) {
                    if (response.success) {
                        document.querySelectorAll('.notification-item').forEach(function(item) {
                            item.classList.add('notification-read');
                            var readBtn = item.querySelector('.mark-read-btn');
                            if (readBtn) readBtn.remove();
                        });
                        updateBadge();
                        showAlert('All notifications marked as read', 'success');
                    }
                })
                .catch(function() {
                    showAlert('Failed to mark all as read', 'error');
                });
        });
    }

    function updateBadge() {
        var badge = document.getElementById('unread-count');
        if (badge) {
            campusApi.get('/api/notifications/unread-count')
                .then(function(response) {
                    if (response.success) {
                        var count = response.data;
                        badge.textContent = count > 99 ? '99+' : count;
                        badge.style.display = count > 0 ? 'inline-flex' : 'none';
                    }
                })
                .catch(function() {});
        }
    }

    // Notification dropdown toggle. The fragment ships with the `hidden` attribute set,
    // so toggling a CSS class is not enough — the attribute must be removed/re-added or
    // the dropdown stays invisible regardless of class state.
    var notifBell = document.getElementById('notificationToggle');
    var notifDropdown = document.getElementById('notificationDropdown');
    if (notifBell && notifDropdown) {
        notifBell.addEventListener('click', function(e) {
            e.stopPropagation();
            var nowOpen = notifDropdown.hasAttribute('hidden');
            if (nowOpen) {
                notifDropdown.removeAttribute('hidden');
                notifDropdown.classList.add('fluent-dropdown--open');
                notifBell.setAttribute('aria-expanded', 'true');
            } else {
                notifDropdown.setAttribute('hidden', '');
                notifDropdown.classList.remove('fluent-dropdown--open');
                notifBell.setAttribute('aria-expanded', 'false');
            }
        });

        // Click-away closes the dropdown — but ignore clicks inside the dropdown so
        // links/forms inside it still work.
        document.addEventListener('click', function(e) {
            if (!notifDropdown.contains(e.target) && e.target !== notifBell) {
                notifDropdown.setAttribute('hidden', '');
                notifDropdown.classList.remove('fluent-dropdown--open');
                notifBell.setAttribute('aria-expanded', 'false');
            }
        });
    }

})();

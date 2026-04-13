/**
 * CampusStore — Core Application JavaScript
 * All client-side interactions, CSRF handling, and notification polling.
 * No external CDN dependencies.
 */
(function() {
    'use strict';

    // CSRF Token Management
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    /**
     * Wrapper around fetch that includes CSRF token and JSON headers.
     */
    window.campusApi = {
        get: function(url) {
            return fetch(url, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                },
                credentials: 'same-origin'
            }).then(handleResponse);
        },

        post: function(url, data) {
            return fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                },
                credentials: 'same-origin',
                body: JSON.stringify(data)
            }).then(handleResponse);
        },

        put: function(url, data) {
            return fetch(url, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                },
                credentials: 'same-origin',
                body: data ? JSON.stringify(data) : undefined
            }).then(handleResponse);
        },

        delete: function(url) {
            return fetch(url, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                },
                credentials: 'same-origin'
            }).then(handleResponse);
        }
    };

    function handleResponse(response) {
        if (response.status === 401) {
            window.location.href = '/login';
            return Promise.reject(new Error('Unauthorized'));
        }
        if (response.status === 429) {
            showAlert('Too many requests. Please wait a moment and try again.', 'warning');
            return Promise.reject(new Error('Rate limited'));
        }
        return response.json();
    }

    // Alert system
    window.showAlert = function(message, type) {
        type = type || 'info';
        var container = document.getElementById('alert-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'alert-container';
            container.className = 'alert-container';
            document.body.insertBefore(container, document.body.firstChild);
        }

        var alert = document.createElement('div');
        alert.className = 'fluent-alert fluent-alert--' + type;
        alert.setAttribute('role', 'alert');
        alert.innerHTML = '<span>' + escapeHtml(message) + '</span>' +
            '<button class="fluent-alert-close" onclick="this.parentElement.remove()" aria-label="Close">&times;</button>';
        container.appendChild(alert);

        setTimeout(function() {
            if (alert.parentElement) {
                alert.remove();
            }
        }, 5000);
    };

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(text));
        return div.innerHTML;
    }

    // Notification Badge Polling
    function updateNotificationBadge() {
        var badge = document.getElementById('unread-count');
        if (!badge) return;

        campusApi.get('/api/notifications/unread-count')
            .then(function(response) {
                if (response.success && response.data !== undefined) {
                    var count = response.data;
                    badge.textContent = count > 99 ? '99+' : count;
                    badge.style.display = count > 0 ? 'inline-flex' : 'none';
                }
            })
            .catch(function() {
                // Silently fail — notification polling is non-critical
            });
    }

    // Poll every 30 seconds
    if (document.getElementById('unread-count')) {
        updateNotificationBadge();
        setInterval(updateNotificationBadge, 30000);
    }

    // Mobile Navigation Toggle
    var navToggle = document.getElementById('navToggle');
    var navMenu = document.getElementById('navMenu');
    if (navToggle && navMenu) {
        navToggle.addEventListener('click', function() {
            navMenu.classList.toggle('fluent-nav-items--open');
            navToggle.classList.toggle('fluent-nav-toggle--active');
        });
    }

    // Form submission protection (prevent double submit)
    document.querySelectorAll('form[data-prevent-double-submit]').forEach(function(form) {
        form.addEventListener('submit', function(e) {
            var submitBtn = form.querySelector('[type="submit"]');
            if (submitBtn && submitBtn.disabled) {
                e.preventDefault();
                return;
            }
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.classList.add('fluent-btn--loading');
                // Re-enable after 5 seconds as safety net
                setTimeout(function() {
                    submitBtn.disabled = false;
                    submitBtn.classList.remove('fluent-btn--loading');
                }, 5000);
            }
        });
    });

    // Confirmation dialogs
    document.querySelectorAll('[data-confirm]').forEach(function(el) {
        el.addEventListener('click', function(e) {
            var message = el.getAttribute('data-confirm');
            if (!confirm(message)) {
                e.preventDefault();
            }
        });
    });

    // Auto-dismiss alerts
    document.querySelectorAll('.fluent-alert[data-auto-dismiss]').forEach(function(alert) {
        var delay = parseInt(alert.getAttribute('data-auto-dismiss')) || 5000;
        setTimeout(function() {
            alert.style.opacity = '0';
            setTimeout(function() { alert.remove(); }, 300);
        }, delay);
    });

    // Loading states for async buttons
    window.setLoading = function(element, loading) {
        if (loading) {
            element.disabled = true;
            element.setAttribute('data-original-text', element.textContent);
            element.innerHTML = '<span class="fluent-spinner fluent-spinner--tiny"></span> Loading...';
        } else {
            element.disabled = false;
            element.textContent = element.getAttribute('data-original-text') || 'Submit';
        }
    };

    // Tooltip initialization
    document.querySelectorAll('[data-tooltip]').forEach(function(el) {
        el.addEventListener('mouseenter', function() {
            var tip = document.createElement('div');
            tip.className = 'fluent-tooltip';
            tip.textContent = el.getAttribute('data-tooltip');
            el.appendChild(tip);
        });
        el.addEventListener('mouseleave', function() {
            var tip = el.querySelector('.fluent-tooltip');
            if (tip) tip.remove();
        });
    });

})();

/**
 * CampusStore — Search Page Enhancements
 * On-device search history (localStorage), trending term clicks, and favorite toggles.
 * Search form submits as a normal GET — server renders results via Thymeleaf.
 */
(function() {
    'use strict';

    var searchForm = document.getElementById('search-form');
    var searchInput = document.getElementById('search-input');

    // === On-device search history (localStorage) ===
    var HISTORY_KEY = 'campusstore_search_history';
    var MAX_HISTORY = 20;

    function saveSearchToLocalHistory(query) {
        if (!query || !query.trim()) return;
        try {
            var history = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
            history = history.filter(function(h) { return h !== query.trim(); });
            history.unshift(query.trim());
            if (history.length > MAX_HISTORY) history = history.slice(0, MAX_HISTORY);
            localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
        } catch (e) { /* localStorage unavailable */ }
    }

    function getLocalSearchHistory() {
        try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); }
        catch (e) { return []; }
    }

    window.clearLocalSearchHistory = function() {
        try { localStorage.removeItem(HISTORY_KEY); } catch (e) {}
        var container = document.getElementById('local-search-history');
        if (container) container.style.display = 'none';
    };

    // Save current search to local history on page load (form was just submitted)
    if (searchInput && searchInput.value && searchInput.value.trim()) {
        saveSearchToLocalHistory(searchInput.value.trim());
    }

    // Render local search history below search input
    var historyContainer = document.getElementById('local-search-history');
    if (historyContainer) {
        var history = getLocalSearchHistory();
        if (history.length > 0) {
            var html = '<span class="fluent-text fluent-text--muted">Recent:</span> ';
            history.slice(0, 5).forEach(function(h) {
                var escaped = h.replace(/'/g, "\\'").replace(/</g, '&lt;').replace(/>/g, '&gt;');
                html += '<button type="button" class="fluent-chip fluent-chip--outline fluent-chip--small" ' +
                    'onclick="document.getElementById(\'search-input\').value=\'' + escaped +
                    '\';document.getElementById(\'search-form\').submit();">' +
                    escaped + '</button> ';
            });
            html += '<button type="button" class="fluent-btn fluent-btn--subtle fluent-btn--small" ' +
                'onclick="clearLocalSearchHistory()">Clear</button>';
            historyContainer.innerHTML = html;
            historyContainer.style.display = 'flex';
        }
    }

    // === Favorite toggle on item detail page ===
    document.querySelectorAll('.favorite-btn').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            var itemId = this.getAttribute('data-item-id');
            if (itemId) {
                campusApi.post('/api/favorites/' + itemId)
                    .then(function(response) {
                        if (response.success) {
                            btn.querySelector('.favorite-icon').innerHTML = '&#9733;';
                        }
                    })
                    .catch(function() {
                        if (typeof showAlert === 'function') showAlert('Failed to update favorite', 'error');
                    });
            }
        });
    });

})();

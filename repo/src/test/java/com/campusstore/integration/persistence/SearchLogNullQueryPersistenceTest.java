package com.campusstore.integration.persistence;

import com.campusstore.core.service.SearchService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.SearchLogEntity;
import com.campusstore.infrastructure.persistence.repository.SearchLogRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the null-query search case: {@code query_text} on
 * {@code search_log} is NOT NULL, and the API exposes {@code q} as optional. Without
 * normalization, a filter-only search (e.g. "price range + category") would blow up at
 * flush. This test runs the real {@link SearchService} against H2 and verifies the log
 * row lands with an empty-string {@code queryText} instead of NULL.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchLogNullQueryPersistenceTest {

    @Autowired private SearchService searchService;
    @Autowired private SearchLogRepository searchLogRepository;

    @Test
    void search_withNullQuery_normalizesToEmptyStringAndLogsCleanly() {
        long before = searchLogRepository.count();

        // Null keyword + all filters null → the empty-search case the audit cited.
        Page<InventoryItemEntity> page = searchService.search(
                null, null, null, null, null, false, null,
                PageRequest.of(0, 10));

        assertThat(page).isNotNull();

        List<SearchLogEntity> all = searchLogRepository.findAll();
        assertThat(all).hasSizeGreaterThan((int) before);
        SearchLogEntity latest = all.get(all.size() - 1);
        assertThat(latest.getQueryText())
                .as("null query must normalize to empty string to satisfy NOT NULL")
                .isEqualTo("");
    }
}

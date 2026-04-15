package com.campusstore.unit.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.service.SearchService;
import com.campusstore.infrastructure.persistence.entity.BrowsingHistoryEntity;
import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import com.campusstore.infrastructure.persistence.repository.BrowsingHistoryRepository;
import com.campusstore.infrastructure.persistence.repository.FavoriteRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.SearchLogRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneDistanceRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SearchService} covering favorites CRUD,
 * browsing history recording, and search history retrieval.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private SearchLogRepository searchLogRepository;
    @Mock private BrowsingHistoryRepository browsingHistoryRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private ZoneDistanceRepository zoneDistanceRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private SearchService searchService;

    // ── addFavorite ───────────────────────────────────────────────────

    @Test
    void addFavorite_alreadyFavorited_throwsConflictException() {
        when(favoriteRepository.existsByUserIdAndItemId(1L, 10L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> searchService.addFavorite(1L, 10L));
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_itemNotFound_throwsResourceNotFoundException() {
        when(favoriteRepository.existsByUserIdAndItemId(1L, 99L)).thenReturn(false);
        when(inventoryItemRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> searchService.addFavorite(1L, 99L));
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_validRequest_savesFavorite() {
        when(favoriteRepository.existsByUserIdAndItemId(1L, 10L)).thenReturn(false);
        when(inventoryItemRepository.existsById(10L)).thenReturn(true);
        FavoriteEntity saved = new FavoriteEntity();
        saved.setId(1L);
        saved.setUserId(1L);
        saved.setItemId(10L);
        when(favoriteRepository.save(any(FavoriteEntity.class))).thenReturn(saved);

        FavoriteEntity result = searchService.addFavorite(1L, 10L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(10L, result.getItemId());
        verify(favoriteRepository).save(any(FavoriteEntity.class));
    }

    @Test
    void addFavorite_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> searchService.addFavorite(null, 10L));
    }

    // ── removeFavorite ────────────────────────────────────────────────

    @Test
    void removeFavorite_notFound_throwsResourceNotFoundException() {
        when(favoriteRepository.existsByUserIdAndItemId(1L, 10L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> searchService.removeFavorite(1L, 10L));
        verify(favoriteRepository, never()).deleteByUserIdAndItemId(anyLong(), anyLong());
    }

    @Test
    void removeFavorite_found_deletesEntry() {
        when(favoriteRepository.existsByUserIdAndItemId(1L, 10L)).thenReturn(true);

        searchService.removeFavorite(1L, 10L);

        verify(favoriteRepository).deleteByUserIdAndItemId(1L, 10L);
    }

    // ── getFavorites ──────────────────────────────────────────────────

    @Test
    void getFavorites_delegatesToRepository() {
        FavoriteEntity f = new FavoriteEntity();
        f.setUserId(1L);
        f.setItemId(5L);
        when(favoriteRepository.findByUserId(1L)).thenReturn(List.of(f));

        List<FavoriteEntity> result = searchService.getFavorites(1L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getItemId());
    }

    @Test
    void getFavorites_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> searchService.getFavorites(null));
    }

    // ── recordBrowse ──────────────────────────────────────────────────

    @Test
    void recordBrowse_underLimit_savesEntry() {
        when(browsingHistoryRepository.countByUserId(1L)).thenReturn(5L);
        when(browsingHistoryRepository.save(any(BrowsingHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        searchService.recordBrowse(1L, 10L);

        verify(browsingHistoryRepository).save(any(BrowsingHistoryEntity.class));
        verify(browsingHistoryRepository, never()).deleteAll(any());
    }

    @Test
    void recordBrowse_overLimit_trimsHistory() {
        BrowsingHistoryEntity old = new BrowsingHistoryEntity();
        when(browsingHistoryRepository.countByUserId(1L)).thenReturn(31L);
        when(browsingHistoryRepository.save(any(BrowsingHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Return 31 entries so subList(30, 31) yields the one to delete
        List<BrowsingHistoryEntity> history = new java.util.ArrayList<>();
        for (int i = 0; i < 31; i++) {
            history.add(old);
        }
        when(browsingHistoryRepository.findByUserIdOrderByViewedAtDesc(eq(1L), any()))
                .thenReturn(new PageImpl<>(history));

        searchService.recordBrowse(1L, 10L);

        verify(browsingHistoryRepository).deleteAll(anyList());
    }

    @Test
    void recordBrowse_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> searchService.recordBrowse(null, 10L));
    }
}

package com.campusstore.core.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.infrastructure.persistence.entity.BrowsingHistoryEntity;
import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.SearchLogEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.persistence.repository.BrowsingHistoryRepository;
import com.campusstore.infrastructure.persistence.repository.FavoriteRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.SearchLogRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneDistanceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final SearchLogRepository searchLogRepository;
    private final BrowsingHistoryRepository browsingHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final ZoneDistanceRepository zoneDistanceRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public SearchService(InventoryItemRepository inventoryItemRepository,
                         SearchLogRepository searchLogRepository,
                         BrowsingHistoryRepository browsingHistoryRepository,
                         FavoriteRepository favoriteRepository,
                         ZoneDistanceRepository zoneDistanceRepository,
                         UserPreferenceRepository userPreferenceRepository,
                         UserRepository userRepository,
                         EntityManager entityManager) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.searchLogRepository = searchLogRepository;
        this.browsingHistoryRepository = browsingHistoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.zoneDistanceRepository = zoneDistanceRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    public Page<InventoryItemEntity> search(String keyword, Long categoryId, Long departmentId,
                                             Long userZoneId, String sortBy,
                                             boolean personalizationEnabled, Long userId,
                                             Pageable pageable) {
        return search(keyword, categoryId, departmentId, userZoneId, sortBy,
                personalizationEnabled, userId, pageable, null, null, null, null);
    }

    public Page<InventoryItemEntity> search(String keyword, Long categoryId, Long departmentId,
                                             Long userZoneId, String sortBy,
                                             boolean personalizationEnabled, Long userId,
                                             Pageable pageable,
                                             BigDecimal priceMin, BigDecimal priceMax,
                                             ItemCondition condition, Long zoneId) {

        // Enforce privacy toggle: if user has disabled personalization in preferences, override
        if (personalizationEnabled && userId != null) {
            Optional<UserPreferenceEntity> prefOpt = userPreferenceRepository.findByUserId(userId);
            if (prefOpt.isPresent() && Boolean.FALSE.equals(prefOpt.get().getPersonalizationEnabled())) {
                personalizationEnabled = false;
                log.debug("Personalization disabled by user preference for user={}", userId);
            }
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<InventoryItemEntity> countRoot = countQuery.from(InventoryItemEntity.class);
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, keyword, categoryId, departmentId,
                priceMin, priceMax, condition, zoneId);
        countQuery.select(cb.count(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        long totalCount = entityManager.createQuery(countQuery).getSingleResult();

        // Data query
        CriteriaQuery<InventoryItemEntity> dataQuery = cb.createQuery(InventoryItemEntity.class);
        Root<InventoryItemEntity> root = dataQuery.from(InventoryItemEntity.class);
        List<Predicate> predicates = buildPredicates(cb, root, keyword, categoryId, departmentId,
                priceMin, priceMax, condition, zoneId);
        dataQuery.where(predicates.toArray(new Predicate[0]));

        // Apply sorting
        if (sortBy != null && !sortBy.isBlank()) {
            switch (sortBy.toLowerCase()) {
                case "newest" -> dataQuery.orderBy(cb.desc(root.get("createdAt")));
                case "price_asc" -> dataQuery.orderBy(cb.asc(root.get("priceUsd")));
                case "price_desc" -> dataQuery.orderBy(cb.desc(root.get("priceUsd")));
                case "popularity" -> dataQuery.orderBy(cb.desc(root.get("popularityScore")));
                case "distance" -> {} // distance sort requires global ordering — handled below
                default -> dataQuery.orderBy(cb.desc(root.get("createdAt")));
            }
        } else {
            dataQuery.orderBy(cb.desc(root.get("createdAt")));
        }

        TypedQuery<InventoryItemEntity> typedQuery = entityManager.createQuery(dataQuery);

        // Distance and personalization sorts both require GLOBAL ordering before pagination,
        // so fetch the full result set in those cases. Plain sorts paginate in-query.
        boolean needsGlobalSort = ("distance".equalsIgnoreCase(sortBy) && userZoneId != null)
                || (personalizationEnabled && userId != null);

        List<InventoryItemEntity> items;
        if (needsGlobalSort) {
            items = new ArrayList<>(typedQuery.getResultList());

            if ("distance".equalsIgnoreCase(sortBy) && userZoneId != null) {
                final Long homeZone = userZoneId;
                items.sort((a, b) -> {
                    BigDecimal distA = getZoneDistance(homeZone, a.getLocationId() != null ? getZoneIdForLocation(a) : null);
                    BigDecimal distB = getZoneDistance(homeZone, b.getLocationId() != null ? getZoneIdForLocation(b) : null);
                    return distA.compareTo(distB);
                });
            }

            // Personalization: apply AFTER distance ordering (if both apply) so favorites,
            // last-30 viewed items, and recently-browsed categories bubble within the
            // globally-sorted set — still pre-pagination.
            if (personalizationEnabled && userId != null) {
                Set<Long> favoriteItemIds = favoriteRepository.findByUserId(userId).stream()
                        .map(FavoriteEntity::getItemId)
                        .collect(Collectors.toSet());

                List<Long> recentCategoryIds = browsingHistoryRepository.findRecentCategoryIdsByUserId(userId);
                Set<Long> recentCategorySet = recentCategoryIds.stream().collect(Collectors.toSet());

                // Last-30 browsed items, ordered most-recent first. Build a recency-weighted
                // repeat-interaction score so items the user viewed recently / often rank higher.
                List<Long> recentItemIds = browsingHistoryRepository.findRecentItemIdsByUserId(userId);
                Map<Long, Integer> recentItemScore = computeRecentItemScore(recentItemIds);

                items.sort((a, b) -> {
                    int scoreA = computePersonalizationScore(a, favoriteItemIds, recentCategorySet, recentItemScore);
                    int scoreB = computePersonalizationScore(b, favoriteItemIds, recentCategorySet, recentItemScore);
                    return Integer.compare(scoreB, scoreA); // higher score first
                });
            }

            // Manual pagination after global sort
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), items.size());
            items = (start < items.size()) ? items.subList(start, end) : java.util.Collections.emptyList();
        } else {
            // Simple sorts: paginate in query
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
            items = new ArrayList<>(typedQuery.getResultList());
        }

        // Log the search — schema enforces query_text NOT NULL, so normalize the keyword
        // to an empty string when the caller omitted `q`. "" cleanly represents a
        // filter-only search in the log without violating the constraint.
        SearchLogEntity searchLog = new SearchLogEntity();
        searchLog.setUserId(userId);
        searchLog.setQueryText(keyword != null ? keyword : "");
        searchLog.setFiltersJson(buildFiltersJson(categoryId, departmentId, sortBy));
        searchLog.setResultCount(items.size());
        searchLog.setSearchedAt(Instant.now());
        searchLogRepository.save(searchLog);

        return new PageImpl<>(items, pageable, totalCount);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<InventoryItemEntity> root,
                                             String keyword, Long categoryId, Long departmentId,
                                             BigDecimal priceMin, BigDecimal priceMax,
                                             ItemCondition condition, Long zoneId) {
        List<Predicate> predicates = new ArrayList<>();

        // Only active items
        predicates.add(cb.equal(root.get("isActive"), true));

        if (keyword != null && !keyword.isBlank()) {
            // Tokenized keyword matching: split query into tokens and match each
            // In MySQL production, the FULLTEXT index on (name, description) provides
            // native tokenized matching via InventoryItemRepository.searchFulltext().
            // The Criteria API path uses token-based LIKE matching as a fallback
            // that works across all databases (including H2 in tests).
            String[] tokens = keyword.trim().toLowerCase().split("\\s+");
            List<Predicate> tokenPredicates = new ArrayList<>();
            for (String token : tokens) {
                String sanitized = token.replaceAll("[^a-zA-Z0-9]", "");
                if (!sanitized.isEmpty()) {
                    String pattern = "%" + sanitized + "%";
                    Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                    Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                    tokenPredicates.add(cb.or(nameLike, descLike));
                }
            }
            if (!tokenPredicates.isEmpty()) {
                // All tokens must match (AND semantics)
                predicates.add(cb.and(tokenPredicates.toArray(new Predicate[0])));
            }
        }

        if (categoryId != null) {
            predicates.add(cb.equal(root.get("categoryId"), categoryId));
        }

        if (departmentId != null) {
            predicates.add(cb.equal(root.get("departmentId"), departmentId));
        }

        if (priceMin != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("priceUsd"), priceMin));
        }

        if (priceMax != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("priceUsd"), priceMax));
        }

        if (condition != null) {
            predicates.add(cb.equal(root.get("itemCondition"), condition));
        }

        if (zoneId != null) {
            Join<InventoryItemEntity, StorageLocationEntity> locationJoin = root.join("location", JoinType.INNER);
            predicates.add(cb.equal(locationJoin.get("zoneId"), zoneId));
        }

        return predicates;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrendingTerms() {
        List<Object[]> rawResults = searchLogRepository.findTrendingTerms(10);
        List<Map<String, Object>> trending = new ArrayList<>();
        for (Object[] row : rawResults) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("term", row[0]);
            entry.put("count", row[1]);
            trending.add(entry);
        }
        return trending;
    }

    @Transactional(readOnly = true)
    public Page<SearchLogEntity> getSearchHistory(Long userId, Pageable pageable) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return searchLogRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable);
    }

    public void recordBrowse(Long userId, Long itemId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(itemId, "Item ID must not be null");

        BrowsingHistoryEntity entry = new BrowsingHistoryEntity();
        entry.setUserId(userId);
        entry.setItemId(itemId);
        entry.setViewedAt(Instant.now());
        browsingHistoryRepository.save(entry);

        // Trim to last 30 entries per user
        long count = browsingHistoryRepository.countByUserId(userId);
        if (count > 30) {
            Page<BrowsingHistoryEntity> allHistory = browsingHistoryRepository
                    .findByUserIdOrderByViewedAtDesc(userId, PageRequest.of(0, (int) count));
            List<BrowsingHistoryEntity> historyList = allHistory.getContent();
            if (historyList.size() > 30) {
                List<BrowsingHistoryEntity> toDelete = historyList.subList(30, historyList.size());
                browsingHistoryRepository.deleteAll(toDelete);
            }
        }

        log.debug("Browsing history recorded for user={}, item={}", userId, itemId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteEntity> getFavorites(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return favoriteRepository.findByUserId(userId);
    }

    public FavoriteEntity addFavorite(Long userId, Long itemId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(itemId, "Item ID must not be null");

        if (favoriteRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new ConflictException("Item is already a favorite");
        }

        if (!inventoryItemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("InventoryItem", itemId);
        }

        FavoriteEntity favorite = new FavoriteEntity();
        favorite.setUserId(userId);
        favorite.setItemId(itemId);
        favorite.setCreatedAt(Instant.now());
        FavoriteEntity saved = favoriteRepository.save(favorite);

        log.info("Favorite added for user={}, item={}", userId, itemId);
        return saved;
    }

    public void removeFavorite(Long userId, Long itemId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(itemId, "Item ID must not be null");

        if (!favoriteRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new ResourceNotFoundException("Favorite not found for user " + userId + " and item " + itemId);
        }

        favoriteRepository.deleteByUserIdAndItemId(userId, itemId);
        log.info("Favorite removed for user={}, item={}", userId, itemId);
    }

    private BigDecimal getZoneDistance(Long fromZoneId, Long toZoneId) {
        if (fromZoneId == null || toZoneId == null) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        if (fromZoneId.equals(toZoneId)) {
            return BigDecimal.ZERO;
        }
        return zoneDistanceRepository.findDistanceWeight(fromZoneId, toZoneId)
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
    }

    private Long getZoneIdForLocation(InventoryItemEntity item) {
        if (item.getLocation() != null) {
            return item.getLocation().getZoneId();
        }
        return null;
    }

    private int computePersonalizationScore(InventoryItemEntity item, Set<Long> favoriteItemIds,
                                             Set<Long> recentCategoryIds) {
        return computePersonalizationScore(item, favoriteItemIds, recentCategoryIds, java.util.Map.of());
    }

    private int computePersonalizationScore(InventoryItemEntity item, Set<Long> favoriteItemIds,
                                             Set<Long> recentCategoryIds,
                                             Map<Long, Integer> recentItemScore) {
        int score = 0;
        if (favoriteItemIds.contains(item.getId())) {
            score += 10;
        }
        // Direct last-30-items affinity: recency + repeat interactions.
        Integer itemBoost = recentItemScore.get(item.getId());
        if (itemBoost != null) {
            score += itemBoost;
        }
        if (item.getCategoryId() != null && recentCategoryIds.contains(item.getCategoryId())) {
            score += 5;
        }
        return score;
    }

    /**
     * Convert an ordered last-30 browsed-item list (most recent first) into a
     * recency-weighted repeat-interaction score. More recent views weigh more
     * (linear decay from 30 → 1) and duplicate entries accumulate so repeated
     * views bubble up over one-off views.
     */
    private Map<Long, Integer> computeRecentItemScore(List<Long> recentItemIdsMostRecentFirst) {
        Map<Long, Integer> score = new java.util.HashMap<>();
        int n = recentItemIdsMostRecentFirst.size();
        for (int i = 0; i < n; i++) {
            Long itemId = recentItemIdsMostRecentFirst.get(i);
            if (itemId == null) continue;
            int weight = n - i; // 30 for most recent, 1 for oldest of the window
            score.merge(itemId, weight, Integer::sum);
        }
        return score;
    }

    private String buildFiltersJson(Long categoryId, Long departmentId, String sortBy) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (categoryId != null) {
            sb.append("\"categoryId\":").append(categoryId);
            first = false;
        }
        if (departmentId != null) {
            if (!first) sb.append(",");
            sb.append("\"departmentId\":").append(departmentId);
            first = false;
        }
        if (sortBy != null) {
            if (!first) sb.append(",");
            String sanitizedSort = sortBy.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"sortBy\":\"").append(sanitizedSort).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}

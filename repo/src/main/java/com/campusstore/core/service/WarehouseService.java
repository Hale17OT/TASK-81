package com.campusstore.core.service;

import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.ABCClassification;
import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.PathCostConfigEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.PathCostConfigRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final StorageLocationRepository storageLocationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PickTaskRepository pickTaskRepository;
    private final PathCostConfigRepository pathCostConfigRepository;
    private final ZoneRepository zoneRepository;
    private final AuditService auditService;

    public WarehouseService(StorageLocationRepository storageLocationRepository,
                            InventoryItemRepository inventoryItemRepository,
                            PickTaskRepository pickTaskRepository,
                            PathCostConfigRepository pathCostConfigRepository,
                            ZoneRepository zoneRepository,
                            AuditService auditService) {
        this.storageLocationRepository = storageLocationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.pickTaskRepository = pickTaskRepository;
        this.pathCostConfigRepository = pathCostConfigRepository;
        this.zoneRepository = zoneRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StorageLocationEntity> recommendPutaway(Long itemId) {
        Objects.requireNonNull(itemId, "Item ID must not be null");

        InventoryItemEntity item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemId));

        // Get item attributes
        TemperatureZone requiredTemp = null;
        SecurityLevel requiredSecurity = null;
        ABCClassification abcClass = item.getAbcClassification();

        if (item.getLocation() != null) {
            requiredTemp = item.getLocation().getTemperatureZone();
            requiredSecurity = item.getLocation().getSecurityLevel();
        }

        // Query available locations with capacity
        List<StorageLocationEntity> availableLocations = storageLocationRepository.findLocationsWithAvailableCapacity();

        // Filter by temperature zone and security level if specified
        final TemperatureZone finalTemp = requiredTemp;
        final SecurityLevel finalSecurity = requiredSecurity;

        List<StorageLocationEntity> filtered = availableLocations.stream()
                .filter(loc -> {
                    if (finalTemp != null && loc.getTemperatureZone() != null && loc.getTemperatureZone() != finalTemp) {
                        return false;
                    }
                    if (finalSecurity != null && loc.getSecurityLevel() != null
                            && loc.getSecurityLevel().ordinal() < finalSecurity.ordinal()) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Sort by: ABC match (A items -> level=0 easily accessible), then by remaining capacity
        final ABCClassification finalAbcClass = abcClass;
        filtered.sort((a, b) -> {
            // ABC match scoring: A items prefer level "0" or lower levels
            int abcScoreA = computeAbcLocationScore(a, finalAbcClass);
            int abcScoreB = computeAbcLocationScore(b, finalAbcClass);

            if (abcScoreA != abcScoreB) {
                return Integer.compare(abcScoreB, abcScoreA); // higher score first
            }

            // Then by remaining capacity (more capacity first)
            int capA = (a.getCapacity() != null ? a.getCapacity() : 0) - (a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0);
            int capB = (b.getCapacity() != null ? b.getCapacity() : 0) - (b.getCurrentOccupancy() != null ? b.getCurrentOccupancy() : 0);
            return Integer.compare(capB, capA);
        });

        log.info("Putaway recommendations generated for item id={}: {} locations", itemId, filtered.size());
        return filtered;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generatePickPath(List<Long> pickTaskIds) {
        Objects.requireNonNull(pickTaskIds, "Pick task IDs must not be null");

        if (pickTaskIds.isEmpty()) {
            return Map.of("steps", List.of(), "sequence", List.of(), "totalCost", BigDecimal.ZERO);
        }

        // Get all pick task locations with coordinates
        List<PickTaskEntity> pickTasks = new ArrayList<>();
        for (Long taskId : pickTaskIds) {
            pickTaskRepository.findById(taskId).ifPresent(pickTasks::add);
        }

        if (pickTasks.isEmpty()) {
            return Map.of("steps", List.of(), "sequence", List.of(), "totalCost", BigDecimal.ZERO);
        }

        // Active PathCostConfig drives full matrix (horizontal/vertical/zone transition).
        PathCostMatrix matrix = loadActiveMatrix();

        // Prepare location+expiration data from pick tasks
        List<PickLocationNode> nodes = new ArrayList<>();
        for (PickTaskEntity task : pickTasks) {
            StorageLocationEntity location = task.getLocation();
            if (location != null) {
                double x = location.getXCoord() != null ? location.getXCoord().doubleValue() : 0.0;
                double y = location.getYCoord() != null ? location.getYCoord().doubleValue() : 0.0;
                int level = parseLevel(location.getLevel());
                Long zoneId = location.getZone() != null ? location.getZone().getId() : null;

                LocalDate expirationDate = null;
                Instant itemCreated = null;
                try {
                    var request = task.getRequest();
                    if (request != null && request.getItem() != null) {
                        expirationDate = request.getItem().getExpirationDate();
                        itemCreated = request.getItem().getCreatedAt();
                    }
                } catch (Exception ignore) { /* best-effort; missing associations OK */ }

                nodes.add(new PickLocationNode(task.getId(), location.getId(), x, y, level, zoneId,
                        location.getName(), expirationDate, itemCreated));
            }
        }

        if (nodes.isEmpty()) {
            return Map.of("steps", List.of(), "sequence", List.of(), "totalCost", BigDecimal.ZERO);
        }

        // FIFO / expiration policy: pick items that expire sooner first. Bucket by urgency,
        // so items expiring within 7 days are always picked before items expiring later,
        // even if a slightly closer non-urgent location exists. Within a bucket we still
        // minimize travel via nearest-neighbor with full-matrix cost.
        for (PickLocationNode node : nodes) {
            node.urgencyBucket = urgencyBucket(node.expirationDate);
        }

        List<PickLocationNode> sequence = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean[] visited = new boolean[nodes.size()];
        double currentX = 0.0, currentY = 0.0;
        int currentLevel = 0;
        Long currentZoneId = null;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (int step = 0; step < nodes.size(); step++) {
            int currentBucket = lowestRemainingBucket(nodes, visited);
            if (currentBucket == Integer.MAX_VALUE) break;

            double minCost = Double.MAX_VALUE;
            int nearestIdx = -1;
            LocalDate bestExpiry = null;
            Instant bestCreated = null;

            for (int i = 0; i < nodes.size(); i++) {
                if (visited[i]) continue;
                PickLocationNode cand = nodes.get(i);
                if (cand.urgencyBucket != currentBucket) continue; // strict bucket priority

                double cost = matrix.cost(currentX, currentY, currentLevel, currentZoneId,
                        cand.x, cand.y, cand.level, cand.zoneId);
                boolean closer = cost < minCost;
                // Tie-break: sooner expiration, then earlier createdAt (FIFO).
                // Compare LocalDate by value — two different instances holding the
                // same date must still match so the createdAt FIFO rule fires.
                boolean tie = Math.abs(cost - minCost) < 1e-9;
                boolean fifoBetter = tie && (
                        isExpirationSooner(cand.expirationDate, bestExpiry)
                        || (Objects.equals(cand.expirationDate, bestExpiry)
                                && isEarlier(cand.itemCreatedAt, bestCreated))
                );
                if (closer || fifoBetter) {
                    minCost = cost;
                    nearestIdx = i;
                    bestExpiry = cand.expirationDate;
                    bestCreated = cand.itemCreatedAt;
                }
            }

            if (nearestIdx == -1) break;

            visited[nearestIdx] = true;
            PickLocationNode node = nodes.get(nearestIdx);
            sequence.add(node);

            BigDecimal stepCost = BigDecimal.valueOf(minCost).setScale(4, RoundingMode.HALF_UP);
            totalCost = totalCost.add(stepCost);

            Map<String, Object> stepInfo = new LinkedHashMap<>();
            stepInfo.put("order", step + 1);
            stepInfo.put("pickTaskId", node.pickTaskId);
            stepInfo.put("locationId", node.locationId);
            stepInfo.put("locationName", node.locationName);
            stepInfo.put("stepCost", stepCost);
            stepInfo.put("urgencyBucket", node.urgencyBucket);
            stepInfo.put("expirationDate", node.expirationDate);
            steps.add(stepInfo);

            currentX = node.x;
            currentY = node.y;
            currentLevel = node.level;
            currentZoneId = node.zoneId;
        }

        // Return to origin
        if (!sequence.isEmpty()) {
            double returnCost = matrix.cost(currentX, currentY, currentLevel, currentZoneId,
                    0, 0, 0, null);
            BigDecimal returnCostBd = BigDecimal.valueOf(returnCost).setScale(4, RoundingMode.HALF_UP);
            totalCost = totalCost.add(returnCostBd);

            Map<String, Object> returnStep = new LinkedHashMap<>();
            returnStep.put("order", sequence.size() + 1);
            returnStep.put("pickTaskId", null);
            returnStep.put("locationName", "ORIGIN");
            returnStep.put("stepCost", returnCostBd);
            steps.add(returnStep);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // UI consumes `steps`; keep `sequence` as backward-compatible alias.
        result.put("steps", steps);
        result.put("sequence", steps);
        result.put("totalCost", totalCost.setScale(4, RoundingMode.HALF_UP));

        log.info("Pick path generated with {} stops, totalCost={}", sequence.size(), totalCost);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runSimulation(String proposedConfigJson) {
        log.info("Starting shadow picking simulation");

        // Load last 1000 completed pick tasks
        List<PickTaskEntity> completedTasks = pickTaskRepository.findByStatusIn(
                List.of(PickStatus.COMPLETED));

        // Limit to last 1000
        if (completedTasks.size() > 1000) {
            completedTasks = completedTasks.subList(completedTasks.size() - 1000, completedTasks.size());
        }

        if (completedTasks.isEmpty()) {
            // Keep response schema identical to the populated branch so UI rendering is
            // key-parity safe on fresh deployments with no completed picks.
            Map<String, Object> emptyReport = new LinkedHashMap<>();
            emptyReport.put("orderCount", 0);
            emptyReport.put("avgStepsSaved", 0.0);
            emptyReport.put("totalCostSaved", 0.0);
            emptyReport.put("totalTimeSaved", 0.0);
            emptyReport.put("proposedVsActualRatio", 1.0);
            emptyReport.put("improvements", List.of());
            return emptyReport;
        }

        // Baseline (currently active) matrix — what today's picks actually cost.
        PathCostMatrix currentMatrix = loadActiveMatrix();

        // Proposed matrix — overlay any coefficients the caller supplied on top of the
        // active matrix. Missing keys fall back to the active config, so a caller can
        // vary just levelMultiplier without re-specifying horizontal/zone weights.
        double proposedHorizontal = currentMatrix.horizontalWeight;
        double proposedVertical = currentMatrix.verticalWeight;
        double proposedZone = currentMatrix.zoneTransitionWeight;
        if (proposedConfigJson != null && !proposedConfigJson.isBlank()) {
            Double h = extractJsonNumber(proposedConfigJson, "horizontalWeight");
            if (h != null) proposedHorizontal = h;
            Double lm = extractJsonNumber(proposedConfigJson, "levelMultiplier");
            Double vw = extractJsonNumber(proposedConfigJson, "verticalWeight");
            if (vw != null) proposedVertical = vw;
            else if (lm != null) proposedVertical = lm; // alias
            Double z = extractJsonNumber(proposedConfigJson, "zoneTransitionWeight");
            if (z != null) proposedZone = z;
        }
        PathCostMatrix proposedMatrix = new PathCostMatrix(
                proposedHorizontal, proposedVertical, proposedZone);

        // Group completed tasks by request to form orders
        Map<Long, List<PickTaskEntity>> orderGroups = completedTasks.stream()
                .filter(t -> t.getRequestId() != null)
                .collect(Collectors.groupingBy(PickTaskEntity::getRequestId));

        double totalActualCost = 0.0;
        double totalProposedCost = 0.0;
        List<Map<String, Object>> improvements = new ArrayList<>();

        for (Map.Entry<Long, List<PickTaskEntity>> entry : orderGroups.entrySet()) {
            List<PickTaskEntity> tasks = entry.getValue();

            // Calculate actual cost (sum of recorded path costs)
            double actualCost = tasks.stream()
                    .filter(t -> t.getPickPathCost() != null)
                    .mapToDouble(t -> t.getPickPathCost().doubleValue())
                    .sum();

            // If no recorded cost, estimate from locations using the current matrix
            if (actualCost == 0.0) {
                actualCost = estimatePathCost(tasks, currentMatrix);
            }

            // Recalculate with proposed matrix (honors horizontal/vertical/zone weights)
            double proposedCost = estimatePathCost(tasks, proposedMatrix);

            totalActualCost += actualCost;
            totalProposedCost += proposedCost;

            if (proposedCost < actualCost) {
                Map<String, Object> imp = new LinkedHashMap<>();
                imp.put("requestId", entry.getKey());
                imp.put("actualCost", BigDecimal.valueOf(actualCost).setScale(4, RoundingMode.HALF_UP));
                imp.put("proposedCost", BigDecimal.valueOf(proposedCost).setScale(4, RoundingMode.HALF_UP));
                imp.put("savings", BigDecimal.valueOf(actualCost - proposedCost).setScale(4, RoundingMode.HALF_UP));
                improvements.add(imp);
            }
        }

        int totalOrders = orderGroups.size();
        double avgStepsSaved = totalOrders > 0 ? (totalActualCost - totalProposedCost) / totalOrders : 0.0;
        double totalSaved = totalActualCost - totalProposedCost;
        double ratio = totalActualCost > 0 ? totalProposedCost / totalActualCost : 1.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("orderCount", totalOrders);
        report.put("avgStepsSaved", BigDecimal.valueOf(avgStepsSaved).setScale(4, RoundingMode.HALF_UP));
        report.put("totalCostSaved", BigDecimal.valueOf(totalSaved).setScale(4, RoundingMode.HALF_UP));
        // Retain prior key name for backward compatibility.
        report.put("totalTimeSaved", BigDecimal.valueOf(totalSaved).setScale(4, RoundingMode.HALF_UP));
        report.put("proposedVsActualRatio", BigDecimal.valueOf(ratio).setScale(4, RoundingMode.HALF_UP));
        report.put("improvements", improvements);

        log.info("Simulation completed: {} orders analyzed, ratio={}", totalOrders, ratio);
        return report;
    }

    @Transactional(readOnly = true)
    public Page<StorageLocationEntity> listLocations(Pageable pageable) {
        return storageLocationRepository.findAll(pageable);
    }

    public StorageLocationEntity createLocation(String name, Long zoneId, BigDecimal xCoord, BigDecimal yCoord,
                                                 Integer level, TemperatureZone temperatureZone,
                                                 SecurityLevel securityLevel, Integer capacity,
                                                 Long createdByUserId) {
        Objects.requireNonNull(name, "Location name must not be null");

        ZoneEntity zone = null;
        if (zoneId != null) {
            zone = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        }

        StorageLocationEntity location = new StorageLocationEntity();
        location.setName(name);
        if (zone != null) {
            location.setZone(zone);
        }
        location.setXCoord(xCoord);
        location.setYCoord(yCoord);
        location.setLevel(level);
        location.setTemperatureZone(temperatureZone);
        location.setSecurityLevel(securityLevel);
        location.setCapacity(capacity != null ? capacity : 0);
        location.setCurrentOccupancy(0);
        location.setCreatedAt(Instant.now());
        location.setUpdatedAt(Instant.now());

        StorageLocationEntity saved = storageLocationRepository.save(location);

        auditService.log(createdByUserId, "CREATE_LOCATION", "StorageLocation", saved.getId(),
                "{\"name\":\"" + name + "\"}");
        log.info("Storage location created id={}, name={}", saved.getId(), name);
        return saved;
    }

    public StorageLocationEntity updateLocation(Long locationId, String name, BigDecimal xCoord,
                                                 BigDecimal yCoord, Integer level,
                                                 Integer capacity, Long updatedByUserId) {
        Objects.requireNonNull(locationId, "Location ID must not be null");

        StorageLocationEntity location = storageLocationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("StorageLocation", locationId));

        if (name != null) {
            location.setName(name);
        }
        if (xCoord != null) {
            location.setXCoord(xCoord);
        }
        if (yCoord != null) {
            location.setYCoord(yCoord);
        }
        if (level != null) {
            location.setLevel(level);
        }
        if (capacity != null) {
            location.setCapacity(capacity);
        }
        location.setUpdatedAt(Instant.now());

        StorageLocationEntity saved = storageLocationRepository.save(location);

        auditService.log(updatedByUserId, "UPDATE_LOCATION", "StorageLocation", locationId, "{}");
        log.info("Storage location updated id={}", locationId);
        return saved;
    }

    private double calculateCost(double x1, double y1, int level1,
                                  double x2, double y2, int level2,
                                  double levelMultiplier) {
        double horizontalDist = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double verticalCost = Math.abs(level2 - level1) * levelMultiplier;
        return horizontalDist + verticalCost;
    }

    private int parseLevel(Integer level) {
        return level != null ? level : 0;
    }

    private int computeAbcLocationScore(StorageLocationEntity location, ABCClassification abcClass) {
        if (abcClass == null) return 0;

        int levelValue = parseLevel(location.getLevel());

        return switch (abcClass) {
            case A -> levelValue == 0 ? 10 : Math.max(0, 10 - levelValue * 2);
            case B -> levelValue <= 1 ? 5 : Math.max(0, 5 - levelValue);
            case C -> 0; // C items can go anywhere
        };
    }

    private double estimatePathCost(List<PickTaskEntity> tasks, double levelMultiplier) {
        // Use the active matrix with the caller-supplied level multiplier, so simulations
        // can compare different vertical-weight proposals under the same matrix shape.
        PathCostMatrix base = loadActiveMatrix();
        PathCostMatrix matrix = new PathCostMatrix(
                base.horizontalWeight, levelMultiplier, base.zoneTransitionWeight);
        return estimatePathCost(tasks, matrix);
    }

    private double estimatePathCost(List<PickTaskEntity> tasks, PathCostMatrix matrix) {
        if (tasks.isEmpty()) return 0.0;

        List<PickLocationNode> nodes = new ArrayList<>();
        for (PickTaskEntity task : tasks) {
            StorageLocationEntity loc = task.getLocation();
            if (loc != null) {
                double x = loc.getXCoord() != null ? loc.getXCoord().doubleValue() : 0.0;
                double y = loc.getYCoord() != null ? loc.getYCoord().doubleValue() : 0.0;
                int level = parseLevel(loc.getLevel());
                Long zoneId = loc.getZone() != null ? loc.getZone().getId() : null;
                LocalDate exp = null;
                Instant itemCreated = null;
                try {
                    var request = task.getRequest();
                    if (request != null && request.getItem() != null) {
                        exp = request.getItem().getExpirationDate();
                        itemCreated = request.getItem().getCreatedAt();
                    }
                } catch (Exception ignore) { /* OK */ }
                PickLocationNode node = new PickLocationNode(
                        task.getId(), loc.getId(), x, y, level, zoneId, loc.getName(), exp, itemCreated);
                node.urgencyBucket = urgencyBucket(exp);
                nodes.add(node);
            }
        }

        if (nodes.isEmpty()) return 0.0;

        // FIFO/expiration-aware nearest-neighbor, matching generatePickPath semantics so
        // the simulation report reflects the same picking strategy as production.
        boolean[] visited = new boolean[nodes.size()];
        double cx = 0, cy = 0;
        int cl = 0;
        Long cz = null;
        double totalCost = 0.0;

        for (int step = 0; step < nodes.size(); step++) {
            int bucket = lowestRemainingBucket(nodes, visited);
            if (bucket == Integer.MAX_VALUE) break;

            double minCost = Double.MAX_VALUE;
            int nearest = -1;
            for (int i = 0; i < nodes.size(); i++) {
                if (visited[i]) continue;
                PickLocationNode n = nodes.get(i);
                if (n.urgencyBucket != bucket) continue;
                double cost = matrix.cost(cx, cy, cl, cz, n.x, n.y, n.level, n.zoneId);
                if (cost < minCost) {
                    minCost = cost;
                    nearest = i;
                }
            }
            if (nearest == -1) break;
            visited[nearest] = true;
            totalCost += minCost;
            cx = nodes.get(nearest).x;
            cy = nodes.get(nearest).y;
            cl = nodes.get(nearest).level;
            cz = nodes.get(nearest).zoneId;
        }

        // Return to origin
        totalCost += matrix.cost(cx, cy, cl, cz, 0, 0, 0, null);
        return totalCost;
    }

    /**
     * Mutable pick-list node with FIFO/expiration context and zone linkage so the cost
     * matrix can compute zone-transition penalties.
     */
    private static final class PickLocationNode {
        final Long pickTaskId;
        final Long locationId;
        final double x;
        final double y;
        final int level;
        final Long zoneId;
        final String locationName;
        final LocalDate expirationDate;
        final Instant itemCreatedAt;
        int urgencyBucket;

        PickLocationNode(Long pickTaskId, Long locationId, double x, double y, int level,
                          String locationName) {
            this(pickTaskId, locationId, x, y, level, null, locationName, null, null);
        }

        PickLocationNode(Long pickTaskId, Long locationId, double x, double y, int level,
                          Long zoneId, String locationName,
                          LocalDate expirationDate, Instant itemCreatedAt) {
            this.pickTaskId = pickTaskId;
            this.locationId = locationId;
            this.x = x;
            this.y = y;
            this.level = level;
            this.zoneId = zoneId;
            this.locationName = locationName;
            this.expirationDate = expirationDate;
            this.itemCreatedAt = itemCreatedAt;
        }
    }

    /**
     * Configurable path-cost matrix parsed from {@code path_cost_config.config_json}.
     * Supports: {@code horizontalWeight} (default 1.0), {@code verticalWeight} (default = entity
     * level_multiplier), {@code zoneTransitionWeight} (default 0.0). Missing JSON falls back to
     * the scalar {@code level_multiplier} only, preserving legacy behavior.
     */
    private static final class PathCostMatrix {
        final double horizontalWeight;
        final double verticalWeight;
        final double zoneTransitionWeight;

        PathCostMatrix(double horizontalWeight, double verticalWeight, double zoneTransitionWeight) {
            this.horizontalWeight = horizontalWeight;
            this.verticalWeight = verticalWeight;
            this.zoneTransitionWeight = zoneTransitionWeight;
        }

        double cost(double x1, double y1, int level1, Long zone1,
                    double x2, double y2, int level2, Long zone2) {
            double horizontal = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2))
                    * horizontalWeight;
            double vertical = Math.abs(level2 - level1) * verticalWeight;
            double zonePenalty = 0.0;
            if (zone1 != null && zone2 != null && !zone1.equals(zone2)) {
                zonePenalty = zoneTransitionWeight;
            }
            return horizontal + vertical + zonePenalty;
        }
    }

    private PathCostMatrix loadActiveMatrix() {
        double horizontal = 1.0;
        double vertical = 2.0;
        double zoneTransition = 0.0;

        List<PathCostConfigEntity> configs = pathCostConfigRepository.findByIsActiveTrue();
        if (!configs.isEmpty()) {
            PathCostConfigEntity cfg = configs.get(0);
            if (cfg.getLevelMultiplier() != null) {
                vertical = cfg.getLevelMultiplier().doubleValue();
            }
            String json = cfg.getConfigJson();
            if (json != null && !json.isBlank()) {
                Double h = extractJsonNumber(json, "horizontalWeight");
                if (h != null) horizontal = h;
                Double v = extractJsonNumber(json, "verticalWeight");
                if (v != null) vertical = v;
                Double z = extractJsonNumber(json, "zoneTransitionWeight");
                if (z != null) zoneTransition = z;
            }
        }
        return new PathCostMatrix(horizontal, vertical, zoneTransition);
    }

    /** Lightweight extractor — avoids a Jackson dependency for a handful of scalar keys. */
    private static Double extractJsonNumber(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]*\\.?[0-9]+)")
                .matcher(json);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); }
            catch (NumberFormatException ignore) { return null; }
        }
        return null;
    }

    /**
     * Urgency bucket for FIFO/expiration picking priority. Lower bucket is picked first.
     * 0 = expired / expires within 7 days, 1 = 8–30 days, 2 = 31+ days, 3 = no expiry (non-perishable).
     */
    private static int urgencyBucket(LocalDate expirationDate) {
        if (expirationDate == null) return 3;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
        if (days <= 7) return 0;
        if (days <= 30) return 1;
        return 2;
    }

    private static int lowestRemainingBucket(List<PickLocationNode> nodes, boolean[] visited) {
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            if (visited[i]) continue;
            if (nodes.get(i).urgencyBucket < lowest) lowest = nodes.get(i).urgencyBucket;
        }
        return lowest;
    }

    private static boolean isExpirationSooner(LocalDate a, LocalDate b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isBefore(b);
    }

    private static boolean isEarlier(Instant a, Instant b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isBefore(b);
    }
}

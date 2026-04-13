package com.campusstore.unit.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for the warehouse pick-path cost calculation and putaway ranking algorithms.
 * These algorithms will be implemented in WarehouseService but the math is tested here
 * in isolation to ensure correctness of the core logic.
 */
@Tag("unit")
class WarehouseAlgorithmTest {

    // ── Helper types to model locations for algorithm testing ──────────

    record Location(long id, double x, double y, int level, String name) {}

    // ── Linear distance (Euclidean) between two 2D points ─────────────

    private double linearDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private double levelCost(int level1, int level2, double multiplier) {
        return Math.abs(level2 - level1) * multiplier;
    }

    private double totalCost(double x1, double y1, int level1,
                             double x2, double y2, int level2,
                             double levelMultiplier) {
        return linearDistance(x1, y1, x2, y2) + levelCost(level1, level2, levelMultiplier);
    }

    // ── Nearest-neighbor path ordering ─────────────────────────────────

    /**
     * Greedy nearest-neighbor algorithm: starting from start location,
     * always visit the closest unvisited location next.
     */
    private List<Location> nearestNeighborPath(Location start, List<Location> locations,
                                                double levelMultiplier) {
        List<Location> unvisited = new ArrayList<>(locations);
        List<Location> path = new ArrayList<>();
        Location current = start;

        while (!unvisited.isEmpty()) {
            Location nearest = null;
            double nearestCost = Double.MAX_VALUE;

            for (Location loc : unvisited) {
                double cost = totalCost(current.x, current.y, current.level,
                        loc.x, loc.y, loc.level, levelMultiplier);
                if (cost < nearestCost) {
                    nearestCost = cost;
                    nearest = loc;
                }
            }

            path.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }

        return path;
    }

    /**
     * Compute total path cost along an ordered sequence of locations.
     */
    private double computePathCost(Location start, List<Location> orderedStops,
                                    double levelMultiplier) {
        double total = 0;
        Location current = start;
        for (Location stop : orderedStops) {
            total += totalCost(current.x, current.y, current.level,
                    stop.x, stop.y, stop.level, levelMultiplier);
            current = stop;
        }
        return total;
    }

    // ── Putaway ranking: prefer locations with lower travel cost, same ──
    //    zone constraints, and available capacity ─────────────────────────

    record PutawayCandidate(long locationId, double travelCost, int availableCapacity,
                            boolean matchesZone) {}

    /**
     * Rank putaway candidates: matching zone first, then by travel cost ascending.
     */
    private List<PutawayCandidate> rankPutawayCandidates(List<PutawayCandidate> candidates) {
        return candidates.stream()
                .filter(c -> c.availableCapacity > 0)
                .sorted((a, b) -> {
                    // Zone match is top priority
                    if (a.matchesZone != b.matchesZone) {
                        return a.matchesZone ? -1 : 1;
                    }
                    // Then sort by travel cost
                    return Double.compare(a.travelCost, b.travelCost);
                })
                .toList();
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void linearDistance_samePoint_returnsZero() {
        double dist = linearDistance(3.0, 4.0, 3.0, 4.0);
        assertThat(dist).isEqualTo(0.0);
    }

    @Test
    void linearDistance_knownTriangle_3_4_5() {
        double dist = linearDistance(0.0, 0.0, 3.0, 4.0);
        assertThat(dist).isCloseTo(5.0, within(0.0001));
    }

    @Test
    void linearDistance_horizontalLine() {
        double dist = linearDistance(1.0, 0.0, 5.0, 0.0);
        assertThat(dist).isCloseTo(4.0, within(0.0001));
    }

    @Test
    void linearDistance_verticalLine() {
        double dist = linearDistance(0.0, 2.0, 0.0, 7.0);
        assertThat(dist).isCloseTo(5.0, within(0.0001));
    }

    @Test
    void linearDistance_negativeCoordinates() {
        double dist = linearDistance(-1.0, -1.0, 2.0, 3.0);
        assertThat(dist).isCloseTo(5.0, within(0.0001));
    }

    // ── Level cost ─────────────────────────────────────────────────────

    @Test
    void levelCost_sameLevel_returnsZero() {
        double cost = levelCost(2, 2, 1.5);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void levelCost_oneLevelDifference() {
        double cost = levelCost(1, 2, 1.5);
        assertThat(cost).isCloseTo(1.5, within(0.0001));
    }

    @Test
    void levelCost_multipleLevels_withMultiplier() {
        double cost = levelCost(1, 4, 2.0);
        assertThat(cost).isCloseTo(6.0, within(0.0001));
    }

    @Test
    void levelCost_isSymmetric() {
        double cost1 = levelCost(1, 3, 2.0);
        double cost2 = levelCost(3, 1, 2.0);
        assertThat(cost1).isEqualTo(cost2);
    }

    // ── Total cost ─────────────────────────────────────────────────────

    @Test
    void totalCost_sameLevelSamePoint_returnsZero() {
        double cost = totalCost(0, 0, 1, 0, 0, 1, 2.0);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void totalCost_combinesLinearAndLevelCosts() {
        // Linear distance: sqrt(3^2 + 4^2) = 5.0
        // Level cost: |3 - 1| * 2.0 = 4.0
        // Total: 9.0
        double cost = totalCost(0, 0, 1, 3, 4, 3, 2.0);
        assertThat(cost).isCloseTo(9.0, within(0.0001));
    }

    @Test
    void totalCost_zeroLevelMultiplier_onlyLinearDistance() {
        double cost = totalCost(0, 0, 1, 3, 4, 5, 0.0);
        assertThat(cost).isCloseTo(5.0, within(0.0001));
    }

    // ── Nearest-neighbor path ordering ─────────────────────────────────

    @Test
    void nearestNeighborPath_singleLocation_returnsThatLocation() {
        Location start = new Location(0, 0, 0, 1, "Start");
        Location a = new Location(1, 3, 4, 1, "A");

        List<Location> path = nearestNeighborPath(start, List.of(a), 1.0);

        assertThat(path).hasSize(1);
        assertThat(path.get(0).id).isEqualTo(1);
    }

    @Test
    void nearestNeighborPath_threeLocations_visitsNearestFirst() {
        Location start = new Location(0, 0, 0, 1, "Start");
        Location a = new Location(1, 10, 10, 1, "A");  // far
        Location b = new Location(2, 1, 1, 1, "B");     // close
        Location c = new Location(3, 5, 5, 1, "C");     // medium

        List<Location> path = nearestNeighborPath(start, List.of(a, b, c), 0.0);

        // B(1,1) is closest to start(0,0), then C(5,5) from B, then A(10,10) from C
        assertThat(path.get(0).id).isEqualTo(2); // B
        assertThat(path.get(1).id).isEqualTo(3); // C
        assertThat(path.get(2).id).isEqualTo(1); // A
    }

    @Test
    void nearestNeighborPath_fourLocations_levelCostAffectsOrdering() {
        Location start = new Location(0, 0, 0, 1, "Start");
        // A is close in 2D but on a different level
        Location a = new Location(1, 1, 1, 5, "A");    // dist ~1.41 + level cost 4*2=8 = ~9.41
        // B is farther in 2D but on the same level
        Location b = new Location(2, 5, 0, 1, "B");    // dist 5.0 + level cost 0 = 5.0
        Location c = new Location(3, 3, 3, 2, "C");    // dist ~4.24 + level cost 1*2=2 = ~6.24
        Location d = new Location(4, 8, 8, 1, "D");    // dist ~11.31 + level cost 0 = ~11.31

        double levelMultiplier = 2.0;
        List<Location> path = nearestNeighborPath(start, List.of(a, b, c, d), levelMultiplier);

        // B should be first (cost 5.0), then C (from B: dist ~3.6+2=~5.6),
        // then order continues with nearest
        assertThat(path.get(0).id).isEqualTo(2); // B is nearest to start
    }

    @Test
    void nearestNeighborPath_totalCostIsAccumulated() {
        Location start = new Location(0, 0, 0, 1, "Start");
        Location a = new Location(1, 3, 0, 1, "A");    // cost from start: 3.0
        Location b = new Location(2, 6, 0, 1, "B");    // cost from A: 3.0
        Location c = new Location(3, 10, 0, 1, "C");   // cost from B: 4.0

        List<Location> path = nearestNeighborPath(start, List.of(a, b, c), 0.0);
        double total = computePathCost(start, path, 0.0);

        // Total should be 3 + 3 + 4 = 10
        assertThat(total).isCloseTo(10.0, within(0.0001));
    }

    @Test
    void pathCost_withLevelChanges() {
        Location start = new Location(0, 0, 0, 1, "Start");
        Location a = new Location(1, 3, 4, 1, "A");  // dist 5.0, level 0 -> total 5.0
        Location b = new Location(2, 3, 4, 3, "B");  // dist 0.0, level 2*1.5=3 -> total 3.0

        List<Location> path = List.of(a, b);
        double total = computePathCost(start, path, 1.5);

        assertThat(total).isCloseTo(8.0, within(0.0001));
    }

    // ── Putaway ranking ────────────────────────────────────────────────

    @Test
    void putawayRanking_prefersMatchingZone() {
        PutawayCandidate matchingFar = new PutawayCandidate(1, 10.0, 5, true);
        PutawayCandidate nonMatchingClose = new PutawayCandidate(2, 2.0, 5, false);

        List<PutawayCandidate> ranked = rankPutawayCandidates(List.of(nonMatchingClose, matchingFar));

        assertThat(ranked.get(0).locationId).isEqualTo(1); // matching zone first
        assertThat(ranked.get(1).locationId).isEqualTo(2);
    }

    @Test
    void putawayRanking_withinSameZone_sortsByTravelCost() {
        PutawayCandidate far = new PutawayCandidate(1, 10.0, 5, true);
        PutawayCandidate close = new PutawayCandidate(2, 3.0, 5, true);
        PutawayCandidate medium = new PutawayCandidate(3, 6.0, 5, true);

        List<PutawayCandidate> ranked = rankPutawayCandidates(List.of(far, medium, close));

        assertThat(ranked.get(0).locationId).isEqualTo(2); // closest
        assertThat(ranked.get(1).locationId).isEqualTo(3); // medium
        assertThat(ranked.get(2).locationId).isEqualTo(1); // farthest
    }

    @Test
    void putawayRanking_excludesFullLocations() {
        PutawayCandidate full = new PutawayCandidate(1, 1.0, 0, true);    // no capacity
        PutawayCandidate available = new PutawayCandidate(2, 5.0, 3, true); // has capacity

        List<PutawayCandidate> ranked = rankPutawayCandidates(List.of(full, available));

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).locationId).isEqualTo(2);
    }

    @Test
    void putawayRanking_allFull_returnsEmpty() {
        PutawayCandidate full1 = new PutawayCandidate(1, 1.0, 0, true);
        PutawayCandidate full2 = new PutawayCandidate(2, 2.0, 0, true);

        List<PutawayCandidate> ranked = rankPutawayCandidates(List.of(full1, full2));

        assertThat(ranked).isEmpty();
    }

    @Test
    void putawayRanking_mixedZonesAndCapacities() {
        PutawayCandidate c1 = new PutawayCandidate(1, 8.0, 2, true);  // matching, far
        PutawayCandidate c2 = new PutawayCandidate(2, 3.0, 0, true);  // matching but full
        PutawayCandidate c3 = new PutawayCandidate(3, 1.0, 5, false); // non-matching, close
        PutawayCandidate c4 = new PutawayCandidate(4, 2.0, 5, true);  // matching, close

        List<PutawayCandidate> ranked = rankPutawayCandidates(List.of(c1, c2, c3, c4));

        assertThat(ranked).hasSize(3); // c2 excluded (full)
        assertThat(ranked.get(0).locationId).isEqualTo(4); // matching, closest
        assertThat(ranked.get(1).locationId).isEqualTo(1); // matching, farther
        assertThat(ranked.get(2).locationId).isEqualTo(3); // non-matching
    }
}

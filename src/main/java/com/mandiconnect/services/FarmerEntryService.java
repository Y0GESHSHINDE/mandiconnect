package com.mandiconnect.services;

import com.mandiconnect.models.CropStats;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.CropStatsRepository;
import com.mandiconnect.repositories.FarmerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerEntryService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int TREND_DAYS = 30;

    private final FarmerEntryRepository farmerEntryRepository;
    private final CropStatsRepository cropStatsRepository;

    @Transactional
    public FarmerEntry addFarmerEntry(FarmerEntry entry) {
        validateEntry(entry);

        entry.setCreatedAt(now());
        FarmerEntry savedEntry = farmerEntryRepository.save(entry);
        recalculateStatsForCropAndMarket(savedEntry.getCrop(), savedEntry.getMarket());
        return savedEntry;
    }

    @Transactional
    public void deleteFarmerEntry(FarmerEntry entry) {
        if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
            throw new IllegalArgumentException("Farmer entry is required for deletion");
        }

        Crops crop = entry.getCrop();
        Market market = entry.getMarket();

        farmerEntryRepository.delete(entry);

        if (crop != null && market != null) {
            recalculateStatsForCropAndMarket(crop, market);
        }
    }

    @Transactional
    public CropStats getStatsByCropAndMarket(Crops crop, Market market) {
        validateCropAndMarket(crop, market);
        return recalculateStatsForCropAndMarket(crop, market);
    }

    @Transactional
    public List<CropStats> getStatsByCrop(Crops crop) {
        if (crop == null || crop.getId() == null || crop.getId().isBlank()) {
            throw new IllegalArgumentException("Crop is required");
        }

        Map<String, Market> marketsById = farmerEntryRepository.findAll().stream()
                .filter(entry -> entry != null && entry.getCrop() != null && entry.getMarket() != null)
                .filter(entry -> Objects.equals(entry.getCrop().getId(), crop.getId()))
                .filter(entry -> entry.getMarket().getId() != null && !entry.getMarket().getId().isBlank())
                .collect(Collectors.toMap(
                        entry -> entry.getMarket().getId(),
                        FarmerEntry::getMarket,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (marketsById.isEmpty()) {
            return cropStatsRepository.findAllByCrop(crop);
        }

        List<CropStats> rebuiltStats = new ArrayList<>();
        for (Market market : marketsById.values()) {
            rebuiltStats.add(recalculateStatsForCropAndMarket(crop, market));
        }
        return rebuiltStats;
    }

    @Transactional
    public List<CropStats> getStatsByMarket(Market market) {
        if (market == null || market.getId() == null || market.getId().isBlank()) {
            throw new IllegalArgumentException("Market is required");
        }

        Map<String, Crops> cropsById = farmerEntryRepository.findAll().stream()
                .filter(entry -> entry != null && entry.getCrop() != null && entry.getMarket() != null)
                .filter(entry -> Objects.equals(entry.getMarket().getId(), market.getId()))
                .filter(entry -> entry.getCrop().getId() != null && !entry.getCrop().getId().isBlank())
                .collect(Collectors.toMap(
                        entry -> entry.getCrop().getId(),
                        FarmerEntry::getCrop,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (cropsById.isEmpty()) {
            return cropStatsRepository.findAllByMandi(market);
        }

        List<CropStats> rebuiltStats = new ArrayList<>();
        for (Crops crop : cropsById.values()) {
            rebuiltStats.add(recalculateStatsForCropAndMarket(crop, market));
        }
        return rebuiltStats;
    }

    @Transactional
    public Map<String, Object> rebuildAllStats() {
        List<FarmerEntry> allEntries = farmerEntryRepository.findAll();
        cropStatsRepository.deleteAll();

        Map<String, CropMarketRef> uniquePairs = new LinkedHashMap<>();
        for (FarmerEntry entry : allEntries) {
            if (entry == null || entry.getCrop() == null || entry.getMarket() == null) {
                continue;
            }

            String cropId = entry.getCrop().getId();
            String marketId = entry.getMarket().getId();
            if (cropId == null || cropId.isBlank() || marketId == null || marketId.isBlank()) {
                continue;
            }

            uniquePairs.putIfAbsent(cropId + ":" + marketId, new CropMarketRef(entry.getCrop(), entry.getMarket()));
        }

        int rebuiltCount = 0;
        for (CropMarketRef pair : uniquePairs.values()) {
            recalculateStatsForCropAndMarket(pair.crop(), pair.market());
            rebuiltCount++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("entriesScanned", allEntries.size());
        response.put("statsRebuilt", rebuiltCount);
        response.put("rebuiltAt", now());
        return response;
    }

    @Transactional
    public CropStats recalculateStatsForCropAndMarket(Crops crop, Market market) {
        validateCropAndMarket(crop, market);

        List<FarmerEntry> allEntries = farmerEntryRepository.findByCropAndMarket(crop, market);
        CropStats cropStats = cropStatsRepository.findByCropAndMandi(crop, market)
                .orElseGet(() -> createNewCropStats(crop, market));

        LocalDateTime currentTime = now();
        LocalDateTime startOfToday = startOfToday();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
        LocalDateTime startOfSevenDayWindow = startOfToday.minusDays(6);

        List<FarmerEntry> todayEntries = filterEntriesBetween(allEntries, startOfToday, startOfTomorrow);
        List<FarmerEntry> weeklyEntries = filterEntriesBetween(allEntries, startOfSevenDayWindow, startOfTomorrow);

        cropStats.setAllTimeStats(buildStats(allEntries, currentTime));
        cropStats.setDailyStats(buildStats(todayEntries, currentTime));
        cropStats.setWeeklyStats(buildStats(weeklyEntries, currentTime));
        cropStats.setTrend(buildTrend(allEntries, startOfToday));

        CropStats savedStats = cropStatsRepository.save(cropStats);
        log.info(
                "Recalculated stats for crop {} and market {}. allTime={}, daily={}, weekly={}",
                crop.getId(),
                market.getId(),
                allEntries.size(),
                todayEntries.size(),
                weeklyEntries.size()
        );
        return savedStats;
    }

    private void validateEntry(FarmerEntry entry) {
        if (entry == null || entry.getCrop() == null || entry.getMarket() == null) {
            throw new IllegalArgumentException("Farmer entry must have crop and market information");
        }

        if (entry.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }
    }

    private void validateCropAndMarket(Crops crop, Market market) {
        if (crop == null || crop.getId() == null || crop.getId().isBlank()) {
            throw new IllegalArgumentException("Crop is required");
        }

        if (market == null || market.getId() == null || market.getId().isBlank()) {
            throw new IllegalArgumentException("Market is required");
        }
    }

    private List<FarmerEntry> filterEntriesBetween(List<FarmerEntry> entries, LocalDateTime start, LocalDateTime end) {
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getCreatedAt() != null)
                .filter(entry -> !entry.getCreatedAt().isBefore(start) && entry.getCreatedAt().isBefore(end))
                .toList();
    }

    private CropStats.Stats buildStats(List<FarmerEntry> entries, LocalDateTime updatedAt) {
        if (entries == null || entries.isEmpty()) {
            return CropStats.Stats.builder()
                    .averagePrice(0)
                    .minPrice(0)
                    .maxPrice(0)
                    .totalFarmersContributed(0)
                    .lastUpdated(updatedAt)
                    .isStale(true)
                    .build();
        }

        double sum = entries.stream().mapToDouble(FarmerEntry::getPrice).sum();
        double min = entries.stream().mapToDouble(FarmerEntry::getPrice).min().orElse(0);
        double max = entries.stream().mapToDouble(FarmerEntry::getPrice).max().orElse(0);
        double average = sum / entries.size();

        if (Double.isNaN(average) || Double.isInfinite(average)) {
            average = 0;
        }

        return CropStats.Stats.builder()
                .averagePrice(roundPrice(average))
                .minPrice(roundPrice(min))
                .maxPrice(roundPrice(max))
                .totalFarmersContributed(countUniqueFarmers(entries))
                .lastUpdated(updatedAt)
                .isStale(false)
                .build();
    }

    private List<CropStats.Trend> buildTrend(List<FarmerEntry> entries, LocalDateTime startOfToday) {
        LocalDate startDate = startOfToday.toLocalDate().minusDays(TREND_DAYS - 1L);
        LocalDate endDate = startOfToday.toLocalDate();

        Map<LocalDate, List<FarmerEntry>> entriesByDate = entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getCreatedAt() != null)
                .collect(Collectors.groupingBy(entry -> entry.getCreatedAt().toLocalDate()));

        List<CropStats.Trend> trend = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            List<FarmerEntry> dayEntries = entriesByDate.getOrDefault(day, List.of());
            trend.add(buildTrendPoint(day.atStartOfDay(), dayEntries));
        }
        return trend;
    }

    private CropStats.Trend buildTrendPoint(LocalDateTime date, List<FarmerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return CropStats.Trend.builder()
                    .date(date)
                    .averagePrice(0)
                    .minPrice(0)
                    .maxPrice(0)
                    .farmersContributed(0)
                    .build();
        }

        double sum = entries.stream().mapToDouble(FarmerEntry::getPrice).sum();
        double min = entries.stream().mapToDouble(FarmerEntry::getPrice).min().orElse(0);
        double max = entries.stream().mapToDouble(FarmerEntry::getPrice).max().orElse(0);
        double average = sum / entries.size();

        if (Double.isNaN(average) || Double.isInfinite(average)) {
            average = 0;
        }

        return CropStats.Trend.builder()
                .date(date)
                .averagePrice(roundPrice(average))
                .minPrice(roundPrice(min))
                .maxPrice(roundPrice(max))
                .farmersContributed(countUniqueFarmers(entries))
                .build();
    }

    private int countUniqueFarmers(List<FarmerEntry> entries) {
        Set<String> farmerIds = entries.stream()
                .map(FarmerEntry::getFarmer)
                .filter(Objects::nonNull)
                .map(farmer -> farmer.getId())
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        return farmerIds.size();
    }

    private double roundPrice(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private CropStats createNewCropStats(Crops crop, Market market) {
        return CropStats.builder()
                .crop(crop)
                .mandi(market)
                .allTimeStats(buildStats(List.of(), now()))
                .dailyStats(buildStats(List.of(), now()))
                .weeklyStats(buildStats(List.of(), now()))
                .trend(new ArrayList<>())
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(IST_ZONE);
    }

    private LocalDateTime startOfToday() {
        return LocalDate.now(IST_ZONE).atStartOfDay();
    }

    private record CropMarketRef(Crops crop, Market market) {
    }
}

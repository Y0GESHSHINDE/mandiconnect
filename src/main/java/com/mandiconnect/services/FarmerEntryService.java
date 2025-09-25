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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerEntryService {

    private final FarmerEntryRepository farmerEntryRepository;
    private final CropStatsRepository cropStatsRepository;

    @Transactional
    public FarmerEntry addFarmerEntry(FarmerEntry entry) {
        // Validate and save entry
        if (entry == null || entry.getCrop() == null || entry.getMarket() == null) {
            throw new IllegalArgumentException("Farmer entry must have crop and market information");
        }

        if (entry.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }

        FarmerEntry savedEntry = farmerEntryRepository.save(entry);
        updateAllStatsTypes(savedEntry);
        return savedEntry;
    }

    @Transactional
    protected void updateAllStatsTypes(FarmerEntry entry) {
        Crops crop = entry.getCrop();
        Market mandi = entry.getMarket();

        if (crop == null || mandi == null) {
            log.error("Crop or Market reference is null in FarmerEntry: {}", entry.getId());
            return;
        }

        String cropId = crop.getId();
        String mandiId = mandi.getId();

        try {
            // Get or create CropStats - use DBRef objects for query
            CropStats cropStats = cropStatsRepository.findByCropAndMandi(crop, mandi).orElseGet(() -> createNewCropStats(crop, mandi));

            // 1. Update ALL-TIME Stats
            updateAllTimeStats(cropStats, crop, mandi, entry);

            // 2. Update DAILY Stats (last 24 hours)
            updateDailyStats(cropStats, crop, mandi, entry);

            // 3. Update WEEKLY Stats (last 7 days)
            updateWeeklyStats(cropStats, crop, mandi, entry);

            // 4. Update Trend (daily snapshot) - FIXED
            updateTrend(cropStats, crop, mandi);

            // Save updated CropStats
            cropStatsRepository.save(cropStats);
            log.info("Successfully updated all stats for crop: {} and mandi: {}", cropId, mandiId);

        } catch (Exception e) {
            log.error("Error updating stats for crop: {}, mandi: {}", cropId, mandiId, e);
            throw new RuntimeException("Failed to update statistics", e);
        }
    }

    private void updateAllTimeStats(CropStats cropStats, Crops crop, Market mandi, FarmerEntry entry) {
        List<FarmerEntry> allEntries = farmerEntryRepository.findByCropAndMarket(crop, mandi);
        updateStatsObject(cropStats.getAllTimeStats(), allEntries, "All-time");
    }

    private void updateDailyStats(CropStats cropStats, Crops crop, Market mandi, FarmerEntry entry) {
        // Last 24 hours from now
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<FarmerEntry> dailyEntries = farmerEntryRepository.findByCropAndMarketAndCreatedAtAfter(crop, mandi, twentyFourHoursAgo);

        updateStatsObject(cropStats.getDailyStats(), dailyEntries, "Daily");
    }

    private void updateWeeklyStats(CropStats cropStats, Crops crop, Market mandi, FarmerEntry entry) {
        // Last 7 days from now
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<FarmerEntry> weeklyEntries = farmerEntryRepository.findByCropAndMarketAndCreatedAtAfter(crop, mandi, sevenDaysAgo);

        updateStatsObject(cropStats.getWeeklyStats(), weeklyEntries, "Weekly");
    }

    private void updateStatsObject(CropStats.Stats stats, List<FarmerEntry> entries, String statsType) {
        if (entries.isEmpty()) {
            stats.setAveragePrice(0);
            stats.setMinPrice(0);
            stats.setMaxPrice(0);
            stats.setTotalFarmersContributed(0);
            stats.setLastUpdated(LocalDateTime.now());
            stats.setStale(false);
            log.debug("{} stats: No entries found", statsType);
            return;
        }

        double sum = entries.stream().mapToDouble(FarmerEntry::getPrice).sum();
        double min = entries.stream().mapToDouble(FarmerEntry::getPrice).min().orElse(0);
        double max = entries.stream().mapToDouble(FarmerEntry::getPrice).max().orElse(0);
        double avg = sum / entries.size();

        // Validate and handle NaN/Infinite values
        if (Double.isNaN(avg) || Double.isInfinite(avg)) {
            avg = 0;
            log.warn("Invalid average calculation for {} stats, setting to 0", statsType);
        }

        stats.setAveragePrice(Math.round(avg * 100.0) / 100.0);
        stats.setMinPrice(Math.round(min * 100.0) / 100.0);
        stats.setMaxPrice(Math.round(max * 100.0) / 100.0);
        stats.setTotalFarmersContributed(entries.size());
        stats.setLastUpdated(LocalDateTime.now());
        stats.setStale(false);

        log.debug("{} stats updated - Avg: {}, Min: {}, Max: {}, Count: {}", statsType, avg, min, max, entries.size());
    }

    private void updateTrend(CropStats cropStats, Crops crop, Market mandi) {
        LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        // Get today's entries using exact date range
        List<FarmerEntry> todayEntries = farmerEntryRepository.findByCropAndMarketAndCreatedAtBetween(crop, mandi, startOfToday, startOfTomorrow);

        List<CropStats.Trend> trendList = cropStats.getTrend() != null ? new ArrayList<>(cropStats.getTrend()) : new ArrayList<>();

        // Find today's trend or create new one
        CropStats.Trend todayTrend = findOrCreateTodayTrend(trendList, startOfToday);

        // Update today's trend with actual data
        updateSingleTrend(todayTrend, todayEntries);

        // Clean up old trends (keep only last 30 days)
        trendList = cleanupOldTrends(trendList);

        cropStats.setTrend(trendList);
        log.debug("Trend updated for date: {}, Entries: {}", startOfToday.toLocalDate(), todayEntries.size());
    }

    private CropStats.Trend findOrCreateTodayTrend(List<CropStats.Trend> trendList, LocalDateTime startOfToday) {
        return trendList.stream().filter(trend -> isSameDay(trend.getDate(), startOfToday)).findFirst().orElseGet(() -> {
            CropStats.Trend newTrend = CropStats.Trend.builder().date(startOfToday).averagePrice(0).minPrice(0).maxPrice(0).farmersContributed(0).build();
            trendList.add(newTrend);
            log.info("Created new trend entry for date: {}", startOfToday.toLocalDate());
            return newTrend;
        });
    }

    private void updateSingleTrend(CropStats.Trend trend, List<FarmerEntry> entries) {
        if (entries.isEmpty()) {
            trend.setAveragePrice(0);
            trend.setMinPrice(0);
            trend.setMaxPrice(0);
            trend.setFarmersContributed(0);
            return;
        }

        double sum = entries.stream().mapToDouble(FarmerEntry::getPrice).sum();
        double min = entries.stream().mapToDouble(FarmerEntry::getPrice).min().orElse(0);
        double max = entries.stream().mapToDouble(FarmerEntry::getPrice).max().orElse(0);
        double avg = sum / entries.size();

        trend.setAveragePrice(Math.round(avg * 100.0) / 100.0);
        trend.setMinPrice(Math.round(min * 100.0) / 100.0);
        trend.setMaxPrice(Math.round(max * 100.0) / 100.0);
        trend.setFarmersContributed(entries.size());

        log.debug("Trend data updated - Avg: {}, Min: {}, Max: {}, Count: {}", avg, min, max, entries.size());
    }

    private List<CropStats.Trend> cleanupOldTrends(List<CropStats.Trend> trendList) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return trendList.stream().filter(trend -> trend.getDate().isAfter(thirtyDaysAgo)).collect(Collectors.toList());
    }

    private boolean isSameDay(LocalDateTime date1, LocalDateTime date2) {
        return date1.toLocalDate().equals(date2.toLocalDate());
    }

    private CropStats createNewCropStats(Crops crop, Market mandi) {
        LocalDateTime now = LocalDateTime.now();
        CropStats.Stats emptyStats = CropStats.Stats.builder().averagePrice(0).minPrice(0).maxPrice(0).totalFarmersContributed(0).lastUpdated(now).isStale(false).build();

        return CropStats.builder().crop(crop)  // This will save as proper DBRef
                .mandi(mandi) // This will save as proper DBRef
                .allTimeStats(emptyStats).dailyStats(emptyStats).weeklyStats(emptyStats).trend(new ArrayList<>()).build();
    }


}
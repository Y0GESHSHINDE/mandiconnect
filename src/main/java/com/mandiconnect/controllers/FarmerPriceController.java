package com.mandiconnect.controllers;

import com.mandiconnect.models.DailyMarketStats;
import com.mandiconnect.models.FarmerPrice;
import com.mandiconnect.repositories.DailyMarketStatsRepository;
import com.mandiconnect.repositories.FarmerPriceRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/prices")
public class FarmerPriceController {

    @Autowired
    private FarmerPriceRepository farmerPriceRepository;

    @Autowired
    private DailyMarketStatsRepository dailyMarketStatsRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // 1️⃣ Create or Update Farmer Price
    @PostMapping("/addPrice")
    ResponseEntity<?> createOrUpdatePrice(@RequestBody FarmerPrice priceEntry, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        LocalDate today = priceEntry.getDate();
        if (today == null) {
            today = LocalDate.now();
            priceEntry.setDate(today);
        }

        // Check if farmer already has entry for crop+market+date
        FarmerPrice existing = farmerPriceRepository.findByFarmerIdAndCropIdAndMarketIdAndDate(priceEntry.getFarmerId(), priceEntry.getCropId(), priceEntry.getMarketId(), today).orElse(null);

        if (existing != null) {
            existing.setPrice(priceEntry.getPrice());
            existing.setUnit(priceEntry.getUnit());
            existing.setCurrency(priceEntry.getCurrency());
            existing.setUpdatedAt(LocalDateTime.now());
            farmerPriceRepository.save(existing);
        } else {
            priceEntry.setCreatedAt(LocalDateTime.now());
            priceEntry.setUpdatedAt(LocalDateTime.now());
            farmerPriceRepository.save(priceEntry);
        }

        // Recalculate stats for this crop+market+date
        recalculateMarketStats(priceEntry.getCropId(), priceEntry.getMarketId(), today, priceEntry.getUnit(), priceEntry.getCurrency());

        return ResponseEntity.ok(priceEntry);
    }

    // 2️⃣ Recalculate Aggregated Stats
    private void recalculateMarketStats(String cropId, String marketId, LocalDate date, String unit, String currency) {
        List<FarmerPrice> prices = farmerPriceRepository.findByCropIdAndMarketIdAndDate(cropId, marketId, date);

        if (prices.isEmpty()) return;

        double avg = prices.stream().mapToDouble(FarmerPrice::getPrice).average().orElse(0.0);
        double min = prices.stream().mapToDouble(FarmerPrice::getPrice).min().orElse(0.0);
        double max = prices.stream().mapToDouble(FarmerPrice::getPrice).max().orElse(0.0);

        DailyMarketStats stats = dailyMarketStatsRepository.findByCropIdAndMarketIdAndDate(cropId, marketId, date).orElse(new DailyMarketStats());

        stats.setCropId(cropId);
        stats.setMarketId(marketId);
        stats.setDate(date);
        stats.setAveragePrice(avg);
        stats.setMinPrice(min);
        stats.setMaxPrice(max);
        stats.setTotalFarmers(prices.size());
        stats.setUnit(unit);
        stats.setCurrency(currency);
        stats.setUpdatedAt(LocalDateTime.now());

        dailyMarketStatsRepository.save(stats);
    }
}

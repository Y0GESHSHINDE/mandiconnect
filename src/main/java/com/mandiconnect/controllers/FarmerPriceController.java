package com.mandiconnect.controllers;

import com.mandiconnect.models.Crops;
import com.mandiconnect.models.DailyMarketStats;
import com.mandiconnect.models.FarmerPrice;
import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.DailyMarketStatsRepository;
import com.mandiconnect.repositories.FarmerPriceRepository;
import com.mandiconnect.repositories.MarketRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private CropRepository cropRepository;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ---------------- Create or Update Farmer Price ----------------
    @PostMapping("/addPrice")
    ResponseEntity<?> createOrUpdatePrice(@RequestBody FarmerPrice priceEntry, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerified = jwtUtil.validateToken(token);

        if (!isVerified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        LocalDate today = priceEntry.getDate();
        if (today == null) {
            today = LocalDate.now();
            priceEntry.setDate(today);
        }

        // Fetch crop metadata
        Crops crop = cropRepository.findById(priceEntry.getCropId()).orElseThrow();

        // ✅ Normalize input price to crop’s displayUnit
        double normalizedPrice = priceEntry.getPrice();
        String inputUnit = priceEntry.getUnit();

        if ("grain".equalsIgnoreCase(crop.getType())) {
            if ("kg".equalsIgnoreCase(inputUnit)) {
                normalizedPrice = normalizedPrice * 100; // per kg → per quintal
            }
            priceEntry.setUnit("quintal");
        } else if ("leafy-vegetable".equalsIgnoreCase(crop.getType())) {
            priceEntry.setUnit("judi"); // only judi allowed
        } else {
            priceEntry.setUnit("kg"); // fruits/vegetables → kg
        }

        priceEntry.setPrice(normalizedPrice);

        // Check if farmer already has entry for crop+market+date
        FarmerPrice existing = farmerPriceRepository.findByFarmerIdAndCropIdAndMarketIdAndDate(priceEntry.getFarmerId(), priceEntry.getCropId(), priceEntry.getMarketId(), today).orElse(null);

        if (existing != null) {
            existing.setPrice(normalizedPrice);
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
        recalculateMarketStats(priceEntry.getCropId(), priceEntry.getMarketId(), today);

        return ResponseEntity.ok(priceEntry);
    }

    // ---------------- Recalculate Aggregated Stats ----------------
    private void recalculateMarketStats(String cropId, String marketId, LocalDate date) {
        List<FarmerPrice> prices = farmerPriceRepository.findByCropIdAndMarketIdAndDate(cropId, marketId, date);

        if (prices.isEmpty()) return;

        double avg = prices.stream().mapToDouble(FarmerPrice::getPrice).average().orElse(0.0);
        double min = prices.stream().mapToDouble(FarmerPrice::getPrice).min().orElse(0.0);
        double max = prices.stream().mapToDouble(FarmerPrice::getPrice).max().orElse(0.0);

        DailyMarketStats stats = dailyMarketStatsRepository.findByCropIdAndMarketIdAndDate(cropId, marketId, date).orElse(new DailyMarketStats());

        Crops crop = cropRepository.findById(cropId).orElseThrow();
        Market market = marketRepository.findById(marketId).orElseThrow();

        stats.setCrop(crop);
        stats.setMarket(market);
        stats.setDate(date);
        stats.setAveragePrice(avg);
        stats.setMinPrice(min);
        stats.setMaxPrice(max);
        stats.setTotalFarmers(prices.size());
        stats.setUnit(crop.getDisplayUnit()); // directly from crop
        stats.setCurrency(prices.get(0).getCurrency());
        stats.setUpdatedAt(LocalDateTime.now());

        dailyMarketStatsRepository.save(stats);
    }

    // Get ALl market stats
    @GetMapping("/getMarketStats")
    ResponseEntity<?> getMarketStats(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login First Then Try");
        }
        return ResponseEntity.status(HttpStatus.OK).body(dailyMarketStatsRepository.findAll());
    }

    // Get ALl market stats
    @GetMapping("/getMarketStatsByMarket/{marketid}")
    public ResponseEntity<?> getMarketStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String marketid) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        boolean isVerified;
        try {
            isVerified = jwtUtil.validateToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        if (!isVerified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login First Then Try");
        }

        List<DailyMarketStats> stats = dailyMarketStatsRepository.findByMarket(marketid);
        if (stats == null || stats.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No stats found for market: " + marketid);
        }

        return ResponseEntity.ok(stats);
    }


}

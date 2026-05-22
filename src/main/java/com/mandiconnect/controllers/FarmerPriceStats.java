package com.mandiconnect.controllers;

import com.mandiconnect.models.CropStats;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.MarketRepository;
import com.mandiconnect.services.FarmerEntryService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class FarmerPriceStats {

    private final JwtUtil jwtUtil;
    private final CropRepository cropRepository;
    private final MarketRepository marketRepository;
    private final FarmerEntryService farmerEntryService;

    @GetMapping("/getByMarket/{marketId}")
    public ResponseEntity<List<CropStats>> getByMarket(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String marketId
    ) {
        validateTokenOrThrow(authHeader);
        Market market = getMarketOrThrow(marketId);
        return ResponseEntity.ok(farmerEntryService.getStatsByMarket(market));
    }

    @GetMapping("/getByCrop/{cropId}")
    public ResponseEntity<List<CropStats>> getByCrop(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String cropId
    ) {
        validateTokenOrThrow(authHeader);
        Crops crop = getCropOrThrow(cropId);
        return ResponseEntity.ok(farmerEntryService.getStatsByCrop(crop));
    }

    @GetMapping({"/getByCropIdAndMarketid/{cropId}/{marketId}", "/crop-market/{cropId}/{marketId}"})
    public ResponseEntity<CropStats> getByCropIdAndMarketid(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String cropId,
            @PathVariable String marketId
    ) {
        validateTokenOrThrow(authHeader);
        Crops crop = getCropOrThrow(cropId);
        Market market = getMarketOrThrow(marketId);
        return ResponseEntity.ok(farmerEntryService.getStatsByCropAndMarket(crop, market));
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildAllStats(
            @RequestHeader("Authorization") String authHeader
    ) {
        validateTokenOrThrow(authHeader);
        return ResponseEntity.ok(farmerEntryService.rebuildAllStats());
    }

    private void validateTokenOrThrow(String authHeader) {
        String token = authHeader == null ? "" : authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private Crops getCropOrThrow(String cropId) {
        return cropRepository.findById(cropId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop not found"));
    }

    private Market getMarketOrThrow(String marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found"));
    }
}

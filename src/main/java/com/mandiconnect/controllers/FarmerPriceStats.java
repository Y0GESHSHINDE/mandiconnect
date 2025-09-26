package com.mandiconnect.controllers;

import com.mandiconnect.models.CropStats;
import com.mandiconnect.repositories.CropStatsRepository;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class FarmerPriceStats {

    private final JwtUtil jwtUtil;
    @Autowired
    private CropStatsRepository cropStatsRepository;

    @GetMapping("getByMarket/{marketId}")
    ResponseEntity<?> getByMarket(@RequestHeader("Authorization") String authHeader, @PathVariable String marketId) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        Optional<CropStats> entries = cropStatsRepository.findByMandiId(marketId);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("getByCrop/{cropId}")
    ResponseEntity<?> getByCrop(@RequestHeader("Authorization") String authHeader, @PathVariable String cropId) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        Optional<CropStats> entries = cropStatsRepository.findByCropId(cropId);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/getByCropIdAndMarketid/{cropId}/{marketId}")
    ResponseEntity<?> getByCropIdAndMarketid(@RequestHeader("Authorization") String authHeader, @PathVariable String cropId, @PathVariable String marketId) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        Optional<CropStats> entries = cropStatsRepository.findByCropIdAndMandiId(cropId, marketId);
        return ResponseEntity.ok(entries);
    }
}

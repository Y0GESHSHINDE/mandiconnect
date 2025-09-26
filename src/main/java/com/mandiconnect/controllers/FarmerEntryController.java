package com.mandiconnect.controllers;

import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.repositories.FarmerEntryRepository;
import com.mandiconnect.services.FarmerEntryService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/farmer-entries")
@RequiredArgsConstructor
public class FarmerEntryController {

    private final FarmerEntryService farmerEntryService;
    private final FarmerEntryRepository farmerEntryRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/add")
    public ResponseEntity<?> addEntry(@RequestHeader("Authorization") String authHeader, @RequestBody FarmerEntry entry) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        FarmerEntry savedEntry = farmerEntryService.addFarmerEntry(entry);
        return ResponseEntity.ok("Price Entry Add");
    }

    @GetMapping("/getAllEntries")
    ResponseEntity<?> getAllEntries(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        return ResponseEntity.ok(farmerEntryRepository.findAll());
    }

    @GetMapping("/getByCropAndMarket/{cropId}/{marketId}")
    ResponseEntity<?> getByCropAndMarket(@RequestHeader("Authorization") String authHeader, @PathVariable String cropId, @PathVariable String marketId) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        List<FarmerEntry> entries = farmerEntryRepository.findByCropIdAndMarketId(cropId, marketId);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/getByFarmerId/{farmerId}")
    ResponseEntity<?> getByCropAndMarket(@RequestHeader("Authorization") String authHeader, @PathVariable String farmerId) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        List<FarmerEntry> entries = farmerEntryRepository.findByFarmerId(farmerId);
        return ResponseEntity.ok(entries);
    }
}

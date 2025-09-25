package com.mandiconnect.controllers;

import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.repositories.FarmerEntryRepository;
import com.mandiconnect.services.FarmerEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/farmer-entries")
@RequiredArgsConstructor
public class FarmerEntryController {

    private final FarmerEntryService farmerEntryService;
    private final FarmerEntryRepository farmerEntryRepository;

    @PostMapping("/add")
    public ResponseEntity<FarmerEntry> addEntry(@RequestBody FarmerEntry entry) {
        FarmerEntry savedEntry = farmerEntryService.addFarmerEntry(entry);
        return ResponseEntity.ok(savedEntry);
    }

    @GetMapping("/getAllEntries")
    ResponseEntity<?> getAllEntries() {
        return ResponseEntity.ok(farmerEntryRepository.findAll());
    }

    @GetMapping("/getByCropAndMarket/{cropId}/{marketId}")
    ResponseEntity<?> getByCropAndMarket(@PathVariable String cropId, @PathVariable String marketId) {
        List<FarmerEntry> entries = farmerEntryRepository.findByCropIdAndMarketId(cropId, marketId);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/getByFarmerId/{farmerId}")
    ResponseEntity<?> getByCropAndMarket(@PathVariable String farmerId) {
        List<FarmerEntry> entries = farmerEntryRepository.findByFarmerId(farmerId);
        return ResponseEntity.ok(entries);
    }
}

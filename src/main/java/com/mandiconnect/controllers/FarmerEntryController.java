package com.mandiconnect.controllers;

import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.services.FarmerEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farmer-entries")
@RequiredArgsConstructor
public class FarmerEntryController {

    private final FarmerEntryService farmerEntryService;

    @PostMapping("/add")
    public ResponseEntity<FarmerEntry> addEntry(@RequestBody FarmerEntry entry) {
        FarmerEntry savedEntry = farmerEntryService.addFarmerEntry(entry);
        return ResponseEntity.ok(savedEntry);
    }
}

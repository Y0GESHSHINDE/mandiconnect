package com.mandiconnect.controllers;

import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.repositories.FarmerEntryRepository;
import com.mandiconnect.services.FarmerEntryService;
import com.mandiconnect.services.NotificationService;
import com.mandiconnect.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/farmer-entries")
@RequiredArgsConstructor
public class FarmerEntryController {

    private final FarmerEntryService farmerEntryService;
    private final FarmerEntryRepository farmerEntryRepository;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService ;
    @PostMapping("/add")
    public ResponseEntity<?> addEntry(@RequestHeader("Authorization") String authHeader, @RequestBody FarmerEntry entry) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        FarmerEntry savedEntry = farmerEntryService.addFarmerEntry(entry);
        notificationService.notifyPricePosted(savedEntry);
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


        /* =====================================================
           1️⃣ AGREE PRICE
           ===================================================== */
        @PostMapping("/agree/{entryId}/{farmerId}")
        public ResponseEntity<?> priceAgree(
                @RequestHeader("Authorization") String authHeader,
                @PathVariable String entryId,
                @PathVariable String farmerId
        ) {

            if (!jwtUtil.validateToken(authHeader.replace("Bearer", "").trim())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            FarmerEntry entry = farmerEntryRepository.findById(entryId)
                    .orElseThrow(() -> new RuntimeException("Price entry not found"));

            if (entry.getFeedback().getVotedFarmers() == null) {
                entry.getFeedback().setVotedFarmers(new ArrayList<>());
            }

            if (entry.getFeedback().getVotedFarmers().contains(farmerId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("You have already voted");
            }

            entry.getFeedback().setAgreeCount(
                    entry.getFeedback().getAgreeCount() + 1
            );
            entry.getFeedback().getVotedFarmers().add(farmerId);

            farmerEntryRepository.save(entry);

            return ResponseEntity.ok("Price agreed successfully");
        }

        /* =====================================================
           2️⃣ DISAGREE PRICE
           ===================================================== */
        @PostMapping("/disagree/{entryId}/{farmerId}")
        public ResponseEntity<?> priceDisAgree(
                @RequestHeader("Authorization") String authHeader,
                @PathVariable String entryId,
                @PathVariable String farmerId
        ) {

            if (!jwtUtil.validateToken(authHeader.replace("Bearer", "").trim())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            FarmerEntry entry = farmerEntryRepository.findById(entryId)
                    .orElseThrow(() -> new RuntimeException("Price entry not found"));

            if (entry.getFeedback().getVotedFarmers() == null) {
                entry.getFeedback().setVotedFarmers(new ArrayList<>());
            }

            if (entry.getFeedback().getVotedFarmers().contains(farmerId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("You have already voted");
            }

            entry.getFeedback().setDisagreeCount(
                    entry.getFeedback().getDisagreeCount() + 1
            );
            entry.getFeedback().getVotedFarmers().add(farmerId);

            farmerEntryRepository.save(entry);

            return ResponseEntity.ok("Price disagreed successfully");
        }

        /* =====================================================
           3️⃣ GET AGREE COUNT
           ===================================================== */
        @GetMapping("/agree-count/{entryId}")
        public ResponseEntity<?> getAgreeCount(
                @RequestHeader("Authorization") String authHeader,
                @PathVariable String entryId
        ) {

            if (!jwtUtil.validateToken(authHeader.replace("Bearer", "").trim())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            FarmerEntry entry = farmerEntryRepository.findById(entryId)
                    .orElseThrow(() -> new RuntimeException("Price entry not found"));

            return ResponseEntity.ok(entry.getFeedback().getAgreeCount());
        }

        /* =====================================================
           4️⃣ GET DISAGREE COUNT
           ===================================================== */
        @GetMapping("/disagree-count/{entryId}")
        public ResponseEntity<?> getDisAgreeCount(
                @RequestHeader("Authorization") String authHeader,
                @PathVariable String entryId
        ) {

            if (!jwtUtil.validateToken(authHeader.replace("Bearer", "").trim())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            FarmerEntry entry = farmerEntryRepository.findById(entryId)
                    .orElseThrow(() -> new RuntimeException("Price entry not found"));

            return ResponseEntity.ok(entry.getFeedback().getDisagreeCount());
        }


}

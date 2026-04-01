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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            if (entry.getFarmer() != null && farmerId.equals(entry.getFarmer().getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("You cannot react to your own price");
            }

            normalizeFeedback(entry);

            boolean addedAgree = false;

            if (entry.getFeedback().getLikedFarmerIds().contains(farmerId)) {
                entry.getFeedback().getLikedFarmerIds().remove(farmerId);
                entry.getFeedback().setAgreeCount(Math.max(0, entry.getFeedback().getAgreeCount() - 1));
            } else {
                if (entry.getFeedback().getDislikedFarmerIds().remove(farmerId)) {
                    entry.getFeedback().setDisagreeCount(Math.max(0, entry.getFeedback().getDisagreeCount() - 1));
                }

                entry.getFeedback().getLikedFarmerIds().add(farmerId);
                entry.getFeedback().setAgreeCount(entry.getFeedback().getAgreeCount() + 1);
                addedAgree = true;
            }

            farmerEntryRepository.save(entry);
            if (addedAgree) {
                notificationService.notifyPriceAgree(entry, farmerId);
            }
            return ResponseEntity.ok(buildReactionSummary(entry, farmerId));
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

            if (entry.getFarmer() != null && farmerId.equals(entry.getFarmer().getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("You cannot react to your own price");
            }

            normalizeFeedback(entry);

            boolean addedDisagree = false;

            if (entry.getFeedback().getDislikedFarmerIds().contains(farmerId)) {
                entry.getFeedback().getDislikedFarmerIds().remove(farmerId);
                entry.getFeedback().setDisagreeCount(Math.max(0, entry.getFeedback().getDisagreeCount() - 1));
            } else {
                if (entry.getFeedback().getLikedFarmerIds().remove(farmerId)) {
                    entry.getFeedback().setAgreeCount(Math.max(0, entry.getFeedback().getAgreeCount() - 1));
                }

                entry.getFeedback().getDislikedFarmerIds().add(farmerId);
                entry.getFeedback().setDisagreeCount(entry.getFeedback().getDisagreeCount() + 1);
                addedDisagree = true;
            }

            farmerEntryRepository.save(entry);
            if (addedDisagree) {
                notificationService.notifyPriceDisAgree(entry, farmerId);
            }

            return ResponseEntity.ok(buildReactionSummary(entry, farmerId));
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

            normalizeFeedback(entry);
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

            normalizeFeedback(entry);
            return ResponseEntity.ok(entry.getFeedback().getDisagreeCount());
        }

        private void normalizeFeedback(FarmerEntry entry) {
            if (entry.getFeedback() == null) {
                entry.setFeedback(new FarmerEntry.Feedback());
            }

            if (entry.getFeedback().getLikedFarmerIds() == null) {
                entry.getFeedback().setLikedFarmerIds(new ArrayList<>());
            }

            if (entry.getFeedback().getDislikedFarmerIds() == null) {
                entry.getFeedback().setDislikedFarmerIds(new ArrayList<>());
            }

            if (entry.getFeedback().getAgreeCount() < 0) {
                entry.getFeedback().setAgreeCount(0);
            }

            if (entry.getFeedback().getDisagreeCount() < 0) {
                entry.getFeedback().setDisagreeCount(0);
            }
        }

        private Map<String, Object> buildReactionSummary(FarmerEntry entry, String farmerId) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("agreeCount", entry.getFeedback().getAgreeCount());
            summary.put("disagreeCount", entry.getFeedback().getDisagreeCount());
            summary.put("userReaction", resolveUserReaction(entry, farmerId));
            return summary;
        }

        private String resolveUserReaction(FarmerEntry entry, String farmerId) {
            if (farmerId == null || farmerId.isBlank() || entry.getFeedback() == null) {
                return "NONE";
            }

            if (entry.getFeedback().getLikedFarmerIds() != null && entry.getFeedback().getLikedFarmerIds().contains(farmerId)) {
                return "AGREE";
            }

            if (entry.getFeedback().getDislikedFarmerIds() != null && entry.getFeedback().getDislikedFarmerIds().contains(farmerId)) {
                return "DISAGREE";
            }

            return "NONE";
        }


}

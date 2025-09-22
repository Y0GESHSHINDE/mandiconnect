package com.mandiconnect.controllers;

import com.mandiconnect.models.CropListing;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.repositories.CropListingRepository;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.services.FileUploadService;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/marketplace/farmer")
public class FarmerMarketplaceController {

    @Autowired
    private FileUploadService fileuploadService;

    @Autowired
    private CropListingRepository cropListingRepository;

    @Autowired
    private FarmerRepository farmerRepository;

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/upload")
    ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, String> fileData = fileuploadService.uploadFile(file);
            Map<String, String> responseData = Map.of(
                    "url", fileData.get("secure_url").toString(),
                    "public_id", fileData.get("public_id").toString()
            );
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("File upload failed: " + e.getMessage());
        }
    }


    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestParam("public_id") String publicId) throws IOException {
        Map<String, Object> result = fileuploadService.deleteFile(publicId);
        return ResponseEntity.ok(result);
    }


    @PostMapping("/cropListing")
    public ResponseEntity<?> createCropListing(@RequestHeader("Authorization") String authHeader, @RequestBody CropListing cropListing) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        // Fetch full Farmer and Crop objects
        Farmer farmer = farmerRepository.findById(cropListing.getFarmer().getId()).orElseThrow(() -> new RuntimeException("Invalid Farmer ID"));

        Crops crop = cropRepository.findById(cropListing.getCrop().getId()).orElseThrow(() -> new RuntimeException("Invalid Crop ID"));

        cropListing.setFarmer(farmer);
        cropListing.setCrop(crop);
        cropListing.setCreatedAt(LocalDateTime.now());
        cropListing.setUpdatedAt(LocalDateTime.now());

        CropListing saved = cropListingRepository.save(cropListing);
        return ResponseEntity.ok(saved);
    }


    @GetMapping("getAllListing")
    ResponseEntity<?> getAllListing() {
        return ResponseEntity.ok(cropListingRepository.findAll());
    }


}

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
import java.util.Optional;

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
    ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file ,@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
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
    public ResponseEntity<?> deleteFile(@RequestParam("public_id") String publicId ,@RequestHeader("Authorization") String authHeader) throws IOException {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        Map<String, Object> result = fileuploadService.deleteFile(publicId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/cropListing/{id}")
    public ResponseEntity<?> deleteCropListing(
            @PathVariable("id") String listingId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        Optional<CropListing> existingListing = cropListingRepository.findById(listingId);
        if (existingListing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Crop listing not found");
        }

        CropListing cropListing = existingListing.get();
        String authenticatedEmail = jwtUtil.getEmailFromToken(token);
        String listingOwnerEmail =
                cropListing.getFarmer() != null ? cropListing.getFarmer().getEmail() : null;

        if (listingOwnerEmail == null || !listingOwnerEmail.equalsIgnoreCase(authenticatedEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can delete only your own crop listings");
        }

        cropListingRepository.deleteById(listingId);
        return ResponseEntity.ok("Crop listing deleted successfully");
    }


    @PostMapping("/cropListing")
    public ResponseEntity<?> createCropListing(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CropListing cropListing) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        Optional<Farmer> farmer = farmerRepository.findById(cropListing.getFarmer().getId());
        if (farmer.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Invalid Farmer ID");
        }

        Optional<Crops> crop = cropRepository.findById(cropListing.getCrop().getId());
        if (crop.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body("Error: Invalid Crop ID");
        }

        cropListing.setFarmer(farmer.get());
        cropListing.setCrop(crop.get());
        cropListing.setCreatedAt(LocalDateTime.now());
        cropListing.setUpdatedAt(LocalDateTime.now());

        CropListing saved = cropListingRepository.save(cropListing);
        return ResponseEntity.ok("Price Entry Added ");
    }



    @GetMapping("getAllListing")
    ResponseEntity<?> getAllListing(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        return ResponseEntity.ok(cropListingRepository.findAll());
    }


}

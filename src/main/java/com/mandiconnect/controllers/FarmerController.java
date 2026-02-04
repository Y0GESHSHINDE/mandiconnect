package com.mandiconnect.controllers;

import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.VerificationToken;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.VerificationTokenRepository;
import com.mandiconnect.services.EmailService;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/farmer")
public class FarmerController {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private FarmerRepository farmerRepository;
    @Autowired
    private VerificationTokenRepository tokenRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private JwtUtil jwtUtil;

    // New: Verify email
    @GetMapping("/verify")
    public ResponseEntity<?> verifyFarmer(@RequestParam("token") String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token).orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().before(new Date())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token expired!"));
        }

        Farmer farmer = farmerRepository.findById(verificationToken.getFarmerId()).orElseThrow(() -> new RuntimeException("Farmer not found"));

        farmer.setVerified(true);
        farmerRepository.save(farmer);

        tokenRepository.delete(verificationToken);

        return ResponseEntity.ok(Map.of("message", "Email verified! You can now log in."));
    }

    //    To Get All Farmers
    @GetMapping("/getFarmers")
    public List<Farmer> getAllFarmers() {
        return farmerRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getFarmerById(@PathVariable String id) {

        return farmerRepository.findById(id).map(farmer -> {

            Map<String, Object> response = new HashMap<>();

            response.put("id", farmer.getId());
            response.put("name", farmer.getName());
            response.put("mobile", farmer.getMobile());
            response.put("email", farmer.getEmail());
            response.put("role", farmer.getRole());
            response.put("verified", farmer.isVerified());
            response.put("farmerAddress", farmer.getFarmerAddress());
            response.put("farmDetails", farmer.getFarmDetails());

            // password is NOT added

            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    // New: Signup farmer (with email verification)
    @PostMapping("/signup")
    public ResponseEntity<?> signupFarmer(@RequestBody Farmer farmer) {

        String email = farmer.getEmail().toLowerCase();
        String phoneNo = farmer.getMobile();

        if (farmerRepository.existsByEmail(email) || farmerRepository.existsByMobile(phoneNo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email or Mobile already exists!"));
        }

        farmer.setEmail(email);
        farmer.setPassword(passwordEncoder.encode(farmer.getPassword()));
        farmer.setVerified(false);
        System.out.println(farmer);
        Farmer savedFarmer = farmerRepository.save(farmer);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(token);
        verificationToken.setFarmerId(savedFarmer.getId());
        verificationToken.setExpiryDate(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        tokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(savedFarmer.getEmail(), token);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Farmer registered! Please verify your email."));
    }

    //Login Farmer Api with JWT token
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Farmer loginRequest) {

        String email = loginRequest.getEmail().toLowerCase();
        String rawPassword = loginRequest.getPassword();

        // 1. Check if email exists
        if (!farmerRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }

        // 2. Find farmer by email
        Optional<Farmer> farmerOpt = farmerRepository.findByEmail(email);

        if (farmerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }

        Farmer farmer = farmerOpt.get();

        // 3. Check if farmer is verified
        if (!farmer.isVerified()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please verify your email first"));
        }

        // 4. Compare passwords
        if (passwordEncoder.matches(rawPassword, farmer.getPassword())) {
            String Token = jwtUtil.generateToken(email);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successfully done");
            response.put("User ID", farmer.getId());
            response.put("token", Token);
            return ResponseEntity.ok().body(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }
    }

    //Update Farmer Info API
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> updateFarmer(@PathVariable String id, @RequestBody Farmer updateData, @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid JWT token"));
        }

        Optional<Farmer> optionalFarmer = farmerRepository.findById(id);
        if (optionalFarmer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Farmer not found"));
        }

        Farmer existing = optionalFarmer.get();

        // ---------------- BASIC INFO ----------------
        if (updateData.getName() != null && !updateData.getName().isBlank()) {
            existing.setName(updateData.getName().trim());
        }

        if (updateData.getPassword() != null && !updateData.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(updateData.getPassword()));
        }

        // ---------------- ADDRESS ----------------
        if (updateData.getFarmerAddress() != null) {
            Farmer.FarmerAddress inAddr = updateData.getFarmerAddress();
            Farmer.FarmerAddress exAddr = existing.getFarmerAddress();

            if (exAddr == null) {
                exAddr = new Farmer.FarmerAddress();
                existing.setFarmerAddress(exAddr);
            }

            if (inAddr.getCity() != null && !inAddr.getCity().isBlank()) exAddr.setCity(inAddr.getCity().trim());

            if (inAddr.getState() != null && !inAddr.getState().isBlank()) exAddr.setState(inAddr.getState().trim());

            if (inAddr.getCountry() != null && !inAddr.getCountry().isBlank())
                exAddr.setCountry(inAddr.getCountry().trim());
        }

        // ---------------- FARM DETAILS ----------------
        if (updateData.getFarmDetails() != null) {
            Farmer.FarmDetails inFarm = updateData.getFarmDetails();
            Farmer.FarmDetails exFarm = existing.getFarmDetails();

            if (exFarm == null) {
                exFarm = new Farmer.FarmDetails();
                existing.setFarmDetails(exFarm);
            }

            if (inFarm.getFarmSize() != null && !inFarm.getFarmSize().isBlank())
                exFarm.setFarmSize(inFarm.getFarmSize().trim());

            if (inFarm.getCropIds() != null && !inFarm.getCropIds().isEmpty()) exFarm.setCropIds(inFarm.getCropIds());

            if (inFarm.getPreferredMarketIds() != null && !inFarm.getPreferredMarketIds().isEmpty())
                exFarm.setPreferredMarketIds(inFarm.getPreferredMarketIds());

            if (inFarm.getIrrigationType() != null && !inFarm.getIrrigationType().isBlank())
                exFarm.setIrrigationType(inFarm.getIrrigationType().trim());

            if (inFarm.getSoilType() != null && !inFarm.getSoilType().isBlank())
                exFarm.setSoilType(inFarm.getSoilType().trim());
        }

        farmerRepository.save(existing);

        return ResponseEntity.ok(Map.of("message", "Farmer updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteBuyer(@PathVariable String id, @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer", "").trim();
        Boolean isTokenValid = jwtUtil.validateToken(token);

        if (!isTokenValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "JWT Token is Not Valid Login Again"));
        }

        return farmerRepository.findById(id).map(buyer -> {
            farmerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Farmer deleted successfully!"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
package com.mandiconnect.controllers;

import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.VerificationTokenRepository;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.VerificationToken;
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

    //    To Get All Farmers
    @GetMapping("/getFarmers")
    public List<Farmer> getAllFarmers() {
        return farmerRepository.findAll();
    }

    //    To Get specific Farmers
    @GetMapping("/{id}")
    public ResponseEntity<Farmer> getFarmerById(@PathVariable String id) {
        return farmerRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }


    // New: Signup farmer (with email verification)
    @PostMapping("/signup")
    public ResponseEntity<?> signupFarmer(@RequestBody Farmer farmer) {

//        check user exists or not
        String email = farmer.getEmail();
        String phoneNo = farmer.getMobile();

        boolean emailExists = farmerRepository.existsByEmail(email);
        boolean phoneExists = farmerRepository.existsByMobile(phoneNo);

        if (emailExists || phoneExists) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email or Mobile already exists!");
        }


        // Hash password
        farmer.setPassword(passwordEncoder.encode(farmer.getPassword()));
        farmer.setVerified(false);

        // Save farmer
        Farmer savedFarmer = farmerRepository.save(farmer);

        // Create verification token
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(token);
        verificationToken.setFarmerId(savedFarmer.getId());
        verificationToken.setExpiryDate(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        tokenRepository.save(verificationToken);

        // Send email
        emailService.sendVerificationEmail(savedFarmer.getEmail(), token);

        return ResponseEntity.status(HttpStatus.CREATED).body("Farmer registered! Please check your email for verification link.");
    }

    // New: Verify email
    @GetMapping("/verify")
    public String verifyFarmer(@RequestParam("token") String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token).orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().before(new Date())) {
            return "Token expired!";
        }

        Farmer farmer = farmerRepository.findById(verificationToken.getFarmerId()).orElseThrow(() -> new RuntimeException("Farmer not found"));

        farmer.setVerified(true);
        farmerRepository.save(farmer);

        tokenRepository.delete(verificationToken);

        return ("Email verified! You can now log in.");
    }

    //Login Farmer Api with JWT token
//Login Farmer Api
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Farmer loginRequest) {

        String email = loginRequest.getEmail();
        String rawPassword = loginRequest.getPassword();

        // 1. Check if email exists
        if (!farmerRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        // 2. Find farmer by email
        Optional<Farmer> farmerOpt = farmerRepository.findByEmail(email);

        if (farmerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        Farmer farmer = farmerOpt.get();

        // 3. Check if farmer is verified
        if (!farmer.isVerified()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Please verify your email first");
        }

        // 4. Compare passwords
        if (passwordEncoder.matches(rawPassword, farmer.getPassword())) {
            String Token = jwtUtil.generateToken(email);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successfully done");
            response.put("token", Token);
            return ResponseEntity.ok().body(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }
    }

}
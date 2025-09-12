package com.mandiconnect.controllers;

import com.mandiconnect.Repositories.FarmerRepository;
import com.mandiconnect.Repositories.VerificationTokenRepository;
import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.VerificationToken;
import com.mandiconnect.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/farmer")
public class FarmerController {

    @Autowired
    private FarmerRepository farmerRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    //    To Get All Farmers
    @GetMapping("/getFarmers")
    public List<Farmer> getAllFarmers() {
        return farmerRepository.findAll();
    }

    //    To Get specific Farmers
    @GetMapping("/{id}")
    public ResponseEntity<Farmer> getFarmerById(@PathVariable String id) {
        return farmerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    // New: Signup farmer (with email verification)
    @PostMapping("/signup")
    public String signupFarmer(@RequestBody Farmer farmer) {
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

        return "Farmer registered! Please check your email for verification link.";
    }

    // New: Verify email
    @GetMapping("/verify")
    public String verifyFarmer(@RequestParam("token") String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().before(new Date())) {
            return "Token expired!";
        }

        Farmer farmer = farmerRepository.findById(verificationToken.getFarmerId())
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        farmer.setVerified(true);
        farmerRepository.save(farmer);

        return "Email verified! You can now log in.";
    }
}
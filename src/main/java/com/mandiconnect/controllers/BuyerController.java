package com.mandiconnect.controllers;

import com.mandiconnect.models.Buyer;
import com.mandiconnect.models.BuyerVerificationToken;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.BuyerVerificationTokenRepository;
import com.mandiconnect.services.EmailService;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/buyer")
public class BuyerController {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private BuyerVerificationTokenRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;


    @Autowired
    private BuyerRepository buyerRepository;

    @GetMapping("/getAll")
    public List<Buyer> allBuyers() {
        return buyerRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBuyerById(@PathVariable String id) {

        return buyerRepository.findById(id)
                .map(buyer -> {

                    Map<String, Object> response = new HashMap<>();

                    response.put("id", buyer.getId());
                    response.put("verified", buyer.isVerified());
                    response.put("Name", buyer.getName());
                    response.put("Mobile", buyer.getMobile());
                    response.put("Email", buyer.getEmail());
                    response.put("Company Name", buyer.getCompanyName());
                    response.put("Company Address", buyer.getCompanyAddress());
                    response.put("PreferredCrops", buyer.getPreferredCrops());

                    // Password is NOT added

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signUpBuyer(@RequestBody Buyer buyer) {

        buyer.setEmail(buyer.getEmail().toLowerCase());

        if (buyerRepository.existsByEmail(buyer.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists!");
        }

        if (buyerRepository.existsByMobile(buyer.getMobile())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Mobile already exists!");
        }

        buyer.setPassword(passwordEncoder.encode(buyer.getPassword()));
        buyer.setVerified(false);

        Buyer savedBuyer = buyerRepository.save(buyer);

        String token = UUID.randomUUID().toString();
        BuyerVerificationToken verificationToken = new BuyerVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setBuyerId(savedBuyer.getId());
        verificationToken.setExpiryDate(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        tokenRepository.save(verificationToken);

        emailService.sendVerificationEmailForBuyer(savedBuyer.getEmail(), token);

        return ResponseEntity.status(HttpStatus.CREATED).body("Buyer registered! Please check your email for verification link.");

    }

    @GetMapping("/verify")
    public String verifyBuyer(@RequestParam("token") String token) {
        BuyerVerificationToken verificationToken = tokenRepository.findByToken(token).orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().before(new Date())) {
            return "Token expired!";
        }

        Buyer buyer = buyerRepository.findById(verificationToken.getBuyerId()).orElseThrow(() -> new RuntimeException("Buyer not found"));

        buyer.setVerified(true);
        buyerRepository.save(buyer);

//        tokenRepository.delete(verificationToken);

        return "Email verified! You can now log in.";
    }


    @PostMapping("/login")
    public ResponseEntity<?> loginBuyer(@RequestBody Buyer loginRequest) {

        String email = loginRequest.getEmail().toLowerCase();
        String rawPassword = loginRequest.getPassword();

        if (!buyerRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        Optional<Buyer> buyerOpt = buyerRepository.findByEmail(email);

        if (buyerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        Buyer buyer = buyerOpt.get();

        if (!buyer.isVerified()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Please verify your email first");
        }

        if (passwordEncoder.matches(rawPassword, buyer.getPassword())) {
            String token = jwtUtil.generateToken(email);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful");
            response.put("token", token);
            return ResponseEntity.ok().body(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<?> patchUpdateBuyer(@PathVariable String id, @RequestBody Buyer updatedBuyer, @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }
        String token = authHeader.replace("Bearer", "").trim();

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
        }

        return buyerRepository.findById(id).map(existingBuyer -> {

            if (updatedBuyer.getName() != null) {
                existingBuyer.setName(updatedBuyer.getName());
            }

            if (updatedBuyer.getMobile() != null) {
                if (buyerRepository.existsByMobile(updatedBuyer.getMobile()) && !existingBuyer.getMobile().equals(updatedBuyer.getMobile())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Mobile already exists!");
                }
                existingBuyer.setMobile(updatedBuyer.getMobile());
            }

            if (updatedBuyer.getCompanyName() != null) {
                existingBuyer.setCompanyName(updatedBuyer.getCompanyName());
            }

            if (updatedBuyer.getCompanyAddress() != null) {
                Buyer.CompanyAddress newAddr = updatedBuyer.getCompanyAddress();
                Buyer.CompanyAddress oldAddr = existingBuyer.getCompanyAddress();
                if (oldAddr == null) {
                    oldAddr = new Buyer.CompanyAddress();
                }
                if (newAddr.getCity() != null) oldAddr.setCity(newAddr.getCity());
                if (newAddr.getState() != null) oldAddr.setState(newAddr.getState());
                if (newAddr.getCountry() != null) oldAddr.setCountry(newAddr.getCountry());
                existingBuyer.setCompanyAddress(oldAddr);
            }

            if (updatedBuyer.getPreferredCrops() != null && !updatedBuyer.getPreferredCrops().isEmpty()) {
                existingBuyer.setPreferredCrops(updatedBuyer.getPreferredCrops());
            }

            if (updatedBuyer.getPassword() != null) {
                existingBuyer.setPassword(passwordEncoder.encode(updatedBuyer.getPassword()));
            }

            Buyer savedBuyer = buyerRepository.save(existingBuyer);
            return ResponseEntity.ok(savedBuyer);
        }).orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteBuyer(@PathVariable String id, @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String token = authHeader.replace("Bearer", "").trim();

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
        }

        return buyerRepository.findById(id).map(buyer -> {
            buyerRepository.deleteById(id);
            return ResponseEntity.ok("Buyer deleted successfully!");
        }).orElse(ResponseEntity.notFound().build());
    }


}

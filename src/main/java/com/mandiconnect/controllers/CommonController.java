package com.mandiconnect.controllers;

import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.MarketRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonController {

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/addMarket")
    ResponseEntity<?> addMarket(@RequestHeader("Authorization") String authHeader, @RequestBody Market marketData) {

        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        Market savedMarket = marketRepository.save(marketData);

        return ResponseEntity.status(HttpStatus.OK).body(savedMarket);
    }

    @GetMapping("/getAllMarket")
    ResponseEntity<?> getAllMarket(@RequestHeader("Authorization") String authHeader ){
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        return ResponseEntity.status(HttpStatus.OK).body(marketRepository.findAll());

    }

    @PostMapping("/addCrop")
    ResponseEntity<?> addCrop(@RequestHeader("Authorization") String authHeader, @RequestBody Crops cropData) {
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        // Auto-assign displayUnit based on type
        switch (cropData.getType().toLowerCase()) {
            case "grain":
                cropData.setDisplayUnit("quintal");
                break;
            case "fruit":
            case "vegetable":
                cropData.setDisplayUnit("kg");
                break;
            case "leafy-vegetable":
                cropData.setDisplayUnit("judi");
                break;
            default:
                cropData.setDisplayUnit("kg"); // fallback
        }

        Crops savedCrop = cropRepository.save(cropData);

        return ResponseEntity.status(HttpStatus.OK).body(savedCrop);
    }


    @GetMapping("/getAllCrop")
    ResponseEntity<?> getAllCrop(@RequestHeader("Authorization") String authHeader){
        String token = authHeader.replace("Bearer", "").trim();
        boolean isVerfied = jwtUtil.validateToken(token);

        if (!isVerfied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UNAUTHORIZED");
        }

        return ResponseEntity.status(HttpStatus.OK).body(cropRepository.findAll());
    }

}

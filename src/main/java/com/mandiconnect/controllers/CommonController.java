package com.mandiconnect.controllers;

import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.MarketRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonController {

    @Autowired
    private MarketRepository marketRepository;

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


}

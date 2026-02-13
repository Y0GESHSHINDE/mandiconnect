package com.mandiconnect.controllers;

import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Market;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.repositories.MarketRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonController {

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/addMarket")
    ResponseEntity<?> addMarket( @RequestBody Market marketData) {

        Market savedMarket = marketRepository.save(marketData);

        return ResponseEntity.status(HttpStatus.OK).body("Market Added");
    }

    @GetMapping("/getAllMarket")
    ResponseEntity<?> getAllMarket(){
        return ResponseEntity.status(HttpStatus.OK).body(marketRepository.findAll());
    }

    @PostMapping("/addCrop")
    ResponseEntity<?> addCrop( @RequestBody Crops cropData) {

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

        return ResponseEntity.status(HttpStatus.OK).body("Crop Added");
    }


    @GetMapping("/getAllCrop")
    ResponseEntity<?> getAllCrop(){
        return ResponseEntity.status(HttpStatus.OK).body(cropRepository.findAll());
    }

}

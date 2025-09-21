//package com.mandiconnect.controllers;
package com.mandiconnect.controllers;
import com.mandiconnect.models.BuyerDemands;
import com.mandiconnect.repositories.BuyerDemandRepository;
import com.mandiconnect.repositories.BuyerRepository;
import com.mandiconnect.repositories.CropRepository;
import com.mandiconnect.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/buyerDemands")
public class BuyerMarketplaceController {

    @Autowired
    private BuyerDemandRepository buyerDemandRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private CropRepository cropRepository;

    @Autowired
    private JwtUtil jwtUtil;

    //add demand
    @PostMapping("/add")
    public ResponseEntity<?> addDemand(@RequestHeader("Authorization") String authHeader,
                                       @RequestBody BuyerDemands demand) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        if (demand.getBuyerId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Buyer ID cannot be null");
        }
        if (demand.getCropId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Crop ID cannot be null");
        }

        if (!buyerRepository.existsById(demand.getBuyerId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Buyer ID");
        }
        if (!cropRepository.existsById(demand.getCropId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Crop ID");
        }

        demand.setCreatedAt(new Date());
        demand.setUpdatedAt(new Date());

        BuyerDemands saved = buyerDemandRepository.save(demand);
        return ResponseEntity.ok(saved);
    }



    // Get all demands
    @GetMapping("/all")
    public ResponseEntity<?> getAllDemands(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        List<BuyerDemands> allDemands = buyerDemandRepository.findAll();
        return ResponseEntity.ok(allDemands);
    }

    //  Get demands by Buyer ID
    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<?> getDemandsByBuyer(@RequestHeader("Authorization") String authHeader,
                                               @PathVariable String buyerId) {
        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        if (!buyerRepository.existsById(buyerId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Buyer ID");
        }

        List<BuyerDemands> demands = buyerDemandRepository.findByBuyerId(buyerId);
        return ResponseEntity.ok(demands);
    }

    // Update status of a demand
    @PatchMapping("/updateStatus/{id}")
    public ResponseEntity<?> updateStatus(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable String id,
                                          @RequestBody Map<String, String> statusMap) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        BuyerDemands demand = buyerDemandRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demand not found"));

        String status = statusMap.get("status");
        if (!List.of("active", "fulfilled", "cancelled").contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid status value");
        }

        demand.setStatus(status);
        demand.setUpdatedAt(new Date());
        buyerDemandRepository.save(demand);

        return ResponseEntity.ok(demand);
    }

    //  Delete a demand
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteDemand(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable String id) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        return buyerDemandRepository.findById(id)
                .map(demand -> {
                    buyerDemandRepository.deleteById(id);
                    return ResponseEntity.ok("Demand deleted successfully!");
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Demand not found"));
    }

    //  Get demands by status (optional)
    @GetMapping("/status/{status}")
    public ResponseEntity<?> getDemandsByStatus(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable String status) {

        String token = authHeader.replace("Bearer", "").trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        if (!List.of("active", "fulfilled", "cancelled").contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid status");
        }

        List<BuyerDemands> demands = buyerDemandRepository.findByStatus(status);
        return ResponseEntity.ok(demands);
    }
}

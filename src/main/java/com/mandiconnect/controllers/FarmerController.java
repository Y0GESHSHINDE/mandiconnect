package com.mandiconnect.controllers;

import com.mandiconnect.Repositories.FarmerRepository;
import com.mandiconnect.models.Farmer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FarmerController {

    @Autowired
    private FarmerRepository farmerRepository;

    @GetMapping("/getFarmers")
    public List<Farmer> getAllFarmers() {
        return farmerRepository.findAll();
    }

    @PostMapping("/addFarmer")
    public Farmer addFarmer(@RequestBody Farmer farmer) {
        return farmerRepository.save(farmer);
    }
}

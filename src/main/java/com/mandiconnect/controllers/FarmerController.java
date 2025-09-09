package com.mandiconnect.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FarmerController {

    @GetMapping("/")
    String hello(){
        return  "hello Bro";
    }
}

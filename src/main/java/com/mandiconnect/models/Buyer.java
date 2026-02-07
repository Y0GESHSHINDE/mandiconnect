package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "buyers")
public class Buyer {

    @Id
    private String id;

    private String name;
    private String mobile;
    private String email;
    private String password;

    private String role = "BUYER";
    private boolean verified = false;

    private String companyName;
    private CompanyAddress companyAddress;

    private List<String> preferredCrops;

    // ---------- COMPANY ADDRESS ----------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyAddress {
        private String city;
        private String state;
        private String country;
    }
}

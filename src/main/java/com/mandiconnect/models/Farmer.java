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
@Document(collection = "farmers")
public class Farmer {

    @Id
    private String id;

    private String name;
    private String mobile;
    private String email;
    private String password;

    private String role = "FARMER";
    private boolean isVerified = false;

    private FarmerAddress farmerAddress;
    private FarmDetails farmDetails;

    // ---------------- ADDRESS ----------------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmerAddress {
        private String city;
        private String state;
        private String country;
    }

    // ---------------- FARM DETAILS ----------------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmDetails {

        private String farmSize;

        // ✅ THIS WILL NOW SAVE
        private List<String> cropIds;

        private List<String> preferredMarketIds;

        private String irrigationType;
        private String soilType;
    }
}
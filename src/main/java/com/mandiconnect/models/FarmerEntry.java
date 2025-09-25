package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "farmerEntries")
public class FarmerEntry {

    @Id
    private String id;

    @DBRef
    private Farmer farmer;   // reference to Farmer document

    @DBRef
    private Crops crop;       // reference to Crop document

    @DBRef
    private Market market;    // reference to Market/Mandi document

    private double price;
    private String quantity;

    private String photo;       // Cloudinary URL
    private String status;      // active / sold / expired
    private LocalDateTime createdAt = LocalDateTime.now();

    private Feedback feedback = new Feedback();

    // --- Inner Classes ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Feedback {
        private int agreeCount = 0;
        private int disagreeCount = 0;
        private List<String> votedFarmers; // farmer IDs who voted
    }
}

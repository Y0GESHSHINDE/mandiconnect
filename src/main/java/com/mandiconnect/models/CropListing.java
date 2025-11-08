package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "listedCrop")
public class CropListing {

    @Id
    private String id;

    @DBRef
    private Farmer farmer;  // full Farmer object

    @DBRef
    private Crops crop;     // full Crop object

    private double quantity;
    private String unit;
    private double price;
    private Location location;
    private String photoUrl;
    private String publicId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String village ;
        private String city;
        private String state;
        private String country;
    }
}

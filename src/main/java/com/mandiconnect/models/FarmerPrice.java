package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "farmer_prices")
@CompoundIndex(name = "unique_price_per_day", def = "{'farmerId': 1, 'cropId': 1, 'marketId': 1, 'date': 1}", unique = true)
public class FarmerPrice {

    @Id
    private String id;

    private String farmerId;   // Reference to Farmer
    private String cropId;     // Reference to Crop
    private String marketId;   // Reference to Market

    private LocalDate date;    // The date for which this price applies
    private Double price;
    private String unit;       // e.g., "kg"
    private String currency;   // e.g., "INR"

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters and setters
}

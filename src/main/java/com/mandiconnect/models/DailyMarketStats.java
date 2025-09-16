package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "daily_market_stats")
public class DailyMarketStats {

    @Id
    private String id;

    private String cropId;     // Reference to Crop
    private String marketId;   // Reference to Market
    private LocalDate date;    // The date for which stats are calculated

    private Double averagePrice;
    private Double maxPrice;
    private Double minPrice;
    private Integer totalFarmers;

    private String unit;
    private String currency;

    private LocalDateTime updatedAt;

    // getters and setters
}

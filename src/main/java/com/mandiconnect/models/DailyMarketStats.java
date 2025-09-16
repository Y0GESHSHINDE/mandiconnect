package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "daily_market_stats")
public class DailyMarketStats {

    @Id
    private String id;

    @DBRef
    private Crops  crop;     // full Crop document reference

    @DBRef
    private Market market; // full Market document reference

    private LocalDate date;
    private Double averagePrice;
    private Double minPrice;
    private Double maxPrice;
    private Integer totalFarmers;
    private String unit;
    private String currency;
    private LocalDateTime updatedAt;

}

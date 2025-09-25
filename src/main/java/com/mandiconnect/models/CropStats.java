package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "cropStats")
public class CropStats {

    @Id
    private String id;

    @DBRef  // Add this annotation
    private Crops crop;

    @DBRef  // Add this annotation
    private Market mandi;

    private Stats allTimeStats;
    private Stats dailyStats;
    private Stats weeklyStats;
    private List<Trend> trend = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Stats {
        private double averagePrice;
        private double minPrice;
        private double maxPrice;
        private int totalFarmersContributed;
        private LocalDateTime lastUpdated;
        private boolean isStale;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Trend {
        private LocalDateTime date;
        private double averagePrice;
        private double minPrice;
        private double maxPrice;
        private int farmersContributed;
    }
}
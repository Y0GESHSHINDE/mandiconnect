package com.mandiconnect.repositories;

import com.mandiconnect.models.DailyMarketStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyMarketStatsRepository extends MongoRepository<DailyMarketStats, String> {

    // Find stats for a crop in a market on a given date
    Optional<DailyMarketStats> findByCropIdAndMarketIdAndDate(String cropId, String marketId, LocalDate date);

    // (Optional) Find all stats for a crop in a market (useful for historical charts)
    List<DailyMarketStats> findByCropIdAndMarketIdOrderByDateAsc(String cropId, String marketId);
}

package com.mandiconnect.repositories;

import com.mandiconnect.models.Crops;
import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.models.Market;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FarmerEntryRepository extends MongoRepository<FarmerEntry, String> {

    // Query by DBRef objects
    List<FarmerEntry> findByCropAndMarket(Crops crop, Market market);

    List<FarmerEntry> findByCropAndMarketAndCreatedAtAfter(Crops crop, Market market, LocalDateTime date);

    List<FarmerEntry> findByFarmerId(String farmerId);

    // Exact date range query
    @Query("{ 'crop': ?0, 'market': ?1, 'createdAt': { $gte: ?2, $lt: ?3 } }")
    List<FarmerEntry> findByCropAndMarketAndCreatedAtBetween(Crops crop, Market market, LocalDateTime startDate, LocalDateTime endDate);
}
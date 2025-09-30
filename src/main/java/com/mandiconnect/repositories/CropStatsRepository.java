package com.mandiconnect.repositories;

import com.mandiconnect.models.CropStats;
import com.mandiconnect.models.Crops;
import com.mandiconnect.models.Market;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CropStatsRepository extends MongoRepository<CropStats, String> {

    // Query by DBRef objects
    Optional<CropStats> findByCropAndMandi(Crops crop, Market mandi);

    // Alternative by IDs
    Optional<CropStats> findByCropIdAndMandiId(String cropId, String mandiId);
    Optional<CropStats> findByMandiId(String marketId);
    Optional<CropStats> findByCropId(String cropId);
}

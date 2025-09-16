package com.mandiconnect.repositories;

import com.mandiconnect.models.FarmerPrice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FarmerPriceRepository extends MongoRepository<FarmerPrice, String> {

    // Find all prices for a crop in a market on a given date
    List<FarmerPrice> findByCropIdAndMarketIdAndDate(String cropId, String marketId, LocalDate date);

    // Find one farmer’s price entry for a crop in a market on a given date
    Optional<FarmerPrice> findByFarmerIdAndCropIdAndMarketIdAndDate(
            String farmerId,
            String cropId,
            String marketId,
            LocalDate date
    );
}

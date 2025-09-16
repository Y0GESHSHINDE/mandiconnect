package com.mandiconnect.repositories;

import com.mandiconnect.models.Market;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MarketRepository extends MongoRepository<Market , String> {
    Optional<Market> findById(String id);
}

package com.mandiconnect.Repositories;

import com.mandiconnect.models.Farmer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmerRepository extends MongoRepository<Farmer, String> {
    // You can add custom queries if needed
}


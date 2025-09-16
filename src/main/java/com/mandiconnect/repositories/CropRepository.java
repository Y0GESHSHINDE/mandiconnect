package com.mandiconnect.repositories;

import com.mandiconnect.models.Crops;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CropRepository extends MongoRepository<Crops , String> {
    Optional<Crops> findById(String id);
}

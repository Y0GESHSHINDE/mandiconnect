package com.mandiconnect.repositories;

import com.mandiconnect.models.CropListing;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CropListingRepository extends MongoRepository<CropListing , String> {
    Optional<CropListing> findById(String s);
}

package com.mandiconnect.repositories;

import com.mandiconnect.models.Buyer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BuyerRepository extends MongoRepository<Buyer, String> {

    Optional<Buyer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);
}

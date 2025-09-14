package com.mandiconnect.repositories;

import com.mandiconnect.models.BuyerVerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BuyerVerificationTokenRepository extends MongoRepository<BuyerVerificationToken, String> {

    Optional<BuyerVerificationToken> findByToken(String token);

}

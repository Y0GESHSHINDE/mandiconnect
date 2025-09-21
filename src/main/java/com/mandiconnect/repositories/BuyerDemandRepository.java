package com.mandiconnect.repositories;

import com.mandiconnect.models.BuyerDemands;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BuyerDemandRepository extends MongoRepository<BuyerDemands,String > {

    List<BuyerDemands> findByBuyerId(String buyerId);

    List<BuyerDemands> findByStatus(String status);

}

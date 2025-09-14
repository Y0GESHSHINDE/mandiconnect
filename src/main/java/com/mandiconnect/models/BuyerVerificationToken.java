package com.mandiconnect.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Document(collection = "buyer_verification_tokens")
public class BuyerVerificationToken {

    @Id
    private String id;

    @Field("token")
    private String token;

    @Field("buyerId")
    private String buyerId;

    @Field("expiryDate")
    private Date expiryDate;

}

package com.mandiconnect.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
public class BuyerDemands {
    @Id
    private String id;

    @JsonProperty("BuyerId")
    @Field("BuyerId")
    private String buyerId;   // referencing Buyer

    @JsonProperty("CropId")
    @Field("CropId")
    private String cropId;    // can later change to ObjectId if you make Crop model

    @JsonProperty("RequiredQuantity")
    @Field("RequiredQuantity")
    private RequiredQuantity requiredQuantity;

    @JsonProperty("ExpectedPrice")
    @Field("ExpectedPrice")
    private ExpectedPrice expectedPrice;

    @JsonProperty("Status")
    @Field("Status")
    private String status = "active"; // active | fulfilled | cancelled

    @JsonProperty("CreatedAt")
    @Field("CreatedAt")
    private Date createdAt = new Date();

    @JsonProperty("UpdatedAt")
    @Field("UpdatedAt")
    private Date updatedAt = new Date();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequiredQuantity {
        @JsonProperty("Value")
        @Field("Value")
        private double value;

        @JsonProperty("Unit")
        @Field("Unit")
        private String unit; // kg | quintal | ton | bundals
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpectedPrice {
        @JsonProperty("Value")
        @Field("Value")
        private double value;

        @JsonProperty("Currency")
        @Field("Currency")
        private String currency = "INR";
    }
}

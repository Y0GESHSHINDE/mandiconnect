package com.mandiconnect.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "markets")
public class Market {

    @Id
    private String id;

    @Field("marketName") // MongoDB field name
    @JsonProperty("marketName") // JSON request/response field name
    private String marketName;

    private MarketAddress marketAddress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketAddress {

        @Field("city")
        @JsonProperty("city")
        private String city;

        @Field("state")
        @JsonProperty("state")
        private String state;

        @Field("country")
        @JsonProperty("country")
        private String country;
    }
}

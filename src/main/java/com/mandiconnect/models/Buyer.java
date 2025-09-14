package com.mandiconnect.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Buyer {

    @Id
//    @Field("Id")
    private String id;

    @JsonProperty("Name")
    @Field("Name")
    private String name;

    @JsonProperty("Mobile")
    @Field("Mobile")
    private String mobile;

    @JsonProperty("Email")
    @Field("Email")
    private String email;

    @JsonProperty("Company Name")
    @Field("Company Name")
    private String companyName;

    @JsonProperty("Company Address")
    @Field("Company Address")
    private CompanyAddress companyAddress;

    @JsonProperty("PreferredCrops")
    @Field("PreferredCrops")
    private List<String> preferredCrops;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyAddress{

        @JsonProperty("City")
        @Field("City")
        private String city;

        @JsonProperty("State")
        @Field("State")
        private String state;

        @JsonProperty("Country")
        @Field("Country")
        private String country;
    }

    @JsonProperty("Password")
    @Field("Password")
    private String password;
    private boolean verified = false;
}

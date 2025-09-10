package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "farmers")
public class Farmer {

    @Id
    private String id;  // MongoDB will generate _id automatically


    @Field("Name")
    private String name;

    @Field("Mobile")
    private String mobile;

    @Field("Email")
    private String email;

    @Field("Farmer Address")
    private FarmerAddress farmerAddress;

    @Field("FarmDetails")
    private FarmDetails farmDetails;

    // ---------- Inner Classes ----------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmerAddress {
        @Field("City")
        private String city;

        @Field("State")
        private String state;

        @Field("Country")
        private String country;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmDetails {
        @Field("FarmSize")
        private String farmSize;

        @Field("CropsGrown")
        private List<String> cropsGrown;

        @Field("IrrigationType")
        private String irrigationType;

        @Field("soilType")
        private String soilType;
    }
}

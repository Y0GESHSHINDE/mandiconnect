package com.mandiconnect.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "crops")
public class Crops {

    @Id
    private String id;

    private String name;
    private String scientificName;
    private String category;       // Grain, Fruit, Vegetable, etc.
    private String variety;
    private String description;
}

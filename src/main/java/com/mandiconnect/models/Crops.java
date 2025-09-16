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
    private String type;        // e.g., "grain", "vegetable", "leafy-vegetable"
    private String displayUnit; // e.g., "quintal" for grains, "kg" for vegetables, "bundel(judi)" for leafy      // Grain, Fruit, Vegetable, etc.
    private String variety;
}

package com.mycodefu.mongodb;

import com.mongodb.client.model.search.SearchOperator;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SearchFilters(
        Boolean hasPerson,
        List<String> animal,
        List<String> appliance,
        List<String> electronic,
        List<String> food,
        List<String> furniture,
        List<String> indoor,
        List<String> kitchen,
        List<String> outdoor,
        List<String> sports,
        List<String> vehicle
) {
    public SearchFilters {
        animal = normalise(animal);
        appliance = normalise(appliance);
        electronic = normalise(electronic);
        food = normalise(food);
        furniture = normalise(furniture);
        indoor = normalise(indoor);
        kitchen = normalise(kitchen);
        outdoor = normalise(outdoor);
        sports = normalise(sports);
        vehicle = normalise(vehicle);
    }

    public static SearchFilters empty() {
        return new SearchFilters(null, null, null, null, null, null, null, null, null, null, null);
    }

    public List<SearchOperator> toSearchClauses() {
        ArrayList<SearchOperator> clauses = new ArrayList<>();
        if (hasPerson != null) {
            clauses.add(ImageDataAccess.equalsClause("hasPerson", hasPerson));
        }
        for (Map.Entry<String, List<String>> entry : categories().entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                clauses.add(ImageDataAccess.equalsClause(entry.getKey(), value));
            }
        }
        return clauses;
    }

    public Document toVectorFilterDocument() {
        ArrayList<Document> clauses = new ArrayList<>();
        if (hasPerson != null) {
            clauses.add(new Document("hasPerson", hasPerson));
        }
        for (Map.Entry<String, List<String>> entry : categories().entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                clauses.add(new Document(entry.getKey(), value));
            }
        }
        if (clauses.isEmpty()) {
            return new Document();
        }
        if (clauses.size() == 1) {
            return clauses.getFirst();
        }
        return new Document("$and", clauses);
    }

    private Map<String, List<String>> categories() {
        LinkedHashMap<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("animal", animal);
        categories.put("appliance", appliance);
        categories.put("electronic", electronic);
        categories.put("food", food);
        categories.put("furniture", furniture);
        categories.put("indoor", indoor);
        categories.put("kitchen", kitchen);
        categories.put("outdoor", outdoor);
        categories.put("sports", sports);
        categories.put("vehicle", vehicle);
        return categories;
    }

    private static List<String> normalise(List<String> values) {
        return values == null ? null : values.stream().filter(Objects::nonNull).toList();
    }
}

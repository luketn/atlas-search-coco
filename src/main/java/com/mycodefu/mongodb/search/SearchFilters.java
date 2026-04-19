package com.mycodefu.mongodb.search;

import com.mongodb.client.model.search.SearchOperator;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

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
        return buildClauses(SearchFilters::equalsClause);
    }

    public Document toVectorFilterDocument() {
        ArrayList<Document> clauses = buildClauses(Document::new);
        if (clauses.isEmpty()) {
            return new Document();
        }
        if (clauses.size() == 1) {
            return clauses.getFirst();
        }
        return new Document("$and", clauses);
    }

    private <T> ArrayList<T> buildClauses(BiFunction<String, Object, T> clauseFactory) {
        ArrayList<T> clauses = new ArrayList<>();
        if (hasPerson != null) {
            clauses.add(clauseFactory.apply("hasPerson", hasPerson));
        }
        for (SearchCategory category : SearchCategory.filterable()) {
            List<String> values = category.filterValues(this);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                clauses.add(clauseFactory.apply(category.fieldName(), value));
            }
        }
        return clauses;
    }

    private static SearchOperator equalsClause(String fieldName, Object value) {
        return SearchOperator.of(new Document("equals", new Document("path", fieldName).append("value", value)));
    }

    private static List<String> normalise(List<String> values) {
        return values == null ? null : values.stream().filter(Objects::nonNull).toList();
    }
}

package com.mycodefu.mongodb.atlas;

import com.mongodb.client.model.search.SearchOperator;
import org.bson.BsonDateTime;
import org.bson.Document;

import java.time.Instant;
import java.util.List;

public class AtlasSearchUtils {

    public static SearchOperator fuzzyText(String path, String query) {
        return SearchOperator.of(new Document("text", new Document()
                .append("path", path)
                .append("query", query)
                .append("fuzzy", new Document(
                        "maxEdits", 1
                ))
        ));
    }

    public static SearchOperator phrase(String path, String query) {
        return SearchOperator.of(new Document("phrase", new Document()
                .append("path", path)
                .append("query", query)
        ));
    }

    public static SearchOperator wildcard(String path, String query) {
        return SearchOperator.of(new Document("wildcard", new Document()
                .append("path", path)
                .append("query", query)
                .append("allowAnalyzedField", true)
        ));
    }

    public static SearchOperator queryString(String defaultPath, String query) {
        return SearchOperator.of(new Document("queryString", new Document()
                .append("defaultPath", defaultPath)
                .append("query", query)
        ));
    }

    public static SearchOperator in(String path, List<?> value) {
        return searchOperator("in", path, value);
    }

    public static SearchOperator isEqual(String path, Object value) {
        return searchOperator("equals", path, value);
    }

    public static SearchOperator searchOperator(String operator, String path, Object value) {
        return SearchOperator.of(new Document(operator, new Document()
                .append("path", path)
                .append("value", value)
        ));
    }

    public static SearchOperator range(String key, Instant from, Instant to) {
        Document rangeDocument = new Document()
                .append("path", key)
                .append("gte", new BsonDateTime(from.toEpochMilli()));

        if (to != null) {
            rangeDocument.append("lte", new BsonDateTime(to.toEpochMilli()));
        }
        return SearchOperator.of(new Document("range", rangeDocument));
    }

    public static Document range(String key, Long from, Long to) {
        Document rangeDocument = new Document()
                .append("path", key)
                .append("gte", from);

        if (to != null) {
            rangeDocument.append("lte", to);
        }
        return new Document("range", rangeDocument);
    }
}

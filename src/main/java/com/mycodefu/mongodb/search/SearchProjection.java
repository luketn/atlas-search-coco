package com.mycodefu.mongodb.search;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public final class SearchProjection {
    private static final List<String> IMAGE_FIELDS = buildImageFields();
    private static final List<String> LICENSE_FIELDS = List.of("licenseName", "licenseUrl");

    private SearchProjection() {
    }

    public static Document projectStage(boolean includeLicense) {
        return new Document("$project", imageProjection(includeLicense));
    }

    public static Document facetMetaStage() {
        return new Document("$project", new Document("hasMore", null).append("facet", "$facet"));
    }

    public static Document emptyMetaStage() {
        return new Document("$project", new Document("hasMore", null).append("facet", null));
    }

    public static Document imageProjection(boolean includeLicense) {
        Document projection = new Document();
        for (String field : IMAGE_FIELDS) {
            projection.append(field, 1);
        }
        if (includeLicense) {
            for (String field : LICENSE_FIELDS) {
                projection.append(field, 1);
            }
        }
        return projection;
    }

    private static List<String> buildImageFields() {
        ArrayList<String> fields = new ArrayList<>(List.of(
                "_id",
                "caption",
                "url",
                "height",
                "width",
                "dateCaptured",
                "hasPerson",
                "accessory"
        ));
        for (SearchCategory category : SearchCategory.filterable()) {
            fields.add(category.fieldName());
        }
        return List.copyOf(fields);
    }
}

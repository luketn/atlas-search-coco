package com.mycodefu.mongodb.search;

import org.bson.Document;

import java.util.List;

public final class SearchProjection {
    private static final List<String> IMAGE_FIELDS = List.of(
            "_id",
            "caption",
            "url",
            "height",
            "width",
            "dateCaptured",
            "hasPerson",
            "accessory",
            "animal",
            "appliance",
            "electronic",
            "food",
            "furniture",
            "indoor",
            "kitchen",
            "outdoor",
            "sports",
            "vehicle"
    );
    private static final List<String> LICENSE_FIELDS = List.of("licenseName", "licenseUrl");

    private SearchProjection() {
    }

    public static Document imageProjection(boolean includeLicense, boolean includeHybridScore) {
        Document projection = new Document();
        for (String field : IMAGE_FIELDS) {
            projection.append(field, 1);
        }
        if (includeLicense) {
            for (String field : LICENSE_FIELDS) {
                projection.append(field, 1);
            }
        }
        if (includeHybridScore) {
            projection.append("hybridScore", 1);
        }
        return projection;
    }
}

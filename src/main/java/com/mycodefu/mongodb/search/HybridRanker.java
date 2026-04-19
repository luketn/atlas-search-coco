package com.mycodefu.mongodb.search;

import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HybridRanker {
    private static final int HYBRID_RANK_OFFSET = 60;

    private HybridRanker() {
    }

    public static List<Image> fuse(List<Image> textResults, List<Image> vectorResults) {
        Map<Integer, Image> imageById = new LinkedHashMap<>();
        Map<Integer, Double> scoreById = new HashMap<>();

        applyReciprocalRankScores(textResults, imageById, scoreById);
        applyReciprocalRankScores(vectorResults, imageById, scoreById);

        return scoreById.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> imageById.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    public static ImageSearchResult.ImageMeta buildMeta(List<Image> rankedImages) {
        return new ImageSearchResult.ImageMeta(
                new ImageSearchResult.ImageMetaTotal(rankedImages.size()),
                new ImageSearchResult.ImageMetaFacets(
                        new ImageSearchResult.ImageMetaFacet(List.of()),
                        facetBuckets(rankedImages, "animal"),
                        facetBuckets(rankedImages, "appliance"),
                        facetBuckets(rankedImages, "electronic"),
                        facetBuckets(rankedImages, "food"),
                        facetBuckets(rankedImages, "furniture"),
                        facetBuckets(rankedImages, "indoor"),
                        facetBuckets(rankedImages, "kitchen"),
                        facetBuckets(rankedImages, "outdoor"),
                        facetBuckets(rankedImages, "sports"),
                        facetBuckets(rankedImages, "vehicle")
                )
        );
    }

    private static void applyReciprocalRankScores(List<Image> rankedImages, Map<Integer, Image> imageById, Map<Integer, Double> scoreById) {
        for (int i = 0; i < rankedImages.size(); i++) {
            Image image = rankedImages.get(i);
            imageById.putIfAbsent(image._id(), image);
            scoreById.merge(image._id(), 1.0 / (HYBRID_RANK_OFFSET + i + 1), Double::sum);
        }
    }

    private static ImageSearchResult.ImageMetaFacet facetBuckets(List<Image> rankedImages, String facetName) {
        HashMap<String, Long> counts = new HashMap<>();
        for (Image image : rankedImages) {
            List<String> values = facetValues(image, facetName);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                counts.put(value, counts.getOrDefault(value, 0L) + 1);
            }
        }

        List<ImageSearchResult.ImageMetaFacetBucket> buckets = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(entry -> new ImageSearchResult.ImageMetaFacetBucket(entry.getKey(), entry.getValue()))
                .toList();
        return new ImageSearchResult.ImageMetaFacet(buckets);
    }

    private static List<String> facetValues(Image image, String facetName) {
        return switch (facetName) {
            case "animal" -> image.animal();
            case "appliance" -> image.appliance();
            case "electronic" -> image.electronic();
            case "food" -> image.food();
            case "furniture" -> image.furniture();
            case "indoor" -> image.indoor();
            case "kitchen" -> image.kitchen();
            case "outdoor" -> image.outdoor();
            case "sports" -> image.sports();
            case "vehicle" -> image.vehicle();
            default -> null;
        };
    }
}

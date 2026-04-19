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
        Map<SearchCategory, ImageSearchResult.ImageMetaFacet> facetByCategory = new LinkedHashMap<>();
        for (SearchCategory category : SearchCategory.filterable()) {
            facetByCategory.put(category, facetBuckets(rankedImages, category));
        }

        return new ImageSearchResult.ImageMeta(
                new ImageSearchResult.ImageMetaTotal(rankedImages.size()),
                new ImageSearchResult.ImageMetaFacets(
                        new ImageSearchResult.ImageMetaFacet(List.of()),
                        facetByCategory.get(SearchCategory.ANIMAL),
                        facetByCategory.get(SearchCategory.APPLIANCE),
                        facetByCategory.get(SearchCategory.ELECTRONIC),
                        facetByCategory.get(SearchCategory.FOOD),
                        facetByCategory.get(SearchCategory.FURNITURE),
                        facetByCategory.get(SearchCategory.INDOOR),
                        facetByCategory.get(SearchCategory.KITCHEN),
                        facetByCategory.get(SearchCategory.OUTDOOR),
                        facetByCategory.get(SearchCategory.SPORTS),
                        facetByCategory.get(SearchCategory.VEHICLE)
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

    private static ImageSearchResult.ImageMetaFacet facetBuckets(List<Image> rankedImages, SearchCategory category) {
        HashMap<String, Long> counts = new HashMap<>();
        for (Image image : rankedImages) {
            List<String> values = category.imageValues(image);
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
}

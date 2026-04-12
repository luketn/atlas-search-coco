package com.mycodefu.k6;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class K6DataGenerator {
    private static final Logger log = LoggerFactory.getLogger(K6DataGenerator.class);

    private static final ObjectMapper json = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final List<String> supportedFacetNames = List.of(
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

    private static final Set<String> stopWords = Set.of(
            "a", "about", "above", "across", "after", "all", "along", "an", "and", "another",
            "are", "around", "as", "at", "background", "be", "been", "beside", "by", "for",
            "from", "group", "has", "holding", "in", "into", "is", "it", "its", "large", "near",
            "of", "on", "one", "or", "over", "people", "person", "photo", "small", "some",
            "standing", "sits", "sitting", "that", "the", "their", "there", "these", "this",
            "three", "two", "under", "up", "with"
    );

    private static final Pattern wordPattern = Pattern.compile("[a-z0-9]+(?:'[a-z0-9]+)?");

    static void main(String[] args) throws IOException {
        GeneratorConfig config = GeneratorConfig.fromArgs(args);
        DatasetSource datasetSource = loadDataset(config.maxImages());
        List<SearchRequestSample> samples = buildSamples(datasetSource.categories(), datasetSource.images(), config.maxSamples());
        writeJsonLines(config.outputFile(), samples);

        log.info(
                "Wrote {} search samples to {} using {} data.",
                samples.size(),
                config.outputFile().toAbsolutePath(),
                datasetSource.description()
        );
    }

    private static DatasetSource loadDataset(int maxImages) {
        try {
            MongoDatabase database = MongoConnection.connection().getDatabase(MongoConnection.database_name);
            MongoCollection<Category> categoryCollection = database.getCollection(CategoryDataAccess.collection_name, Category.class);
            MongoCollection<Image> imageCollection = database.getCollection(ImageDataAccess.collection_name, Image.class);

            List<Category> categories = categoryCollection.find().into(new ArrayList<>());
            List<Image> images = imageCollection.find().limit(maxImages).into(new ArrayList<>());
            if (categories.isEmpty() || images.isEmpty()) {
                throw new IllegalStateException("MongoDB dataset is empty.");
            }

            return new DatasetSource(categories, images, "MongoDB");
        } catch (Exception e) {
            log.warn("Falling back to sample data because MongoDB data could not be loaded: {}", e.getMessage());

            List<Category> categories = Arrays.asList(JsonUtil.readFile(
                    Path.of("src", "test", "resources", "sample-data", "category.json").toFile(),
                    Category[].class
            ));
            List<Image> images = Arrays.asList(JsonUtil.readFile(
                    Path.of("src", "test", "resources", "sample-data", "image.json").toFile(),
                    Image[].class
            ));

            int imageLimit = Math.min(images.size(), maxImages);
            return new DatasetSource(categories, images.subList(0, imageLimit), "sample test resource");
        }
    }

    private static List<SearchRequestSample> buildSamples(List<Category> categories, List<Image> images, int maxSamples) {
        Map<String, Set<String>> validCategoriesByFacet = validCategoriesByFacet(categories);
        LinkedHashSet<SearchRequestSample> samples = new LinkedHashSet<>();

        for (int imageIndex = 0; imageIndex < images.size() && samples.size() < maxSamples; imageIndex++) {
            Image image = images.get(imageIndex);
            List<String> textTerms = prioritizedTextTerms(image);
            if (textTerms.isEmpty()) {
                continue;
            }

            List<FacetValue> facetValues = facetValues(image, validCategoriesByFacet);
            String primaryText = textTerms.getFirst();

            addSample(samples, SearchRequestSample.of(primaryText, 0, null, List.of()), maxSamples);
            if (imageIndex % 6 == 0) {
                addSample(samples, SearchRequestSample.of(primaryText, 1, null, List.of()), maxSamples);
            }
            if (image.hasPerson()) {
                addSample(samples, SearchRequestSample.of(primaryText, 0, true, List.of()), maxSamples);
            }
            if (!facetValues.isEmpty()) {
                addSample(samples, SearchRequestSample.of(primaryText, 0, null, List.of(facetValues.getFirst())), maxSamples);
            }
            if (facetValues.size() > 1) {
                String alternateText = textTerms.size() > 1 ? textTerms.get(1) : primaryText;
                addSample(
                        samples,
                        SearchRequestSample.of(alternateText, 0, image.hasPerson() ? true : null, List.of(facetValues.get(0), facetValues.get(1))),
                        maxSamples
                );
            }
            if (facetValues.size() > 2) {
                String alternateText = textTerms.size() > 2 ? textTerms.get(2) : primaryText;
                addSample(
                        samples,
                        SearchRequestSample.of(alternateText, 0, null, List.of(facetValues.get(1), facetValues.get(2))),
                        maxSamples
                );
            }
        }

        return new ArrayList<>(samples);
    }

    private static void addSample(LinkedHashSet<SearchRequestSample> samples, SearchRequestSample sample, int maxSamples) {
        if (samples.size() < maxSamples) {
            samples.add(sample);
        }
    }

    private static Map<String, Set<String>> validCategoriesByFacet(List<Category> categories) {
        Map<String, Set<String>> categoriesByFacet = new LinkedHashMap<>();
        for (Category category : categories) {
            if (!supportedFacetNames.contains(category.superCategory())) {
                continue;
            }
            categoriesByFacet.computeIfAbsent(category.superCategory(), ignored -> new LinkedHashSet<>()).add(category.name());
        }
        return categoriesByFacet;
    }

    private static List<String> prioritizedTextTerms(Image image) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String caption = image.caption();
        if (caption == null || caption.isBlank()) {
            return List.of();
        }

        String normalisedCaption = caption.toLowerCase(Locale.ROOT);
        for (String categoryValue : categoryValues(image)) {
            if (normalisedCaption.contains(categoryValue.toLowerCase(Locale.ROOT))) {
                terms.add(categoryValue);
            }
        }

        Matcher matcher = wordPattern.matcher(normalisedCaption);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 || stopWords.contains(token)) {
                continue;
            }
            terms.add(token);
        }

        return new ArrayList<>(terms);
    }

    private static List<String> categoryValues(Image image) {
        ArrayList<String> values = new ArrayList<>();
        for (String facetName : supportedFacetNames) {
            List<String> facetValues = facetValuesForImage(image).get(facetName);
            if (facetValues != null) {
                values.addAll(facetValues);
            }
        }
        return values;
    }

    private static List<FacetValue> facetValues(Image image, Map<String, Set<String>> validCategoriesByFacet) {
        ArrayList<FacetValue> facetValues = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : facetValuesForImage(image).entrySet()) {
            List<String> values = entry.getValue();
            if (values == null) {
                continue;
            }

            Set<String> validValues = validCategoriesByFacet.getOrDefault(entry.getKey(), Set.of());
            for (String value : values) {
                if (validValues.contains(value)) {
                    facetValues.add(new FacetValue(entry.getKey(), value));
                }
            }
        }
        return facetValues;
    }

    private static Map<String, List<String>> facetValuesForImage(Image image) {
        LinkedHashMap<String, List<String>> facets = new LinkedHashMap<>();
        facets.put("animal", image.animal());
        facets.put("appliance", image.appliance());
        facets.put("electronic", image.electronic());
        facets.put("food", image.food());
        facets.put("furniture", image.furniture());
        facets.put("indoor", image.indoor());
        facets.put("kitchen", image.kitchen());
        facets.put("outdoor", image.outdoor());
        facets.put("sports", image.sports());
        facets.put("vehicle", image.vehicle());
        return facets;
    }

    private static void writeJsonLines(Path outputFile, List<SearchRequestSample> samples) throws IOException {
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (SearchRequestSample sample : samples) {
                writer.write(json.writeValueAsString(sample));
                writer.newLine();
            }
        }
    }

    private record GeneratorConfig(Path outputFile, int maxSamples, int maxImages) {
        private static GeneratorConfig fromArgs(String[] args) {
            Path outputFile = Path.of("k6-data.jsonl");
            int maxSamples = 1000;
            int maxImages = 3000;

            for (String arg : args) {
                if (arg.startsWith("--output=")) {
                    outputFile = Path.of(arg.substring("--output=".length()));
                } else if (arg.startsWith("--maxSamples=")) {
                    maxSamples = Integer.parseInt(arg.substring("--maxSamples=".length()));
                } else if (arg.startsWith("--maxImages=")) {
                    maxImages = Integer.parseInt(arg.substring("--maxImages=".length()));
                }
            }

            return new GeneratorConfig(outputFile, maxSamples, maxImages);
        }
    }

    private record DatasetSource(List<Category> categories, List<Image> images, String description) { }

    private record FacetValue(String name, String value) { }

    public record SearchRequestSample(
            String text,
            Integer page,
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
        private static SearchRequestSample of(String text, Integer page, Boolean hasPerson, List<FacetValue> facetValues) {
            Map<String, List<String>> facets = new LinkedHashMap<>();
            for (FacetValue facetValue : facetValues) {
                facets.computeIfAbsent(facetValue.name(), ignored -> new ArrayList<>()).add(facetValue.value());
            }

            return new SearchRequestSample(
                    text,
                    page,
                    hasPerson,
                    facets.get("animal"),
                    facets.get("appliance"),
                    facets.get("electronic"),
                    facets.get("food"),
                    facets.get("furniture"),
                    facets.get("indoor"),
                    facets.get("kitchen"),
                    facets.get("outdoor"),
                    facets.get("sports"),
                    facets.get("vehicle")
            );
        }
    }
}

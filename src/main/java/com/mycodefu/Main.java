package com.mycodefu;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.lmstudio.LMStudioVisionModel;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.QueryStats;
import com.mycodefu.model.SearchCapabilities;
import com.mycodefu.model.SearchType;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.mongodb.search.SearchFilters;
import com.mycodefu.mongodb.search.SearchCategory;
import com.mycodefu.mongodb.search.SearchRequest;
import com.mycodefu.service.SimpleServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mycodefu.datapreparation.PrepareDataEntryPoint.downloadAndInitialiseDataset;
import static com.mycodefu.datapreparation.PrepareDataEntryPoint.loadPreparedDataset;

public class Main {
    private static final Pattern totalJavaTimePattern = Pattern.compile("(\"totalJavaTimeMs\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)");

    public static void main(String[] args) throws IOException {
        boolean lmStudioCaptioningEnabled = List.of(args).contains("--enableUploadsWithLMStudioCaptioning");
        if (args.length > 0) {
            switch (args[0]) {
                case "--loadData" -> downloadAndInitialiseDataset(false);
                case "--lmStudioVectorEmbeddings" -> downloadAndInitialiseDataset(true);
                case "--enableUploadsWithLMStudioCaptioning" -> {
                    // Server-only feature flag. No startup data load is required.
                }
                case "--loadDataFromDirectory" -> {
                    if (args.length < 2) {
                        throw new IllegalArgumentException("Missing directory argument for --loadDataFromDirectory");
                    }
                    loadPreparedDataset(Path.of(args[1]));
                }
                default -> System.out.println("Unsupported argument. Supported arguments: --loadData, --lmStudioVectorEmbeddings, --loadDataFromDirectory <directory>, --enableUploadsWithLMStudioCaptioning");
            }
        }

        boolean vectorSearchEnabledAtStartup;
        try (ImageDataAccess imageDataAccess = ImageDataAccess.getInstance()) {
            vectorSearchEnabledAtStartup = imageDataAccess.refreshVectorSearchIndexState().available();
        }

        SearchCapabilities searchCapabilities = new SearchCapabilities(vectorSearchEnabledAtStartup, lmStudioCaptioningEnabled);

        CategoryDataAccess categoryDataAccess = CategoryDataAccess.getInstance();
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();

        SimpleServer.SimpleServerBuilder serverBuilder = SimpleServer.create(8222)
                .addGetHandler("/", _ -> {
                    try {
                        return new String(Main.class.getResourceAsStream("/static/index.html").readAllBytes());
                    } catch (IOException e) {
                        return "Error loading index.html: " + e.getMessage();
                    }
                })
                .addGetHandler("/capabilities", _ -> JsonUtil.writeToString(searchCapabilities))
                .addGetHandler("/categories", _ -> {
                    List<Category> categories = categoryDataAccess.list();
                    return JsonUtil.writeToString(categories);
                })
                .addGetHandler("/image", params -> {
                    String id = firstParam(params, "id");
                    if (id == null) {
                        return "Please provide an id parameter";
                    }
                    Image image = imageDataAccess.get(Integer.parseInt(id));
                    return JsonUtil.writeToString(image);
                })
                .addGetHandler("/image/search", params -> {
                    long javaStartedAtNanos = System.nanoTime();
                    String text = firstParam(params, "text");

                    SearchType searchType = parseSearchType(params);
                    if (searchType != SearchType.Text && !searchCapabilities.vectorSearchEnabled()) {
                        return "Vector search is unavailable because the vector search index was not present at startup.";
                    }

                    Integer page = params.containsKey("page")
                            ? Integer.parseInt(firstParam(params, "page"))
                            : 0;
                    boolean includeLicense = Boolean.parseBoolean(firstParam(params, "includeLicense"));

                    SearchRequest request = SearchRequest.of(
                            text,
                            searchType,
                            page,
                            new SearchFilters(
                                    booleanParam(params, "hasPerson"),
                                    listParam(params, "animal"),
                                    listParam(params, "appliance"),
                                    listParam(params, "electronic"),
                                    listParam(params, "food"),
                                    listParam(params, "furniture"),
                                    listParam(params, "indoor"),
                                    listParam(params, "kitchen"),
                                    listParam(params, "outdoor"),
                                    listParam(params, "sports"),
                                    listParam(params, "vehicle")
                            ),
                            includeLicense
                    );
                    ImageSearchResult result = imageDataAccess.search(request);
                    return writeSearchResult(result, javaStartedAtNanos);
                })
                .addDeleteHandler("/image/delete", params -> {
                    String id = firstParam(params, "id");
                    if (id == null) {
                        throw new IllegalArgumentException("Please provide an id parameter");
                    }
                    int imageId = Integer.parseInt(id);
                    boolean deleted = imageDataAccess.delete(imageId);
                    return JsonUtil.writeToString(Map.of(
                            "id", imageId,
                            "deleted", deleted
                    ));
                });

        if (lmStudioCaptioningEnabled) {
            serverBuilder.addJsonPostHandler("/image/caption", requestBody -> {
                LMStudioVisionModel.ImageInput imageInput = JsonUtil.readValue(requestBody, LMStudioVisionModel.ImageInput.class);
                List<Category> categories = categoryDataAccess.list();
                return JsonUtil.writeToString(filterVisionResultCategories(
                        LMStudioVisionModel.caption(imageInput, allowedLabelsByType(categories)),
                        categories
                ));
            });
            serverBuilder.addJsonPostHandler("/image/add", requestBody -> {
                AddImageRequest request = JsonUtil.readValue(requestBody, AddImageRequest.class);
                Image image = toImage(request, imageDataAccess.nextImageId(), categoryDataAccess.list());
                imageDataAccess.insert(image);
                return JsonUtil.writeToString(image);
            });
        }

        SimpleServer server = serverBuilder.build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }

    private static SearchType parseSearchType(Map<String, List<String>> params) {
        List<String> rawValues = params.get("searchType");
        if (rawValues == null || rawValues.isEmpty()) {
            return SearchType.Text;
        }

        boolean sawText = false;
        boolean sawVector = false;
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            for (String token : rawValue.split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                String normalised = token.trim();
                if ("Both".equalsIgnoreCase(normalised)) {
                    return SearchType.Combined;
                }
                SearchType parsed = SearchType.valueOf(normalised);
                sawText = sawText || parsed == SearchType.Text;
                sawVector = sawVector || parsed == SearchType.Vector || parsed == SearchType.Combined;
            }
        }

        if (sawText && sawVector) {
            return SearchType.Combined;
        }
        if (sawVector) {
            return SearchType.Vector;
        }
        return SearchType.Text;
    }

    private static String firstParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static Boolean booleanParam(Map<String, List<String>> params, String key) {
        String value = firstParam(params, key);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private static List<String> listParam(Map<String, List<String>> params, String key) {
        String value = firstParam(params, key);
        return value == null || value.isBlank() ? null : List.of(value.split(","));
    }

    private static String writeSearchResult(ImageSearchResult result, long javaStartedAtNanos) {
        if (result == null || result.stats() == null) {
            return JsonUtil.writeToString(result);
        }

        QueryStats stats = result.stats().withTotalJavaTimeMs(0.0);
        String json = JsonUtil.writeToString(new ImageSearchResult(result.docs(), result.meta(), stats));
        double totalJavaTimeMs = (System.nanoTime() - javaStartedAtNanos) / 1_000_000.0;

        Matcher matcher = totalJavaTimePattern.matcher(json);
        if (!matcher.find()) {
            return json;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + Double.toString(totalJavaTimeMs)));
    }

    private static LMStudioVisionModel.VisionResult filterVisionResultCategories(
            LMStudioVisionModel.VisionResult result,
            List<Category> categories
    ) {
        Map<String, List<String>> filteredCategoryLabels = filterCategoryLabels(result.categoryLabels(), categories);
        List<LMStudioVisionModel.TypedLabel> typedLabels = typedLabelsFromCategoryLabels(filteredCategoryLabels);
        List<String> labels = typedLabels.stream()
                .map(LMStudioVisionModel.TypedLabel::label)
                .distinct()
                .toList();
        return new LMStudioVisionModel.VisionResult(
                result.caption(),
                result.captionEmbedding(),
                result.captionEmbeddingModel(),
                result.visionModel(),
                result.hasPerson(),
                labels,
                typedLabels,
                filteredCategoryLabels
        );
    }

    private static Map<String, List<String>> allowedLabelsByType(List<Category> categories) {
        Set<String> indexedFields = SearchCategory.filterable().stream()
                .map(SearchCategory::fieldName)
                .collect(Collectors.toSet());
        Map<String, List<String>> labelsByType = new LinkedHashMap<>();
        for (Category category : categories) {
            if (!indexedFields.contains(category.superCategory())) {
                continue;
            }
            labelsByType.merge(
                    category.superCategory(),
                    List.of(category.name()),
                    (existing, added) -> java.util.stream.Stream.concat(existing.stream(), added.stream())
                            .distinct()
                            .sorted()
                            .toList()
            );
        }
        return labelsByType;
    }

    private static List<LMStudioVisionModel.TypedLabel> typedLabelsFromCategoryLabels(
            Map<String, List<String>> categoryLabels
    ) {
        if (categoryLabels == null || categoryLabels.isEmpty()) {
            return List.of();
        }
        return categoryLabels.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(value -> new LMStudioVisionModel.TypedLabel(entry.getKey(), value)))
                .toList();
    }

    private static Map<String, List<String>> filterCategoryLabels(
            Map<String, List<String>> categoryLabels,
            List<Category> categories
    ) {
        if (categoryLabels == null || categoryLabels.isEmpty()) {
            return Map.of();
        }

        Set<String> indexedFields = SearchCategory.filterable().stream()
                .map(SearchCategory::fieldName)
                .collect(Collectors.toSet());
        Map<String, Map<String, String>> allowedValuesByField = new HashMap<>();
        for (Category category : categories) {
            if (indexedFields.contains(category.superCategory())) {
                allowedValuesByField
                        .computeIfAbsent(category.superCategory(), _ -> new HashMap<>())
                        .put(category.name().toLowerCase(), category.name());
            }
        }

        Map<String, List<String>> filtered = new HashMap<>();
        categoryLabels.forEach((field, values) -> {
            Map<String, String> allowedValues = allowedValuesByField.get(field);
            if (allowedValues == null || values == null) {
                return;
            }
            List<String> filteredValues = values.stream()
                    .map(value -> allowedValues.get(value.toLowerCase()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!filteredValues.isEmpty()) {
                filtered.put(field, filteredValues);
            }
        });
        return filtered;
    }

    private static Image toImage(AddImageRequest request, int id, List<Category> categories) {
        LMStudioVisionModel.VisionResult result = filterVisionResultCategories(request.captionResult(), categories);
        Map<String, List<String>> categoryLabels = result.categoryLabels();
        return new Image(
                id,
                result.caption(),
                result.captionEmbedding(),
                result.captionEmbeddingModel(),
                request.imageUrl(),
                Math.max(0, request.height()),
                Math.max(0, request.width()),
                new Date(),
                "User Upload",
                null,
                result.hasPerson(),
                List.of(),
                categoryLabels.get("animal"),
                categoryLabels.get("appliance"),
                categoryLabels.get("electronic"),
                categoryLabels.get("food"),
                categoryLabels.get("furniture"),
                categoryLabels.get("indoor"),
                categoryLabels.get("kitchen"),
                categoryLabels.get("outdoor"),
                categoryLabels.get("sports"),
                categoryLabels.get("vehicle")
        );
    }

    private record AddImageRequest(
            String imageUrl,
            int height,
            int width,
            LMStudioVisionModel.VisionResult captionResult
    ) {
    }
}

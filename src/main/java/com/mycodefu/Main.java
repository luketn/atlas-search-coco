package com.mycodefu;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.QueryStats;
import com.mycodefu.model.SearchCapabilities;
import com.mycodefu.model.SearchType;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.service.SimpleServer;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mycodefu.datapreparation.PrepareDataEntryPoint.downloadAndInitialiseDataset;

public class Main {
    private static final Pattern totalJavaTimePattern = Pattern.compile("(\"totalJavaTimeMs\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)");

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            switch (args[0]) {
                case "--loadData" -> downloadAndInitialiseDataset(false);
                case "--lmStudioVectorEmbeddings" -> downloadAndInitialiseDataset(true);
                default -> System.out.println("Unsupported argument. Supported arguments: --loadData, --lmStudioVectorEmbeddings");
            }
        }

        boolean vectorSearchEnabledAtStartup;
        try (ImageDataAccess imageDataAccess = ImageDataAccess.getInstance()) {
            vectorSearchEnabledAtStartup = imageDataAccess.hasVectorSearchIndex();
        }

        SearchCapabilities searchCapabilities = new SearchCapabilities(vectorSearchEnabledAtStartup);

        SimpleServer server = SimpleServer.create(8222)
                .addGetHandler("/", params -> {
                    try {
                        return new String(Main.class.getResourceAsStream("/static/index.html").readAllBytes());
                    } catch (IOException e) {
                        return "Error loading index.html: " + e.getMessage();
                    }
                })
                .addGetHandler("/capabilities", params -> JsonUtil.writeToString(searchCapabilities))
                .addGetHandler("/categories", params -> {
                    List<Category> categories = CategoryDataAccess.getInstance().list();
                    return JsonUtil.writeToString(categories);
                })
                .addGetHandler("/image", params -> {
                    String id = firstParam(params, "id");
                    if (id == null) {
                        return "Please provide an id parameter";
                    }
                    Image image = ImageDataAccess.getInstance().get(Integer.parseInt(id));
                    return JsonUtil.writeToString(image);
                })
                .addGetHandler("/image/search", params -> {
                    long javaStartedAtNanos = System.nanoTime();
                    String text = firstParam(params, "text");

                    EnumSet<SearchType> searchTypes = parseSearchTypes(params);
                    if (searchTypes.contains(SearchType.Vector) && !searchCapabilities.vectorSearchEnabled()) {
                        return "Vector search is unavailable because the vector search index was not present at startup.";
                    }

                    Integer page = params.containsKey("page")
                            ? Integer.parseInt(firstParam(params, "page"))
                            : 0;
                    Double vectorCutoff = doubleParam(params, "vectorCutoff");

                    try (ImageDataAccess imageDataAccess = ImageDataAccess.getInstance()) {
                        ImageSearchResult result = imageDataAccess.search(
                                text,
                                searchTypes,
                                page,
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
                                listParam(params, "vehicle"),
                                vectorCutoff
                        );
                        return writeSearchResult(result, javaStartedAtNanos);
                    }
                })
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }

    private static EnumSet<SearchType> parseSearchTypes(Map<String, List<String>> params) {
        List<String> rawValues = params.get("searchType");
        if (rawValues == null || rawValues.isEmpty()) {
            return EnumSet.of(SearchType.Text);
        }

        EnumSet<SearchType> searchTypes = EnumSet.noneOf(SearchType.class);
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            for (String token : rawValue.split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                searchTypes.add(SearchType.valueOf(token.trim()));
            }
        }

        return searchTypes.isEmpty() ? EnumSet.of(SearchType.Text) : searchTypes;
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

    private static Double doubleParam(Map<String, List<String>> params, String key) {
        String value = firstParam(params, key);
        return value == null || value.isBlank() ? null : Double.parseDouble(value);
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
}

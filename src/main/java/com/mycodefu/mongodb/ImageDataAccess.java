package com.mycodefu.mongodb;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.search.SearchCollector;
import com.mongodb.client.model.search.SearchCount;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchOptions;
import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.QueryStats;
import com.mycodefu.model.SearchType;
import com.mycodefu.mongodb.atlas.MongoConnection;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.search.SearchFacet.stringFacet;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class ImageDataAccess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ImageDataAccess.class);

    private static final int PAGE_SIZE = 5;
    private static final int HYBRID_RANK_OFFSET = 60;
    private static final List<String> FACET_FIELDS = List.of(
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
    private static final List<String> IMAGE_FIELDS = List.of(
            "_id",
            "caption",
            "captionEmbedding",
            "captionEmbeddingModel",
            "url",
            "height",
            "width",
            "dateCaptured",
            "licenseName",
            "licenseUrl",
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

    public static final String collection_name = "image";

    private final MongoClient mongoClient;
    private final MongoCollection<Image> imageCollection;
    private final MongoCollection<Document> imageDocumentCollection;

    public ImageDataAccess(MongoClient mongoClient, String databaseName, String collectionName) {
        this.mongoClient = mongoClient;
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.imageCollection = database.getCollection(collectionName, Image.class);
        this.imageDocumentCollection = database.getCollection(collectionName);
    }

    public static ImageDataAccess getInstance() {
        return new ImageDataAccess(
                MongoConnection.connection(),
                database_name,
                collection_name
        );
    }

    public void insert(Image image) {
        imageCollection.insertOne(image);
    }

    public void insertBulk(List<Image> images) {
        imageCollection.insertMany(images);
    }

    public Image get(int id) {
        return imageCollection.find(eq("_id", id)).first();
    }

    public void removeAll() {
        imageCollection.deleteMany(new Document());
    }

    public boolean hasVectorSearchIndex() {
        for (Document indexDocument : imageDocumentCollection.listSearchIndexes()) {
            if (MongoConnection.vector_index_name.equals(indexDocument.getString("name"))
                    && "READY".equals(indexDocument.getString("status"))) {
                return true;
            }
        }
        return false;
    }

    public ImageSearchResult search(
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
        return search(
                text,
                EnumSet.of(SearchType.Text),
                page,
                hasPerson,
                animal,
                appliance,
                electronic,
                food,
                furniture,
                indoor,
                kitchen,
                outdoor,
                sports,
                vehicle
        );
    }

    public ImageSearchResult search(
            String text,
            Set<SearchType> searchTypes,
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
        EnumSet<SearchType> requestedSearchTypes = searchTypes == null || searchTypes.isEmpty()
                ? EnumSet.of(SearchType.Text)
                : EnumSet.copyOf(searchTypes);
        SearchFilters filters = new SearchFilters(
                hasPerson,
                normaliseValues(animal),
                normaliseValues(appliance),
                normaliseValues(electronic),
                normaliseValues(food),
                normaliseValues(furniture),
                normaliseValues(indoor),
                normaliseValues(kitchen),
                normaliseValues(outdoor),
                normaliseValues(sports),
                normaliseValues(vehicle)
        );

        if (requestedSearchTypes.equals(EnumSet.of(SearchType.Text))) {
            return textSearch(text, page, filters);
        }

        if (!hasVectorSearchIndex()) {
            throw new IllegalStateException("Vector search index is not available.");
        }

        return vectorOrHybridSearch(text, requestedSearchTypes, page, filters);
    }

    private ImageSearchResult textSearch(String text, Integer page, SearchFilters filters) {
        List<SearchOperator> clauses = new ArrayList<>();
        if (text != null) {
            clauses.add(SearchOperator.text(fieldPath("caption"), text));
        }
        clauses.addAll(filters.toSearchClauses());

        int skip = page == null ? 0 : page * PAGE_SIZE;
        List<Bson> aggregateStages = List.of(
                Aggregates.search(
                        SearchCollector.facet(
                                SearchOperator.compound().filter(clauses),
                                List.of(
                                        stringFacet("animal", fieldPath("animal")).numBuckets(10),
                                        stringFacet("appliance", fieldPath("appliance")).numBuckets(10),
                                        stringFacet("electronic", fieldPath("electronic")).numBuckets(10),
                                        stringFacet("food", fieldPath("food")).numBuckets(10),
                                        stringFacet("furniture", fieldPath("furniture")).numBuckets(10),
                                        stringFacet("indoor", fieldPath("indoor")).numBuckets(10),
                                        stringFacet("kitchen", fieldPath("kitchen")).numBuckets(10),
                                        stringFacet("outdoor", fieldPath("outdoor")).numBuckets(10),
                                        stringFacet("sports", fieldPath("sports")).numBuckets(10),
                                        stringFacet("vehicle", fieldPath("vehicle")).numBuckets(10)
                                )
                        ),
                        SearchOptions.searchOptions().count(SearchCount.total())
                ),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE),
                Aggregates.facet(
                        new Facet("docs", List.of()),
                        new Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1)
                        ))
                )
        );

        return aggregateSearchResult(imageCollection.aggregate(aggregateStages, ImageSearchResult.class), aggregateStages);
    }

    private ImageSearchResult vectorOrHybridSearch(String text, Set<SearchType> searchTypes, Integer page, SearchFilters filters) {
        int skip = page == null ? 0 : page * PAGE_SIZE;
        List<Double> queryVector = LMStudioEmbedding.embed(text).embedding();
        long vectorLimit = Math.max(1L, imageDocumentCollection.countDocuments(filters.toVectorFilterDocument()));
        int vectorResultLimit = (int) Math.min(Integer.MAX_VALUE, vectorLimit);
        int numCandidates = Math.max(vectorResultLimit, 100);

        List<Bson> aggregateStages;
        if (searchTypes.equals(EnumSet.of(SearchType.Vector))) {
            aggregateStages = new ArrayList<>(buildVectorPipeline(queryVector, filters, vectorResultLimit, numCandidates));
        } else {
            aggregateStages = new ArrayList<>(buildTextRankingPipeline(text, filters));
            aggregateStages.add(new Document("$unionWith", new Document("coll", collection_name)
                    .append("pipeline", buildVectorPipeline(queryVector, filters, vectorResultLimit, numCandidates))));
            aggregateStages.add(new Document("$group", new Document("_id", "$_id")
                    .append("doc", new Document("$first", "$$ROOT"))
                    .append("hybridScore", new Document("$sum", "$hybridScore"))));
            aggregateStages.add(new Document("$replaceRoot", new Document("newRoot",
                    new Document("$mergeObjects", List.of("$doc", new Document("hybridScore", "$hybridScore"))))));
            aggregateStages.add(new Document("$sort", new Document("hybridScore", -1).append("_id", 1)));
        }
        aggregateStages.add(new Document("$project", buildImageProjectionFields()));
        return aggregateRankedSearchResult(aggregateStages, skip);
    }

    private ImageSearchResult aggregateSearchResult(AggregateIterable<ImageSearchResult> aggregateCursor, List<? extends Bson> aggregateStages) {
        if (log.isTraceEnabled()) {
            for (Bson aggregateStage : aggregateStages) {
                System.out.println(aggregateStage.toBsonDocument().toJson(JsonWriterSettings.builder().indent(true).build()));
            }
        }

        String traceId = null;
        if (MongoConnectionTracing.isTracingEnabled()) {
            traceId = MongoConnectionTracing.newTraceId();
            aggregateCursor.comment(MongoConnectionTracing.toTraceComment(traceId));
        }

        ImageSearchResult aggregateResult;
        QueryStats stats = null;
        try (MongoCursor<ImageSearchResult> cursor = aggregateCursor.cursor()) {
            if (traceId != null) {
                MongoConnectionTracing.registerCursorTrace(cursor, traceId);
            }
            aggregateResult = cursor.tryNext();
            if (traceId != null) {
                stats = MongoConnectionTracing.getQueryStats(cursor);
            }
        }

        ImageSearchResult imageSearchResult = aggregateResult == null ? null : aggregateResult.withStats(stats);

        if (log.isTraceEnabled()) {
            log.trace(JsonUtil.writeToString(imageSearchResult));
        }
        return imageSearchResult;
    }

    private ImageSearchResult aggregateRankedSearchResult(List<? extends Bson> aggregateStages, int skip) {
        if (log.isTraceEnabled()) {
            for (Bson aggregateStage : aggregateStages) {
                System.out.println(aggregateStage.toBsonDocument().toJson(JsonWriterSettings.builder().indent(true).build()));
            }
        }

        AggregateIterable<Image> aggregateCursor = imageDocumentCollection.aggregate(aggregateStages, Image.class);
        String traceId = null;
        if (MongoConnectionTracing.isTracingEnabled()) {
            traceId = MongoConnectionTracing.newTraceId();
            aggregateCursor.comment(MongoConnectionTracing.toTraceComment(traceId));
        }

        ArrayList<Image> rankedImages = new ArrayList<>();
        QueryStats stats = null;
        try (MongoCursor<Image> cursor = aggregateCursor.cursor()) {
            if (traceId != null) {
                MongoConnectionTracing.registerCursorTrace(cursor, traceId);
            }
            while (cursor.hasNext()) {
                rankedImages.add(cursor.next());
            }
            if (traceId != null) {
                stats = MongoConnectionTracing.getQueryStats(cursor);
            }
        }

        ImageSearchResult imageSearchResult = new ImageSearchResult(
                rankedImages.stream().skip(skip).limit(PAGE_SIZE).toList(),
                List.of(buildMeta(rankedImages)),
                stats
        );

        if (log.isTraceEnabled()) {
            log.trace(JsonUtil.writeToString(imageSearchResult));
        }
        return imageSearchResult;
    }

    private List<Bson> buildTextRankingPipeline(String text, SearchFilters filters) {
        ArrayList<Bson> pipeline = new ArrayList<>();
        pipeline.add(new Document("$search", new Document("index", MongoConnection.index_name)
                .append("compound", buildTextCompound(text, filters))));
        pipeline.add(buildScoreProjection("searchScore"));
        pipeline.add(buildRankStage());
        pipeline.add(buildHybridScoreStage());
        pipeline.add(buildImageProjection());
        return pipeline;
    }

    private List<Bson> buildVectorPipeline(List<Double> queryVector, SearchFilters filters, int limit, int numCandidates) {
        ArrayList<Bson> pipeline = new ArrayList<>();
        Document vectorStage = new Document("index", MongoConnection.vector_index_name)
                .append("path", "captionEmbedding")
                .append("queryVector", queryVector)
                .append("limit", limit)
                .append("numCandidates", numCandidates);
        Document filterDocument = filters.toVectorFilterDocument();
        if (!filterDocument.isEmpty()) {
            vectorStage.append("filter", filterDocument);
        }
        pipeline.add(new Document("$vectorSearch", vectorStage));
        pipeline.add(buildScoreProjection("vectorSearchScore"));
        pipeline.add(buildRankStage());
        pipeline.add(buildHybridScoreStage());
        pipeline.add(buildImageProjection());
        return pipeline;
    }

    private Document buildTextCompound(String text, SearchFilters filters) {
        ArrayList<Document> mustClauses = new ArrayList<>();
        mustClauses.add(new Document("text", new Document("path", "caption").append("query", text)));

        ArrayList<Document> filterClauses = new ArrayList<>(filters.toSearchClauseDocuments());

        Document compound = new Document("must", mustClauses);
        if (!filterClauses.isEmpty()) {
            compound.append("filter", filterClauses);
        }
        return compound;
    }

    private Document buildScoreProjection(String scoreMetaField) {
        Document projection = buildImageProjectionFields();
        projection.append("_rawScore", new Document("$meta", scoreMetaField));
        return new Document("$project", projection);
    }

    private Document buildRankStage() {
        return new Document("$setWindowFields", new Document("sortBy", new Document("_rawScore", -1))
                .append("output", new Document("_rank", new Document("$documentNumber", new Document()))));
    }

    private Document buildHybridScoreStage() {
        return new Document("$addFields", new Document("hybridScore",
                new Document("$divide", List.of(1.0, new Document("$add", List.of(HYBRID_RANK_OFFSET, "$_rank"))))));
    }

    private Document buildImageProjection() {
        Document projection = buildImageProjectionFields();
        projection.append("hybridScore", 1);
        return new Document("$project", projection);
    }

    private Document buildImageProjectionFields() {
        Document projection = new Document();
        for (String field : IMAGE_FIELDS) {
            projection.append(field, 1);
        }
        return projection;
    }

    private ImageSearchResult.ImageMeta buildMeta(List<Image> rankedImages) {
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

    private ImageSearchResult.ImageMetaFacet facetBuckets(List<Image> rankedImages, String facetName) {
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

    private List<String> facetValues(Image image, String facetName) {
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

    private List<String> normaliseValues(List<String> values) {
        return values == null ? null : values.stream().filter(Objects::nonNull).toList();
    }

    private static SearchOperator equals(String fieldName, Object value) {
        return SearchOperator.of(new Document("equals", new Document("path", fieldName).append("value", value)));
    }

    public void close() {
        // MongoConnection manages the shared client lifecycle for the app.
    }

    private record SearchFilters(
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

        private List<SearchOperator> toSearchClauses() {
            ArrayList<SearchOperator> clauses = new ArrayList<>();
            if (hasPerson != null) {
                clauses.add(ImageDataAccess.equals("hasPerson", hasPerson));
            }
            for (Map.Entry<String, List<String>> entry : categories().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    clauses.add(ImageDataAccess.equals(entry.getKey(), value));
                }
            }
            return clauses;
        }

        private List<Document> toSearchClauseDocuments() {
            ArrayList<Document> clauses = new ArrayList<>();
            if (hasPerson != null) {
                clauses.add(new Document("equals", new Document("path", "hasPerson").append("value", hasPerson)));
            }
            for (Map.Entry<String, List<String>> entry : categories().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    clauses.add(new Document("equals", new Document("path", entry.getKey()).append("value", value)));
                }
            }
            return clauses;
        }

        private Document toVectorFilterDocument() {
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
    }
}

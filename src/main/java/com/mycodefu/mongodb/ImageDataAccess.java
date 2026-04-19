package com.mycodefu.mongodb;

import com.mongodb.MongoCommandException;
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
import com.mycodefu.mongodb.atlas.MongoConnection;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.search.SearchFacet.stringFacet;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class ImageDataAccess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ImageDataAccess.class);

    private static final int PAGE_SIZE = 10;
    public static final double DEFAULT_VECTOR_SCORE_CUTOFF = 0.88;
    private static final int HYBRID_RANK_OFFSET = 60;
    private static final int MIN_VECTOR_CANDIDATES = 100;
    private static final int MAX_VECTOR_CANDIDATES = 10_000;
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

    public static final String collection_name = "image";

    private final MongoCollection<Image> imageCollection;
    private final MongoCollection<Document> imageDocumentCollection;
    private final SearchStrategy browseSearchStrategy = new BrowseSearchStrategy();
    private final SearchStrategy textSearchStrategy = new TextSearchStrategy();
    private final SearchStrategy vectorSearchStrategy = new VectorSearchStrategy();
    private final SearchStrategy hybridSearchStrategy = new HybridSearchStrategy();

    public ImageDataAccess(MongoClient mongoClient, String databaseName, String collectionName) {
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
        try {
            for (Document indexDocument : imageDocumentCollection.listSearchIndexes()) {
                if (MongoConnection.vector_index_name.equals(indexDocument.getString("name"))
                        && "READY".equals(indexDocument.getString("status"))) {
                    return true;
                }
            }
            return false;
        } catch (MongoCommandException e) {
            log.warn("Search index management service unavailable while detecting vector search index. " +
                    "Starting with vector search disabled.", e);
            return false;
        }
    }

    public ImageSearchResult search(SearchRequest request) {
        if (!request.hasMeaningfulText()) {
            return browseSearchStrategy.search(this, request);
        }

        if (request.textOnly()) {
            return textSearchStrategy.search(this, request);
        }

        if (!hasVectorSearchIndex()) {
            throw new IllegalStateException("Vector search index is not available.");
        }

        return request.vectorOnly()
                ? vectorSearchStrategy.search(this, request)
                : hybridSearchStrategy.search(this, request);
    }

    ImageSearchResult executeFacetSearch(SearchOperator operator, SearchRequest request) {
        int skip = request.page() * PAGE_SIZE;
        List<Bson> aggregateStages = List.of(
                Aggregates.search(
                        SearchCollector.facet(
                                operator,
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
                        buildSearchOptions(request)
                                .count(SearchCount.total())
                ),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE),
                Aggregates.facet(
                        new Facet("docs", List.of(new Document("$project", buildImageProjectionFields(request.includeLicense(), false)))),
                        new Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1)
                        ))
                )
        );

        return aggregateSearchResult(imageCollection.aggregate(aggregateStages, ImageSearchResult.class), aggregateStages);
    }

    ImageSearchResult executeVectorSearch(SearchRequest request) {
        VectorQuery vectorQuery = createVectorQuery(request);
        List<Bson> aggregateStages = new ArrayList<>(buildVectorPipeline(vectorQuery, request));
        aggregateStages.add(new Document("$project", buildImageProjectionFields(request.includeLicense(), false)));
        return aggregateRankedSearchResult(aggregateStages, request.page() * PAGE_SIZE);
    }

    ImageSearchResult executeHybridSearch(SearchRequest request) {
        int skip = request.page() * PAGE_SIZE;
        VectorQuery vectorQuery = createVectorQuery(request);
        int textResultLimit = (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, vectorQuery.filteredDocumentCount()));

        List<Image> textResults = loadRankedImages(buildTextRankingPipeline(request, textResultLimit));
        List<Image> vectorResults = loadRankedImages(buildVectorPipeline(vectorQuery, request));
        List<Image> rankedImages = fuseRankedImages(textResults, vectorResults);
        return buildRankedSearchResult(rankedImages, skip, null);
    }

    SearchOperator buildTextSearchOperator(SearchRequest request) {
        return buildCompoundOperator(
                List.of(SearchOperator.text(fieldPath("caption"), request.text())),
                request.filters().toSearchClauses()
        );
    }

    SearchOperator buildBrowseSearchOperator(SearchRequest request) {
        return buildCompoundOperator(
                List.of(SearchOperator.exists(fieldPath("caption"))),
                request.filters().toSearchClauses()
        );
    }

    private SearchOperator buildCompoundOperator(List<SearchOperator> mustClauses, List<SearchOperator> filterClauses) {
        SearchOperator compound = SearchOperator.compound().must(mustClauses);
        if (!filterClauses.isEmpty()) {
            compound = SearchOperator.compound().must(mustClauses).filter(filterClauses);
        }
        return compound;
    }

    private SearchOptions buildSearchOptions(SearchRequest request) {
        return SearchOptions.searchOptions()
                .returnStoredSource(request.returnStoredSource());
    }

    private VectorQuery createVectorQuery(SearchRequest request) {
        List<Double> queryVector = LMStudioEmbedding.embed(request.text()).embedding();
        long filteredDocumentCount = Math.max(1L, imageDocumentCollection.countDocuments(request.filters().toVectorFilterDocument()));
        int desiredVectorResultLimit = (int) Math.min(Integer.MAX_VALUE, filteredDocumentCount);
        int numCandidates = Math.min(MAX_VECTOR_CANDIDATES, Math.max(desiredVectorResultLimit, MIN_VECTOR_CANDIDATES));
        int vectorResultLimit = Math.min(desiredVectorResultLimit, numCandidates);
        return new VectorQuery(queryVector, filteredDocumentCount, vectorResultLimit, numCandidates);
    }

    private List<Bson> buildTextRankingPipeline(SearchRequest request, int limit) {
        return List.of(
                Aggregates.search(
                        buildTextSearchOperator(request),
                        buildSearchOptions(request).index(MongoConnection.index_name)
                ),
                Aggregates.limit(limit),
                new Document("$project", buildImageProjectionFields(request.includeLicense(), false))
        );
    }

    private List<Bson> buildVectorPipeline(VectorQuery vectorQuery, SearchRequest request) {
        ArrayList<Bson> pipeline = new ArrayList<>();
        pipeline.add(buildVectorPipelineStage(vectorQuery, request));
        pipeline.add(buildScoreProjection("vectorSearchScore", request.includeLicense()));
        pipeline.add(buildScoreCutoffMatchStage(request.vectorScoreCutoff()));
        pipeline.add(buildRankStage());
        pipeline.add(buildHybridScoreStage());
        pipeline.add(new Document("$project", buildImageProjectionFields(request.includeLicense(), true)));
        return pipeline;
    }

    private Document buildVectorPipelineStage(VectorQuery vectorQuery, SearchRequest request) {
        Document vectorStage = new Document("index", MongoConnection.vector_index_name)
                .append("path", "captionEmbedding")
                .append("queryVector", vectorQuery.queryVector())
                .append("limit", vectorQuery.vectorResultLimit())
                .append("numCandidates", vectorQuery.numCandidates())
                .append("returnStoredSource", request.returnStoredSource());
        Document filterDocument = request.filters().toVectorFilterDocument();
        if (!filterDocument.isEmpty()) {
            vectorStage.append("filter", filterDocument);
        }
        return new Document("$vectorSearch", vectorStage);
    }

    private Document buildScoreProjection(String scoreMetaField, boolean includeLicense) {
        Document projection = buildImageProjectionFields(includeLicense, false);
        projection.append("_rawScore", new Document("$meta", scoreMetaField));
        return new Document("$project", projection);
    }

    private Document buildScoreCutoffMatchStage(double vectorScoreCutoff) {
        return new Document("$match", new Document("_rawScore", new Document("$gte", vectorScoreCutoff)));
    }

    private Document buildRankStage() {
        return new Document("$setWindowFields", new Document("sortBy", new Document("_rawScore", -1))
                .append("output", new Document("_rank", new Document("$documentNumber", new Document()))));
    }

    private Document buildHybridScoreStage() {
        return new Document("$addFields", new Document("hybridScore",
                new Document("$divide", List.of(1.0, new Document("$add", List.of(HYBRID_RANK_OFFSET, "$_rank"))))));
    }

    Document buildImageProjectionFields(boolean includeLicense, boolean includeHybridScore) {
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

    private ImageSearchResult aggregateSearchResult(AggregateIterable<ImageSearchResult> aggregateCursor, List<? extends Bson> aggregateStages) {
        tracePipeline(aggregateStages);

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
        if (log.isDebugEnabled()) {
            log.debug(JsonUtil.writeToString(imageSearchResult));
        }
        return imageSearchResult;
    }

    private ImageSearchResult aggregateRankedSearchResult(List<? extends Bson> aggregateStages, int skip) {
        return buildRankedSearchResult(loadRankedImages(aggregateStages), skip, null);
    }

    private List<Image> loadRankedImages(List<? extends Bson> aggregateStages) {
        tracePipeline(aggregateStages);

        AggregateIterable<Image> aggregateCursor = imageDocumentCollection.aggregate(aggregateStages, Image.class);
        String traceId = null;
        if (MongoConnectionTracing.isTracingEnabled()) {
            traceId = MongoConnectionTracing.newTraceId();
            aggregateCursor.comment(MongoConnectionTracing.toTraceComment(traceId));
        }

        ArrayList<Image> rankedImages = new ArrayList<>();
        try (MongoCursor<Image> cursor = aggregateCursor.cursor()) {
            if (traceId != null) {
                MongoConnectionTracing.registerCursorTrace(cursor, traceId);
            }
            while (cursor.hasNext()) {
                rankedImages.add(cursor.next());
            }
        }

        if (log.isTraceEnabled()) {
            log.trace(JsonUtil.writeToString(rankedImages));
        }
        return rankedImages;
    }

    private void tracePipeline(List<? extends Bson> aggregateStages) {
        if (log.isTraceEnabled()) {
            for (Bson aggregateStage : aggregateStages) {
                System.out.println(aggregateStage.toBsonDocument().toJson(JsonWriterSettings.builder().indent(true).build()));
            }
        }
    }

    private ImageSearchResult buildRankedSearchResult(List<Image> rankedImages, int skip, QueryStats stats) {
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

    private List<Image> fuseRankedImages(List<Image> textResults, List<Image> vectorResults) {
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

    private void applyReciprocalRankScores(List<Image> rankedImages, Map<Integer, Image> imageById, Map<Integer, Double> scoreById) {
        for (int i = 0; i < rankedImages.size(); i++) {
            Image image = rankedImages.get(i);
            imageById.putIfAbsent(image._id(), image);
            scoreById.merge(image._id(), 1.0 / (HYBRID_RANK_OFFSET + i + 1), Double::sum);
        }
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

    static SearchOperator equalsClause(String fieldName, Object value) {
        return SearchOperator.of(new Document("equals", new Document("path", fieldName).append("value", value)));
    }

    @Override
    public void close() {
        // MongoConnection manages the shared client lifecycle for the app.
    }

    private record VectorQuery(
            List<Double> queryVector,
            long filteredDocumentCount,
            int vectorResultLimit,
            int numCandidates
    ) {
    }
}

package com.mycodefu.mongodb;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.search.SearchOperator;
import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.QueryStats;
import com.mycodefu.mongodb.atlas.MongoConnection;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import com.mycodefu.mongodb.search.HybridRanker;
import com.mycodefu.mongodb.search.SearchPipelines;
import com.mycodefu.mongodb.search.SearchPlanner;
import com.mycodefu.mongodb.search.SearchRequest;
import com.mycodefu.mongodb.search.VectorQueryPlan;
import com.mycodefu.mongodb.search.VectorQueryPlanner;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class ImageDataAccess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ImageDataAccess.class);

    public static final double DEFAULT_VECTOR_SCORE_CUTOFF = SearchRequest.DEFAULT_VECTOR_SCORE_CUTOFF;
    public static final String collection_name = "image";

    private final MongoCollection<Image> imageCollection;
    private final MongoCollection<Document> imageDocumentCollection;

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
        return switch (SearchPlanner.plan(request, hasVectorSearchIndex())) {
            case BROWSE -> executeFacetSearch(SearchPipelines.browseSearchOperator(request), request);
            case TEXT -> executeFacetSearch(SearchPipelines.textSearchOperator(request), request);
            case VECTOR -> executeVectorSearch(request);
            case HYBRID -> executeHybridSearch(request);
        };
    }

    private ImageSearchResult executeFacetSearch(SearchOperator operator, SearchRequest request) {
        List<Bson> aggregateStages = SearchPipelines.facetSearchPipeline(operator, request);
        return aggregateSearchResult(imageCollection.aggregate(aggregateStages, ImageSearchResult.class), aggregateStages);
    }

    private ImageSearchResult executeVectorSearch(SearchRequest request) {
        VectorQueryPlan vectorQueryPlan = createVectorQueryPlan(request);
        List<Bson> aggregateStages = new ArrayList<>(SearchPipelines.vectorPipeline(vectorQueryPlan, request));
        aggregateStages.add(new Document("$project", com.mycodefu.mongodb.search.SearchProjection.imageProjection(request.includeLicense(), false)));
        return aggregateRankedSearchResult(aggregateStages, request.page() * SearchPipelines.pageSize());
    }

    private ImageSearchResult executeHybridSearch(SearchRequest request) {
        int skip = request.page() * SearchPipelines.pageSize();
        VectorQueryPlan vectorQueryPlan = createVectorQueryPlan(request);
        int textResultLimit = (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, vectorQueryPlan.filteredDocumentCount()));

        List<Image> textResults = loadRankedImages(SearchPipelines.textRankingPipeline(request, textResultLimit));
        List<Image> vectorResults = loadRankedImages(SearchPipelines.vectorPipeline(vectorQueryPlan, request));
        List<Image> rankedImages = HybridRanker.fuse(textResults, vectorResults);
        return buildRankedSearchResult(rankedImages, skip, null);
    }

    private VectorQueryPlan createVectorQueryPlan(SearchRequest request) {
        List<Double> queryVector = LMStudioEmbedding.embed(request.text()).embedding();
        long filteredDocumentCount = imageDocumentCollection.countDocuments(request.filters().toVectorFilterDocument());
        return VectorQueryPlanner.plan(queryVector, filteredDocumentCount);
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
                rankedImages.stream().skip(skip).limit(SearchPipelines.pageSize()).toList(),
                List.of(HybridRanker.buildMeta(rankedImages)),
                stats
        );
        if (log.isTraceEnabled()) {
            log.trace(JsonUtil.writeToString(imageSearchResult));
        }
        return imageSearchResult;
    }

    @Override
    public void close() {
        // MongoConnection manages the shared client lifecycle for the app.
    }
}

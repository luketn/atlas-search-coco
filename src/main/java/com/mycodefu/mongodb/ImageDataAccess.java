package com.mycodefu.mongodb;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.QueryStats;
import com.mycodefu.mongodb.atlas.MongoConnection;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import com.mycodefu.mongodb.atlas.MongoConnectionTracingLogResult;
import com.mycodefu.mongodb.atlas.MongoConnectionTracingLogSearch;
import com.mycodefu.mongodb.search.SearchPipelines;
import com.mycodefu.mongodb.search.SearchPlanner;
import com.mycodefu.mongodb.search.SearchRequest;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

import static com.mongodb.client.model.Filters.eq;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class ImageDataAccess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ImageDataAccess.class);

    public static final String collection_name = "image";

    private static volatile VectorSearchIndexState cachedVectorSearchIndexState;

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

    public VectorSearchIndexState refreshVectorSearchIndexState() {
        cachedVectorSearchIndexState = loadVectorSearchIndexState();
        return cachedVectorSearchIndexState;
    }

    public ImageSearchResult search(SearchRequest request) {
        VectorSearchIndexState vectorSearchIndexState = vectorSearchIndexState();
        return switch (SearchPlanner.plan(request, vectorSearchIndexState.available())) {
            case TEXT -> executeSearch(SearchPipelines.textSearchPipeline(request));
            case VECTOR -> executeSearch(SearchPipelines.vectorSearchPipeline(
                    request,
                    LMStudioEmbedding.embed(request.text()).embedding()
            ));
            case COMBINED -> executeSearch(SearchPipelines.combinedSearchPipeline(
                    request,
                    LMStudioEmbedding.embed(request.text()).embedding()
            ));
        };
    }

    private ImageSearchResult executeSearch(List<? extends Bson> aggregateStages) {
        return aggregateSearchResult(imageCollection.aggregate(aggregateStages, ImageSearchResult.class), aggregateStages);
    }

    private VectorSearchIndexState vectorSearchIndexState() {
        VectorSearchIndexState current = cachedVectorSearchIndexState;
        if (current == null) {
            current = refreshVectorSearchIndexState();
        }
        return current;
    }

    private VectorSearchIndexState loadVectorSearchIndexState() {
        try {
            for (Document indexDocument : imageDocumentCollection.listSearchIndexes()) {
                if (MongoConnection.vector_index_name.equals(indexDocument.getString("name"))
                        && "READY".equals(indexDocument.getString("status"))) {
                    return new VectorSearchIndexState(true);
                }
            }
            return VectorSearchIndexState.unavailable();
        } catch (MongoCommandException e) {
            log.warn("Search index management service unavailable while detecting vector search index. " +
                    "Starting with vector search disabled.", e);
            return VectorSearchIndexState.unavailable();
        }
    }

    private ImageSearchResult aggregateSearchResult(AggregateIterable<ImageSearchResult> aggregateCursor, List<? extends Bson> aggregateStages) {
        AggregateExecutionResult<ImageSearchResult> executionResult = executeAggregate(
                aggregateCursor,
                aggregateStages,
                MongoCursor::tryNext,
                true
        );

        ImageSearchResult aggregateResult = executionResult.value();
        ImageSearchResult imageSearchResult = normaliseSearchResult(aggregateResult, executionResult.stats());
        MongoConnectionTracingLogResult.trace(imageSearchResult);
        return imageSearchResult;
    }

    private ImageSearchResult normaliseSearchResult(ImageSearchResult aggregateResult, QueryStats stats) {
        if (aggregateResult == null) {
            return new ImageSearchResult(
                    List.of(),
                    List.of(new ImageSearchResult.ImageMeta(false, null)),
                    stats
            );
        }

        List<Image> docs = aggregateResult.docs() == null ? List.of() : aggregateResult.docs();
        boolean hasMore = docs.size() > SearchPipelines.pageSize();
        List<Image> pageDocs = hasMore ? List.copyOf(docs.subList(0, SearchPipelines.pageSize())) : docs;
        ImageSearchResult.ImageMeta aggregateMeta = aggregateResult.meta() == null || aggregateResult.meta().isEmpty()
                ? null
                : aggregateResult.meta().getFirst();
        ImageSearchResult.ImageMeta meta = new ImageSearchResult.ImageMeta(
                hasMore,
                aggregateMeta == null ? null : aggregateMeta.facet()
        );
        return new ImageSearchResult(pageDocs, List.of(meta), stats);
    }

    private <T, R> AggregateExecutionResult<R> executeAggregate(
            AggregateIterable<T> aggregateCursor,
            List<? extends Bson> aggregateStages,
            Function<MongoCursor<T>, R> reader,
            boolean collectStats
    ) {
        MongoConnectionTracingLogSearch.trace(aggregateStages);

        String traceId = null;
        if (MongoConnectionTracing.isTracingEnabled()) {
            traceId = MongoConnectionTracing.newTraceId();
            aggregateCursor.comment(MongoConnectionTracing.toTraceComment(traceId));
        }

        QueryStats stats = null;
        R value;
        try (MongoCursor<T> cursor = aggregateCursor.cursor()) {
            if (traceId != null) {
                MongoConnectionTracing.registerCursorTrace(cursor, traceId);
            }
            value = reader.apply(cursor);
            if (collectStats && traceId != null) {
                stats = MongoConnectionTracing.getQueryStats(cursor);
            }
        }
        return new AggregateExecutionResult<>(value, stats);
    }

    @Override
    public void close() {
        // MongoConnection manages the shared client lifecycle for the app.
    }

    private record AggregateExecutionResult<T>(T value, QueryStats stats) {
    }

    public record VectorSearchIndexState(boolean available) {
        private static VectorSearchIndexState unavailable() {
            return new VectorSearchIndexState(false);
        }
    }
}

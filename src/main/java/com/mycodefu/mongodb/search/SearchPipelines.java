package com.mycodefu.mongodb.search;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.search.SearchCollector;
import com.mongodb.client.model.search.SearchCount;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchOptions;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.search.SearchFacet.stringFacet;
import static com.mongodb.client.model.search.SearchPath.fieldPath;

public final class SearchPipelines {
    private static final int PAGE_SIZE = 10;
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

    private SearchPipelines() {
    }

    public static List<Bson> facetSearchPipeline(SearchOperator operator, SearchRequest request) {
        int skip = request.page() * PAGE_SIZE;
        return List.of(
                Aggregates.search(
                        SearchCollector.facet(operator, facetCollectors()),
                        searchOptions(request).count(SearchCount.total())
                ),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE),
                Aggregates.facet(
                        new Facet("docs", List.of(new Document("$project", SearchProjection.imageProjection(request.includeLicense(), false)))),
                        new Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1)
                        ))
                )
        );
    }

    public static List<Bson> textRankingPipeline(SearchRequest request, int limit) {
        return List.of(
                Aggregates.search(
                        textSearchOperator(request),
                        searchOptions(request).index(MongoConnection.index_name)
                ),
                Aggregates.limit(limit),
                new Document("$project", SearchProjection.imageProjection(request.includeLicense(), false))
        );
    }

    public static List<Bson> vectorPipeline(VectorQueryPlan vectorQueryPlan, SearchRequest request) {
        ArrayList<Bson> pipeline = new ArrayList<>();
        pipeline.add(vectorSearchStage(vectorQueryPlan, request));
        pipeline.add(scoreProjection("vectorSearchScore", request.includeLicense()));
        pipeline.add(scoreCutoffMatchStage(request.vectorScoreCutoff()));
        pipeline.add(rankStage());
        pipeline.add(hybridScoreStage());
        pipeline.add(new Document("$project", SearchProjection.imageProjection(request.includeLicense(), true)));
        return pipeline;
    }

    public static SearchOperator textSearchOperator(SearchRequest request) {
        return compoundOperator(
                List.of(SearchOperator.text(fieldPath("caption"), request.text())),
                request.filters().toSearchClauses()
        );
    }

    public static SearchOperator browseSearchOperator(SearchRequest request) {
        return compoundOperator(
                List.of(SearchOperator.exists(fieldPath("caption"))),
                request.filters().toSearchClauses()
        );
    }

    public static int pageSize() {
        return PAGE_SIZE;
    }

    private static SearchOptions searchOptions(SearchRequest request) {
        return SearchOptions.searchOptions().returnStoredSource(request.returnStoredSource());
    }

    private static SearchOperator compoundOperator(List<SearchOperator> mustClauses, List<SearchOperator> filterClauses) {
        SearchOperator compound = SearchOperator.compound().must(mustClauses);
        if (!filterClauses.isEmpty()) {
            compound = SearchOperator.compound().must(mustClauses).filter(filterClauses);
        }
        return compound;
    }

    private static Document vectorSearchStage(VectorQueryPlan vectorQueryPlan, SearchRequest request) {
        Document vectorStage = new Document("index", MongoConnection.vector_index_name)
                .append("path", "captionEmbedding")
                .append("queryVector", vectorQueryPlan.queryVector())
                .append("limit", vectorQueryPlan.vectorResultLimit())
                .append("numCandidates", vectorQueryPlan.numCandidates())
                .append("returnStoredSource", request.returnStoredSource());
        Document filterDocument = request.filters().toVectorFilterDocument();
        if (!filterDocument.isEmpty()) {
            vectorStage.append("filter", filterDocument);
        }
        return new Document("$vectorSearch", vectorStage);
    }

    private static Document scoreProjection(String scoreMetaField, boolean includeLicense) {
        Document projection = SearchProjection.imageProjection(includeLicense, false);
        projection.append("_rawScore", new Document("$meta", scoreMetaField));
        return new Document("$project", projection);
    }

    private static Document scoreCutoffMatchStage(double vectorScoreCutoff) {
        return new Document("$match", new Document("_rawScore", new Document("$gte", vectorScoreCutoff)));
    }

    private static Document rankStage() {
        return new Document("$setWindowFields", new Document("sortBy", new Document("_rawScore", -1))
                .append("output", new Document("_rank", new Document("$documentNumber", new Document()))));
    }

    private static Document hybridScoreStage() {
        return new Document("$addFields", new Document("hybridScore",
                new Document("$divide", List.of(1.0, new Document("$add", List.of(60, "$_rank"))))));
    }

    private static List<com.mongodb.client.model.search.SearchFacet> facetCollectors() {
        return FACET_FIELDS.stream()
                .map(field -> stringFacet(field, fieldPath(field)).numBuckets(10))
                .map(facet -> (com.mongodb.client.model.search.SearchFacet) facet)
                .toList();
    }
}

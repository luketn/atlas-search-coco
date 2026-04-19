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
import java.util.LinkedHashMap;
import java.util.List;

import static com.mongodb.client.model.search.SearchFacet.stringFacet;
import static com.mongodb.client.model.search.SearchPath.fieldPath;

public final class SearchPipelines {
    private static final int PAGE_SIZE = 10;

    private SearchPipelines() {
    }

    public static List<Bson> textSearchPipeline(SearchRequest request) {
        int skip = request.page() * PAGE_SIZE;
        return List.of(
                Aggregates.search(
                        SearchCollector.facet(textOrBrowseOperator(request), facetCollectors()),
                        searchOptions(request)
                                .index(MongoConnection.index_name)
                                .count(SearchCount.total())
                ),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE),
                Aggregates.facet(
                        new Facet("docs", List.of(SearchProjection.projectStage(request.includeLicense()))),
                        new Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1)
                        ))
                )
        );
    }

    public static List<Bson> vectorSearchPipeline(SearchRequest request, List<Double> queryVector, String collectionName) {
        int skip = request.page() * PAGE_SIZE;
        ArrayList<Bson> pipeline = new ArrayList<>();
        pipeline.add(searchStage(vectorSearchBody(request, queryVector, skip + PAGE_SIZE, request.returnStoredSource())));
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(PAGE_SIZE));
        pipeline.add(SearchProjection.projectStage(request.includeLicense()));
        pipeline.add(SearchProjection.docEnvelopeStage());
        pipeline.add(new Document("$unionWith", new Document("coll", collectionName).append("pipeline", vectorCountPipeline(request))));
        pipeline.add(resultAssemblyStage());
        pipeline.add(resultProjectionStage());
        return pipeline;
    }

    public static List<Bson> combinedSearchPipeline(SearchRequest request, List<Double> queryVector, int vectorLimit) {
        int skip = request.page() * PAGE_SIZE;
        return List.of(
                rankFusionStage(request, queryVector, vectorLimit),
                Aggregates.facet(
                        new Facet("docs", List.of(
                                Aggregates.skip(skip),
                                Aggregates.limit(PAGE_SIZE),
                                SearchProjection.projectStage(request.includeLicense())
                        )),
                        new Facet("meta", List.of(
                                Aggregates.count("total"),
                                SearchProjection.countedMetaEnvelopeStage()
                        ))
                )
        );
    }

    public static int pageSize() {
        return PAGE_SIZE;
    }

    private static SearchOperator textOrBrowseOperator(SearchRequest request) {
        List<SearchOperator> mustClauses = request.hasMeaningfulText()
                ? List.of(SearchOperator.text(fieldPath("caption"), request.text()))
                : List.of(SearchOperator.exists(fieldPath("caption")));
        return compoundOperator(mustClauses, request.filters().toSearchClauses());
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

    private static List<Bson> vectorCountPipeline(SearchRequest request) {
        return List.of(
                searchMetaStage(filteredDocumentCountBody(request)),
                SearchProjection.metaEnvelopeStage()
        );
    }

    private static Document filteredDocumentCountBody(SearchRequest request) {
        Document operator = compoundOperatorDocument(
                List.of(new Document("exists", new Document("path", "caption"))),
                request.filters().toSearchClauseDocuments()
        );

        return new Document("index", MongoConnection.vector_index_name)
                .append("compound", operator.get("compound"))
                .append("count", new Document("type", "total"));
    }

    private static Document vectorSearchBody(SearchRequest request, List<Double> queryVector, int limit, boolean returnStoredSource) {
        Document vectorSearch = new Document("path", "captionEmbedding")
                .append("queryVector", queryVector)
                .append("exact", true)
                .append("limit", limit);

        Document filter = filterOperatorDocument(request.filters().toSearchClauseDocuments());
        if (filter != null) {
            vectorSearch.append("filter", filter);
        }

        Document body = new Document("index", MongoConnection.vector_index_name)
                .append("vectorSearch", vectorSearch);
        if (returnStoredSource) {
            body.append("returnStoredSource", true);
        }
        return body;
    }

    private static Bson rankFusionStage(SearchRequest request, List<Double> queryVector, int vectorLimit) {
        LinkedHashMap<String, List<Document>> pipelines = new LinkedHashMap<>();
        pipelines.put("text", List.of(searchStage(textSearchBody(request))));
        pipelines.put("vector", List.of(searchStage(vectorSearchBody(request, queryVector, vectorLimit, false))));

        return new Document("$rankFusion", new Document("input", new Document("pipelines", pipelines)));
    }

    private static Document textSearchBody(SearchRequest request) {
        return new Document("index", MongoConnection.index_name)
                .append("compound", compoundOperatorDocument(
                        List.of(new Document("text", new Document("query", request.text()).append("path", "caption"))),
                        request.filters().toSearchClauseDocuments()
                ).get("compound"));
    }

    private static Document filterOperatorDocument(List<Document> filterClauses) {
        if (filterClauses.isEmpty()) {
            return null;
        }
        if (filterClauses.size() == 1) {
            return filterClauses.getFirst();
        }
        return new Document("compound", new Document("filter", filterClauses));
    }

    private static Document compoundOperatorDocument(List<Document> mustClauses, List<Document> filterClauses) {
        Document compound = new Document("must", mustClauses);
        if (!filterClauses.isEmpty()) {
            compound.append("filter", filterClauses);
        }
        return new Document("compound", compound);
    }

    private static Document searchStage(Document body) {
        return new Document("$search", body);
    }

    private static Document searchMetaStage(Document body) {
        return new Document("$searchMeta", body);
    }

    private static Document resultAssemblyStage() {
        return new Document("$group", new Document("_id", null)
                .append("docs", new Document("$push", new Document("$cond", List.of(
                        new Document("$eq", List.of("$__resultType", "doc")),
                        "$doc",
                        "$$REMOVE"
                ))))
                .append("meta", new Document("$push", new Document("$cond", List.of(
                        new Document("$eq", List.of("$__resultType", "meta")),
                        "$meta",
                        "$$REMOVE"
                )))));
    }

    private static Document resultProjectionStage() {
        return new Document("$project", new Document("_id", 0).append("docs", 1).append("meta", 1));
    }

    private static List<com.mongodb.client.model.search.SearchFacet> facetCollectors() {
        return SearchCategory.filterable().stream()
                .map(category -> stringFacet(category.fieldName(), fieldPath(category.fieldName())).numBuckets(10))
                .map(facet -> (com.mongodb.client.model.search.SearchFacet) facet)
                .toList();
    }
}

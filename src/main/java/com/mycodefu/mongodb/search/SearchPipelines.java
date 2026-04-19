package com.mycodefu.mongodb.search;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.search.SearchCollector;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchOptions;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.Document;
import org.bson.conversions.Bson;

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
                        searchOptions(request).index(MongoConnection.index_name)
                ),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE + 1),
                Aggregates.facet(
                        new com.mongodb.client.model.Facet("docs", List.of(SearchProjection.projectStage(request.includeLicense()))),
                        new com.mongodb.client.model.Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1),
                                SearchProjection.facetMetaStage()
                        ))
                )
        );
    }

    public static List<Bson> vectorSearchPipeline(SearchRequest request, List<Double> queryVector) {
        int skip = request.page() * PAGE_SIZE;
        return List.of(
                searchStage(vectorSearchBody(request, queryVector, resultWindowLimit(request.page()), request.returnStoredSource())),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE + 1),
                Aggregates.facet(
                        new com.mongodb.client.model.Facet("docs", List.of(SearchProjection.projectStage(request.includeLicense()))),
                        new com.mongodb.client.model.Facet("meta", List.of(
                                Aggregates.limit(1),
                                SearchProjection.emptyMetaStage()
                        ))
                )
        );
    }

    public static List<Bson> combinedSearchPipeline(SearchRequest request, List<Double> queryVector) {
        int skip = request.page() * PAGE_SIZE;
        return List.of(
                rankFusionStage(request, queryVector, resultWindowLimit(request.page())),
                Aggregates.skip(skip),
                Aggregates.limit(PAGE_SIZE + 1),
                Aggregates.facet(
                        new com.mongodb.client.model.Facet("docs", List.of(
                                SearchProjection.projectStage(request.includeLicense())
                        )),
                        new com.mongodb.client.model.Facet("meta", List.of(
                                Aggregates.limit(1),
                                SearchProjection.emptyMetaStage()
                        ))
                )
        );
    }

    public static int pageSize() {
        return PAGE_SIZE;
    }

    public static int resultWindowLimit(int page) {
        return (Math.max(0, page) * PAGE_SIZE) + PAGE_SIZE + 1;
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

    private static List<com.mongodb.client.model.search.SearchFacet> facetCollectors() {
        return SearchCategory.filterable().stream()
                .map(category -> stringFacet(category.fieldName(), fieldPath(category.fieldName())).numBuckets(10))
                .map(facet -> (com.mongodb.client.model.search.SearchFacet) facet)
                .toList();
    }
}

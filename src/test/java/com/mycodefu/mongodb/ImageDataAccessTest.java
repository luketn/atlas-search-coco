package com.mycodefu.mongodb;

import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.SearchType;
import com.mycodefu.mongodb.atlas.MongoConnection;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import org.bson.Document;
import org.junit.Test;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;


public class ImageDataAccessTest extends AtlasDataTest {

    @Test
    public void get() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        Image exampleImage = imageDataAccess.get(79047);
        assertNotNull(exampleImage);

        assertEquals(79047, exampleImage._id());
        assertEquals("Snow surrounds a standing bear statue on a sidewalk.", exampleImage.caption());
        assertEquals(List.of(1.0, 0.2, 0.0, 0.0), exampleImage.captionEmbedding());
        assertEquals("sample-test-vector-v1", exampleImage.captionEmbeddingModel());
        assertEquals("http://images.cocodataset.org/train2017/000000079047.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384775627000L), exampleImage.dateCaptured());
        assertEquals("Attribution-NonCommercial-ShareAlike License", exampleImage.licenseName());
        assertEquals("http://creativecommons.org/licenses/by-nc-sa/2.0/", exampleImage.licenseUrl());
        assertFalse(exampleImage.hasPerson());
        assertEquals(1, exampleImage.animal().size());
        assertEquals("bear", exampleImage.animal().getFirst());
        assertEquals(1, exampleImage.outdoor().size());
        assertEquals("bench", exampleImage.outdoor().getFirst());
        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.furniture());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.kitchen());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());

    }

    @Test
    public void search_bear() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null);
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().get(0);
        assertEquals(79047, exampleImage._id());
        assertEquals("Snow surrounds a standing bear statue on a sidewalk.", exampleImage.caption());
        assertNull(exampleImage.captionEmbedding());
        assertNull(exampleImage.captionEmbeddingModel());
        assertEquals("http://images.cocodataset.org/train2017/000000079047.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384775627000L), exampleImage.dateCaptured());
        assertNull(exampleImage.licenseName());
        assertNull(exampleImage.licenseUrl());
        assertFalse(exampleImage.hasPerson());
        assertEquals(1, exampleImage.animal().size());
        assertEquals("bear", exampleImage.animal().getFirst());
        assertEquals(1, exampleImage.outdoor().size());
        assertEquals("bench", exampleImage.outdoor().getFirst());
        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.furniture());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.kitchen());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());
    }

    @Test
    public void search_bird() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(
                "bread basket", 0,
                true,
                List.of("bird"),
                null, null, null,
                List.of("chair", "dining table"),
                null,
                List.of("cup"),
                null, null, null
        );
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().get(0);
        assertEquals(527040, exampleImage._id());
        assertEquals("Three birds sitting on a bread basket near a newspaper.", exampleImage.caption());
        assertNull(exampleImage.captionEmbedding());
        assertNull(exampleImage.captionEmbeddingModel());
        assertEquals("http://images.cocodataset.org/train2017/000000527040.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384546777000L), exampleImage.dateCaptured());
        assertNull(exampleImage.licenseName());
        assertNull(exampleImage.licenseUrl());

        assertTrue(exampleImage.hasPerson());

        assertEquals(1, exampleImage.animal().size());
        assertEquals("bird", exampleImage.animal().getFirst());

        assertEquals(2, exampleImage.furniture().size());
        assertEquals("chair", exampleImage.furniture().getFirst());
        assertEquals("dining table", exampleImage.furniture().getLast());

        assertEquals(2, exampleImage.kitchen().size());
        assertEquals("cup", exampleImage.kitchen().getFirst());
        assertEquals("bowl", exampleImage.kitchen().getLast());

        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());
    }

    @Test
    public void search_cat_pages() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(
                "cat",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResult);
        assertEquals(10, searchResult.docs().size());

        assertEquals(13, searchResult.meta().getFirst().count().total());

        ImageSearchResult searchResultPage2 = imageDataAccess.search(
                "cat",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResultPage2);
        assertEquals(3, searchResultPage2.docs().size());

        ImageSearchResult searchResultPage3 = imageDataAccess.search(
                "cat",
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResultPage3);
        assertEquals(0, searchResultPage3.docs().size());
    }

    @Test
    public void empty_text_browses_all_documents() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        long totalDocuments = MongoConnection.connection()
                .getDatabase(MongoConnection.database_name)
                .getCollection(ImageDataAccess.collection_name, Document.class)
                .countDocuments();

        ImageSearchResult searchResult = imageDataAccess.search(
                null,
                EnumSet.of(SearchType.Text, SearchType.Vector),
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(searchResult);
        assertEquals(10, searchResult.docs().size());
        assertEquals(totalDocuments, searchResult.meta().getFirst().count().total());
    }

    @Test
    public void category_only_search_without_text_returns_filtered_documents() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(
                null,
                EnumSet.of(SearchType.Text, SearchType.Vector),
                0,
                null,
                List.of("bear"),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("bench"),
                null,
                null,
                null
        );

        assertNotNull(searchResult);
        assertFalse(searchResult.docs().isEmpty());
        assertEquals(2, searchResult.meta().getFirst().count().total());
        assertTrue(searchResult.docs().stream().allMatch(image ->
                image.animal() != null && image.animal().contains("bear")
                        && image.outdoor() != null && image.outdoor().contains("bench")));
    }

    @Test
    public void search_includes_stats_when_command_tracing_enabled() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null);

            assertNotNull(searchResult);
            assertNotNull(searchResult.stats());
            assertNotNull(searchResult.stats().traceId());
            assertFalse(searchResult.stats().operations().isEmpty());
            assertTrue(searchResult.stats().totalTimeMs() > 0.0);
            assertTrue(searchResult.stats().operations().stream().anyMatch(operation -> "aggregate".equals(operation.commandName())));
            assertEquals(
                    searchResult.stats().operations().stream().mapToDouble(operation -> operation.timeMs()).sum(),
                    searchResult.stats().totalTimeMs(),
                    0.0001
            );
        } finally {
            System.clearProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY);
        }
    }

    @Test
    public void vector_search_returns_semantic_match() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(1.0, 0.2, 0.0, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(
                    "icy sculpture on a footpath",
                    EnumSet.of(SearchType.Vector),
                    0,
                    null,
                    List.of("bear"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("bench"),
                    null,
                    null
            );

            assertNotNull(searchResult);
            assertFalse(searchResult.docs().isEmpty());
            assertEquals(79047, searchResult.docs().getFirst()._id());
            assertNull(searchResult.docs().getFirst().captionEmbedding());
            assertNull(searchResult.docs().getFirst().captionEmbeddingModel());
            assertNull(searchResult.docs().getFirst().licenseName());
            assertNull(searchResult.docs().getFirst().licenseUrl());
            assertTrue(searchResult.meta().getFirst().count().total() >= 1);
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void vector_search_respects_score_cutoff() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.7, 0.7, 0.0, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(
                    "bear-like query with weaker similarity",
                    EnumSet.of(SearchType.Vector),
                    0,
                    null,
                    List.of("bear"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("bench"),
                    null,
                    null,
                    0.95
            );

            assertNotNull(searchResult);
            assertTrue(searchResult.docs().isEmpty());
            assertEquals(0, searchResult.meta().getFirst().count().total());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void vector_search_handles_large_result_sets_without_limit_num_candidates_error() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.3, 0.3, 0.3, 0.3), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(
                    "cow",
                    EnumSet.of(SearchType.Vector),
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0.0
            );

            assertNotNull(searchResult);
            assertNotNull(searchResult.meta());
            assertNotNull(searchResult.meta().getFirst());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void hybrid_search_combines_text_and_vector_results() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.0, 1.0, 0.15, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(
                    "newspaper",
                    EnumSet.of(SearchType.Text, SearchType.Vector),
                    0,
                    true,
                    List.of("bird"),
                    null,
                    null,
                    null,
                    List.of("chair", "dining table"),
                    null,
                    List.of("cup"),
                    null,
                    null,
                    null
            );

            assertNotNull(searchResult);
            assertFalse(searchResult.docs().isEmpty());
            assertEquals(527040, searchResult.docs().getFirst()._id());
            assertNull(searchResult.docs().getFirst().captionEmbedding());
            assertNull(searchResult.docs().getFirst().captionEmbeddingModel());
            assertNull(searchResult.docs().getFirst().licenseName());
            assertNull(searchResult.docs().getFirst().licenseUrl());
            assertEquals(1, searchResult.meta().getFirst().count().total());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void run_with_traces_returns_identifiable_commands_for_the_run() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            List<MongoConnectionTracing.CommandTrace> traces = MongoConnectionTracing.runWithTraces(
                    () -> imageDataAccess.search("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null)
            );

            assertFalse(traces.isEmpty());
            assertTrue(traces.stream().allMatch(trace -> trace.requestId() >= 0));
            assertTrue(traces.stream().allMatch(trace -> trace.durationMs() >= 0.0));
            assertTrue(traces.stream().anyMatch(trace -> "aggregate".equals(trace.commandName()) && trace.traceId() != null));
        } finally {
            System.clearProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY);
        }
    }
}

package com.mycodefu.mongodb;

import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.model.SearchType;
import com.mycodefu.mongodb.atlas.MongoConnectionTracing;
import com.mycodefu.mongodb.search.SearchFilters;
import com.mycodefu.mongodb.search.SearchPipelines;
import com.mycodefu.mongodb.search.SearchRequest;
import org.junit.Test;

import java.util.Date;
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
    public void delete_removes_image_by_id() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        Image image = new Image(
                9_999_001,
                "Temporary delete test image.",
                null,
                null,
                "http://example.test/delete-test.jpg",
                100,
                100,
                new Date(),
                "Test License",
                null,
                false,
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
        imageDataAccess.insert(image);

        assertNotNull(imageDataAccess.get(image._id()));
        assertTrue(imageDataAccess.delete(image._id()));
        assertNull(imageDataAccess.get(image._id()));
        assertFalse(imageDataAccess.delete(image._id()));
    }

    @Test
    public void search_bear() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(request("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null));
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().getFirst();
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
        ImageSearchResult searchResult = imageDataAccess.search(request(
                "bread basket", 0,
                true,
                List.of("bird"),
                null, null, null,
                List.of("chair", "dining table"),
                null,
                List.of("cup"),
                null, null, null
        ));
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().getFirst();
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
    public void search_include_license_fetches_license_fields_via_single_search_query() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(request(
                "statue",
                SearchType.Text,
                0,
                null,
                List.of("bear"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        ));

        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().getFirst();
        assertEquals(79047, exampleImage._id());
        assertEquals("Attribution-NonCommercial-ShareAlike License", exampleImage.licenseName());
        assertEquals("http://creativecommons.org/licenses/by-nc-sa/2.0/", exampleImage.licenseUrl());
    }

    @Test
    public void search_cat_pages() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(request("cat", 0, null, null, null, null, null, null, null, null, null, null, null));
        assertNotNull(searchResult);
        assertEquals(10, searchResult.docs().size());
        assertTrue(searchResult.meta().getFirst().hasMore());
        assertDateCapturedDescending(searchResult.docs());

        ImageSearchResult searchResultPage2 = imageDataAccess.search(request("cat", 1, null, null, null, null, null, null, null, null, null, null, null));
        assertNotNull(searchResultPage2);
        assertEquals(3, searchResultPage2.docs().size());
        assertFalse(searchResultPage2.meta().getFirst().hasMore());

        ImageSearchResult searchResultPage3 = imageDataAccess.search(request("cat", 2, null, null, null, null, null, null, null, null, null, null, null));
        assertNotNull(searchResultPage3);
        assertEquals(0, searchResultPage3.docs().size());
        assertFalse(searchResultPage3.meta().getFirst().hasMore());
    }

    @Test
    public void empty_text_browses_all_documents() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(request(
                null,
                SearchType.Combined,
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
                false
        ));

        assertNotNull(searchResult);
        assertEquals(SearchPipelines.pageSize(), searchResult.docs().size());
        assertDateCapturedDescending(searchResult.docs());
        assertTrue(searchResult.meta().getFirst().hasMore());
    }

    @Test
    public void category_only_search_without_text_returns_filtered_documents() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(request(
                null,
                SearchType.Combined,
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
                false
        ));

        assertNotNull(searchResult);
        assertFalse(searchResult.docs().isEmpty());
        assertFalse(searchResult.meta().getFirst().hasMore());
        assertTrue(searchResult.docs().stream().allMatch(image ->
                image.animal() != null && image.animal().contains("bear")
                        && image.outdoor() != null && image.outdoor().contains("bench")));
    }

    @Test
    public void search_includes_stats_when_command_tracing_enabled() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null));

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
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "icy sculpture on a footpath",
                    SearchType.Vector,
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
                    false
            ));

            assertNotNull(searchResult);
            assertFalse(searchResult.docs().isEmpty());
            assertDateCapturedDescending(searchResult.docs());
            assertNull(searchResult.docs().getFirst().captionEmbedding());
            assertNull(searchResult.docs().getFirst().captionEmbeddingModel());
            assertNull(searchResult.docs().getFirst().licenseName());
            assertNull(searchResult.docs().getFirst().licenseUrl());
            assertFalse(searchResult.meta().getFirst().hasMore());
            assertNull(searchResult.meta().getFirst().facet());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void vector_search_include_license_fetches_license_fields_via_single_search_query() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(1.0, 0.2, 0.0, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "icy sculpture on a footpath",
                    SearchType.Vector,
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
                    true
            ));

            assertNotNull(searchResult);
            assertFalse(searchResult.docs().isEmpty());
            assertDateCapturedDescending(searchResult.docs());
            assertNotNull(searchResult.docs().getFirst().licenseName());
            assertNotNull(searchResult.docs().getFirst().licenseUrl());
            assertNull(searchResult.meta().getFirst().facet());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void vector_search_executes_single_aggregate_command() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(1.0, 0.2, 0.0, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "icy sculpture on a footpath",
                    SearchType.Vector,
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
                    false
            ));

            assertNotNull(searchResult);
            assertNotNull(searchResult.stats());
            assertEquals(1, searchResult.stats().operations().stream()
                    .filter(operation -> "aggregate".equals(operation.commandName()))
                    .count());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
            System.clearProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY);
        }
    }

    @Test
    public void vector_search_reports_has_more_for_broad_result_set() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.3, 0.3, 0.3, 0.3), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "cow",
                    SearchType.Vector,
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
                    false
            ));

            assertNotNull(searchResult);
            assertTrue(searchResult.meta().getFirst().hasMore());
            assertNull(searchResult.meta().getFirst().facet());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void combined_search_combines_text_and_vector_results() {
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.0, 1.0, 0.15, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "newspaper",
                    SearchType.Combined,
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
                    null,
                    false
            ));

            assertNotNull(searchResult);
            assertFalse(searchResult.docs().isEmpty());
            assertEquals(527040, searchResult.docs().getFirst()._id());
            assertNull(searchResult.docs().getFirst().captionEmbedding());
            assertNull(searchResult.docs().getFirst().captionEmbeddingModel());
            assertNull(searchResult.docs().getFirst().licenseName());
            assertNull(searchResult.docs().getFirst().licenseUrl());
            assertFalse(searchResult.meta().getFirst().hasMore());
            assertNull(searchResult.meta().getFirst().facet());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
        }
    }

    @Test
    public void combined_search_executes_single_aggregate_command() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        LMStudioEmbedding.setEmbeddingProviderForTests(text -> new LMStudioEmbedding.EmbeddingResult(List.of(0.0, 1.0, 0.15, 0.0), "test-query-model"));
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            ImageSearchResult searchResult = imageDataAccess.search(request(
                    "newspaper",
                    SearchType.Combined,
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
                    null,
                    false
            ));

            assertNotNull(searchResult.stats());
            assertEquals(1, searchResult.stats().operations().stream()
                    .filter(operation -> "aggregate".equals(operation.commandName()))
                    .count());
        } finally {
            LMStudioEmbedding.setEmbeddingProviderForTests(null);
            System.clearProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY);
        }
    }

    @Test
    public void run_with_traces_returns_identifiable_commands_for_the_run() {
        System.setProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY, "true");
        try {
            ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
            List<MongoConnectionTracing.CommandTrace> traces = MongoConnectionTracing.runWithTraces(
                    () -> imageDataAccess.search(request("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null))
            );

            assertFalse(traces.isEmpty());
            assertTrue(traces.stream().allMatch(trace -> trace.requestId() >= 0));
            assertTrue(traces.stream().allMatch(trace -> trace.durationMs() >= 0.0));
            assertTrue(traces.stream().anyMatch(trace -> "aggregate".equals(trace.commandName()) && trace.traceId() != null));
        } finally {
            System.clearProperty(MongoConnectionTracing.TRACE_COMMANDS_PROPERTY);
        }
    }

    private static SearchRequest request(
            String text,
            int page,
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
        return request(text, SearchType.Text, page, hasPerson, animal, appliance, electronic, food, furniture, indoor, kitchen, outdoor, sports, vehicle, false);
    }

    private static SearchRequest request(
            String text,
            SearchType searchType,
            int page,
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
            List<String> vehicle,
            boolean includeLicense
    ) {
        return SearchRequest.of(
                text,
                searchType,
                page,
                new SearchFilters(hasPerson, animal, appliance, electronic, food, furniture, indoor, kitchen, outdoor, sports, vehicle),
                includeLicense
        );
    }

    private static void assertDateCapturedDescending(List<Image> images) {
        for (int index = 1; index < images.size(); index++) {
            Date previous = images.get(index - 1).dateCaptured();
            Date current = images.get(index).dateCaptured();
            if (previous != null && current != null) {
                assertFalse(previous.before(current));
            }
        }
    }
}

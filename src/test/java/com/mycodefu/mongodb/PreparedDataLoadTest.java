package com.mycodefu.mongodb;

import com.mycodefu.model.Image;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static com.mycodefu.datapreparation.PrepareDataEntryPoint.loadPreparedDataset;
import static org.junit.Assert.*;

public class PreparedDataLoadTest extends AtlasDataTest {

    @Test
    public void loadPreparedDataset_fromSampleResourceFiles_loadsDataAndVectorIndex() throws IOException {
        Path dataDirectory = Files.createTempDirectory("atlas-search-coco-sample-data");
        copyResource("/sample-data/category.json", dataDirectory.resolve("%s.%s.json".formatted(
                MongoConnection.database_name,
                CategoryDataAccess.collection_name
        )));
        copyResource("/sample-data/image.json", dataDirectory.resolve("%s.%s.json".formatted(
                MongoConnection.database_name,
                ImageDataAccess.collection_name
        )));

        loadPreparedDataset(dataDirectory, "/atlas-vector-search-index-test.json");

        List<?> categories = CategoryDataAccess.getInstance().list();
        assertEquals(10, categories.size());

        Image loadedImage = ImageDataAccess.getInstance().get(79047);
        assertNotNull(loadedImage);
        assertEquals("Snow surrounds a standing bear statue on a sidewalk.", loadedImage.caption());
        assertEquals(List.of(1.0, 0.2, 0.0, 0.0), loadedImage.captionEmbedding());
        assertEquals("sample-test-vector-v1", loadedImage.captionEmbeddingModel());
        assertEquals(new Date(1384775627000L), loadedImage.dateCaptured());

        assertTrue(ImageDataAccess.getInstance().refreshVectorSearchIndexState().available());
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream resourceStream = PreparedDataLoadTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull("Missing test resource: " + resourcePath, resourceStream);
            Files.copy(resourceStream, target);
        }
    }
}

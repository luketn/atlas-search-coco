package com.mycodefu.mongodb;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.BsonDocument;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

abstract class AtlasDataTest {
    private static final MongoDBAtlasLocalContainer atlasLocalContainer;
    static {
        atlasLocalContainer = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:8.0.3");
        atlasLocalContainer.start();
        MongoConnection.setConnectionString(atlasLocalContainer.getConnectionString());

        InputStream categoryDataStream = AtlasDataTest.class.getResourceAsStream("/sample-data/category.json");
        List<Category> categories = Arrays.asList(JsonUtil.readStream(categoryDataStream, Category[].class));
        CategoryDataAccess.getInstance().insertBulk(categories);

        InputStream imageDataStream = AtlasDataTest.class.getResourceAsStream("/sample-data/image.json");
        List<Image> images = Arrays.asList(JsonUtil.readStream(imageDataStream, Image[].class));
        ImageDataAccess.getInstance().insertBulk(images);


        String atlasSearchMappingsString;
        try {
            atlasSearchMappingsString = Resources.toString(AtlasDataTest.class.getResource("/atlas-search-index.json"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var atlasSearchMappings = BsonDocument.parse(atlasSearchMappingsString);
        MongoConnection.createAtlasIndex(
                MongoConnection.database_name,
                ImageDataAccess.collection_name,
                MongoConnection.index_name,
                atlasSearchMappings
        );
    }
}

package com.mycodefu.mongodb;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.BsonDocument;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        InputStream atlasSearchMappingsStream = AtlasDataTest.class.getResourceAsStream("/atlas-search-index.json");
        //read to a string
        BufferedReader br = new BufferedReader(new InputStreamReader(atlasSearchMappingsStream));
        StringBuilder sb = new StringBuilder();
        br.lines().forEach(sb::append);

        var atlasSearchMappings = BsonDocument.parse(sb.toString());
        MongoConnection.createAtlasIndex(
                MongoConnection.database_name,
                ImageDataAccess.collection_name,
                MongoConnection.index_name,
                atlasSearchMappings
        );

    }
}

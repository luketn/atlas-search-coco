package com.mycodefu.mongodb;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import java.io.InputStream;
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
    }
}

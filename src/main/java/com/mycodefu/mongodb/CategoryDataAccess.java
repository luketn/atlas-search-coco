package com.mycodefu.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mycodefu.model.Category;
import com.mycodefu.mongodb.atlas.MongoConnection;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class CategoryDataAccess implements AutoCloseable {
    public static final String collection_name = "Category";

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Category> categoryCollection;

    public CategoryDataAccess(MongoClient mongoClient, String databaseName, String collectionName) {
        this.mongoClient = mongoClient;
        this.database = mongoClient.getDatabase(databaseName);
        this.categoryCollection = database.getCollection(collectionName, Category.class);
    }

    public static CategoryDataAccess getInstance() {
        return new CategoryDataAccess(
                MongoConnection.connection(),
                database_name,
                collection_name
        );
    }

    public List<Category> list() {
        return categoryCollection.find().into(List.of());
    }

    public void insert(Category category) {
        categoryCollection.insertOne(category);
    }

    public void insertBulk(List<Category> categories) {
        categoryCollection.insertMany(categories);
    }

    public Category get(int id) {
        return categoryCollection.find(eq("id", id)).first();
    }

    public void close() {
        mongoClient.close();
    }
}
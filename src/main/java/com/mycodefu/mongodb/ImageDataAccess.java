package com.mycodefu.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.search.SearchCollector;
import com.mongodb.client.model.search.SearchCount;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchOptions;
import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.search.SearchFacet.stringFacet;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mycodefu.mongodb.atlas.MongoConnection.database_name;

public class ImageDataAccess implements AutoCloseable {
    public static final String collection_name = "Image";

    private final MongoClient mongoClient;
    private final MongoCollection<Image> imageCollection;
    private final MongoCollection<Document> imageCollectionForAggregate;

    public ImageDataAccess(MongoClient mongoClient, String databaseName, String collectionName) {
        this.mongoClient = mongoClient;
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.imageCollection = database.getCollection(collectionName, Image.class);
        this.imageCollectionForAggregate = database.getCollection(collectionName, Document.class);
    }

    public static ImageDataAccess getInstance() {
        return new ImageDataAccess(
                MongoConnection.connection(),
                database_name,
                collection_name
        );
    }

    public void insert(Image image) {
        imageCollection.insertOne(image);
    }

    public void insertBulk(List<Image> images) {
        imageCollection.insertMany(images);
    }

    public Image get(int id) {
        return imageCollection.find(eq("_id", id)).first();
    }

    public void removeAll() {
        imageCollection.deleteMany(new Document());
    }

    public ImageSearchResult search(String text, Integer page, Boolean hasPerson, List<String> animal, List<String> appliance, List<String> electronic, List<String> food, List<String> furniture, List<String> indoor, List<String> kitchen, List<String> outdoor, List<String> sports, List<String> vehicle) {
        List<SearchOperator> clauses = new ArrayList<>();

        int skip = 0;
        int pageSize = 5;
        if (page != null) {
            skip = page * pageSize;
        }
        if (text != null) {
            clauses.add(SearchOperator.of(
                    new Document("text", new Document()
                            .append("path", "caption")
                            .append("query", text)
                    ))
            );
        }
        if (hasPerson != null) {
            clauses.add(SearchOperator.of(
                    new Document("equals", new Document()
                            .append("path", "hasPerson")
                            .append("value", hasPerson)
                    ))
            );
        }
        Object[] params = new Object[]{
                "animal", animal,
                "appliance", appliance,
                "electronic", electronic,
                "food", food,
                "furniture", furniture,
                "indoor", indoor,
                "kitchen", kitchen,
                "outdoor", outdoor,
                "sports", sports,
                "vehicle", vehicle};
        for (int i = 0; i < params.length; i+=2) {
            String category = (String)params[i];
            List<String> values = (List<String>)params[i+1];
            if (values != null) {
                for (String value : values) {
                    clauses.add(SearchOperator.of(
                            new Document("equals", new Document()
                                    .append("path", category)
                                    .append("value", value)
                            ))
                    );
                }
            }
        }
        List<Bson> aggregateStages = List.of(
                Aggregates.search(
                        SearchCollector.facet(
                                SearchOperator.compound().filter(clauses),
                                List.of(
                                        stringFacet("animal", fieldPath("animal")).numBuckets(10),
                                        stringFacet("appliance", fieldPath("appliance")).numBuckets(10),
                                        stringFacet("electronic", fieldPath("electronic")).numBuckets(10),
                                        stringFacet("food", fieldPath("food")).numBuckets(10),
                                        stringFacet("furniture", fieldPath("furniture")).numBuckets(10),
                                        stringFacet("indoor", fieldPath("indoor")).numBuckets(10),
                                        stringFacet("kitchen", fieldPath("kitchen")).numBuckets(10),
                                        stringFacet("outdoor", fieldPath("outdoor")).numBuckets(10),
                                        stringFacet("sports", fieldPath("sports")).numBuckets(10),
                                        stringFacet("vehicle", fieldPath("vehicle")).numBuckets(10)
                                )
                        ), SearchOptions.searchOptions().count(SearchCount.total())),
                Aggregates.skip(skip),
                Aggregates.limit(pageSize),
                Aggregates.facet(
                        new Facet("docs", List.of()),
                        new Facet("meta", List.of(
                                Aggregates.replaceWith("$$SEARCH_META"),
                                Aggregates.limit(1)
                        ))
                )

        );
        for (Bson aggregateStage : aggregateStages) {
            System.out.println(aggregateStage.toBsonDocument().toJson(JsonWriterSettings.builder().indent(true).build()));
        }
        ArrayList<Document> results = imageCollectionForAggregate.aggregate(aggregateStages).into(new ArrayList<>());

        if (results.isEmpty()) {
            return null;
        }

        Document document = results.getFirst();
        String json = document.toJson(JsonWriterSettings.builder().indent(true).build());

        System.out.println(json);


        ImageSearchResult imageSearchResult = JsonUtil.readValue(json, ImageSearchResult.class);

        return imageSearchResult;
    }

    public void close() {
        mongoClient.close();
    }
}

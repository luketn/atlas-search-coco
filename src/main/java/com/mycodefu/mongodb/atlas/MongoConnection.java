package com.mycodefu.mongodb.atlas;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mycodefu.mongodb.atlas.WaitUtil.waitUntil;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.bson.codecs.pojo.Conventions.*;

public class MongoConnection {
    private static final Logger log = LoggerFactory.getLogger(MongoConnection.class);

    public static final String database_name = System.getenv("DATABASE_NAME") != null ? System.getenv("DATABASE_NAME") : "atlasSearchCoco";
    public static final String index_name = System.getenv("INDEX_NAME") != null ? System.getenv("INDEX_NAME") : "default";
    public static final String vector_index_name = System.getenv("VECTOR_INDEX_NAME") != null ? System.getenv("VECTOR_INDEX_NAME") : "vector_caption";

    private static String connection_string = System.getenv("CONNECTION_STRING") != null ? System.getenv("CONNECTION_STRING") : "mongodb://localhost:27017/directConnection=true";
    private static MongoClient mongo_client = null;

    public static CodecRegistry getCodecRegistry() {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder()
                .automatic(true)
                .conventions(Arrays.asList(
                        SET_PRIVATE_FIELDS_CONVENTION,
                        OBJECT_ID_GENERATORS,
                        ANNOTATION_CONVENTION))
                .build();
        return fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
    }

    public static MongoClient connection() {
        if (mongo_client == null) {
            long start = System.currentTimeMillis();

            MongoClientSettings clientSettings = MongoClientSettings.builder()
                    .codecRegistry(getCodecRegistry())
                    .applyConnectionString(new ConnectionString(connection_string))
                    .addCommandListener(MongoConnectionTracing.commandListener())
                    .build();
            mongo_client = MongoClients.create(clientSettings);

            long end = System.currentTimeMillis();

            log.info("Connected to MongoDB at {} in {}ms", connection_string.replaceAll("://.*@", "://<redacted>@"), end - start);
        }
        return mongo_client;
    }

    public static void setConnectionString(String connectionString) {
        mongo_client = null;
        connection_string = connectionString;
    }

    public static void createAtlasIndex(String databaseName, String collectionName, String indexName, BsonDocument mappingsDocument) {
        createAtlasIndex(databaseName, collectionName, Document.class, indexName, mappingsDocument, List.of());
    }

    public static <T> void createAtlasIndex(String databaseName, String collectionName, Class<T> clazz, String indexName, BsonDocument mappingsDocument, List<T> sampleDocuments) {
        createSearchIndex(databaseName, collectionName, clazz, indexName, mappingsDocument, SearchIndexType.search(), sampleDocuments);
    }

    private static <T> void createSearchIndex(String databaseName, String collectionName, Class<T> clazz, String indexName, BsonDocument definitionDocument, SearchIndexType searchIndexType, List<T> sampleDocuments) {
        MongoDatabase database = connection().getDatabase(databaseName);
        MongoCollection<T> collection = database.getCollection(collectionName, clazz);

        //insert sample documents
        if (!sampleDocuments.isEmpty()) {
            collection.insertMany(sampleDocuments);
        }

        Instant start = Instant.now();
        boolean indexCreated = createIndex(indexName, definitionDocument, searchIndexType, collection);
        if (!indexCreated) {
            throw new RuntimeException("Failed to create Atlas search index %s".formatted(indexName));
        }
        waitUntil(() -> indexReady(collection, indexName), 600, 100, "Atlas search index not ready");

        //log time taken to create index with the collection and index name
        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        log.info("Time taken to create atlas index %s on collection %s: %s".formatted(indexName, collectionName, timeElapsed));
    }

    private static boolean createIndex(String indexName, BsonDocument definitionDocument, SearchIndexType searchIndexType, MongoCollection<?> collection) {
        boolean indexCreated = false;
        try {
            dropSearchIndex(indexName, collection);
            if (log.isDebugEnabled()) {
                Set<String> fields = definitionDocument.containsKey("mappings")
                        ? definitionDocument.getDocument("mappings").getDocument("fields").keySet()
                        : Set.of("vector");
                log.debug("Creating Atlas search index '%s' with fields: %s".formatted(indexName, fields));
            }
            collection.createSearchIndexes(List.of(new SearchIndexModel(indexName, definitionDocument, searchIndexType)));
            log.debug("Atlas search index '%s' created".formatted(indexName));
            indexCreated = true;
        } catch (Exception e) {
            log.warn("Error creating search index %s, retrying...".formatted(indexName), e);
        }
        return indexCreated;
    }

    private static boolean indexReady(MongoCollection<?> collection, String indexName) {
        ListSearchIndexesIterable<Document> indexDocuments = collection.listSearchIndexes();
        for (Document indexDocument : indexDocuments) {
            String name = indexDocument.getString("name");
            String status = indexDocument.getString("status");

            if (indexName.equals(name) && "READY".equals(status)) {
                log.debug("Atlas search index READY.");
                return true;
            }
        }
        return false;
    }

    private static void dropSearchIndex(String indexName, MongoCollection<?> collection) {
        for (Document index: collection.listSearchIndexes()) {
            if (index.getString("name").equals(indexName)) {
                collection.dropSearchIndex(indexName);
                break;
            }
        }
    }
}

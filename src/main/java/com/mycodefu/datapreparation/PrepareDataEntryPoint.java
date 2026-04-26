package com.mycodefu.datapreparation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mycodefu.lmstudio.LMStudioEmbedding;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.datapreparation.source.License;
import com.mycodefu.datapreparation.source.CaptionDataSource;
import com.mycodefu.datapreparation.source.ImageObject;
import com.mycodefu.datapreparation.source.InstanceDataSource;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.mongodb.atlas.MongoConnection;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.mycodefu.datapreparation.util.JsonUtil.readFromStreamWithoutClosing;
import static com.mycodefu.datapreparation.util.JsonUtil.writeToFile;

/**
 * This class reads the COCO dataset annotations from a zip file and writes the data to JSON files.
 */
public class PrepareDataEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(PrepareDataEntryPoint.class);
    private static final int IMPORT_BATCH_SIZE = 1_000;
    private static final Duration IMPORT_PROGRESS_LOG_INTERVAL = Duration.ofSeconds(5);
    private static final ObjectMapper preparedDataMapper = createPreparedDataMapper();

    public static void main(String[] args) throws IOException {
        downloadAndInitialiseDataset(false);
    }

    private static ObjectMapper createPreparedDataMapper() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new PreparedDateDeserializer());
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(module);
    }

    private static final class PreparedDateDeserializer extends JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return readDateValue(parser);
        }

        private Date readDateValue(JsonParser parser) throws IOException {
            JsonToken token = parser.currentToken();
            return switch (token) {
                case VALUE_NUMBER_INT -> new Date(parser.getLongValue());
                case VALUE_STRING -> parseDateString(parser.getText(), parser);
                case START_OBJECT -> readDateObject(parser);
                case VALUE_NULL -> null;
                default -> throw JsonMappingException.from(parser, "Unsupported date value token: " + token);
            };
        }

        private Date readDateObject(JsonParser parser) throws IOException {
            Date date = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if ("$date".equals(fieldName) || "$numberLong".equals(fieldName)) {
                    date = readDateValue(parser);
                } else {
                    parser.skipChildren();
                }
            }
            return date;
        }

        private Date parseDateString(String rawValue, JsonParser parser) throws IOException {
            String value = rawValue == null ? "" : rawValue.trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                if (value.matches("-?\\d+")) {
                    return new Date(Long.parseLong(value));
                }
                return Date.from(Instant.parse(value));
            } catch (RuntimeException e) {
                throw JsonMappingException.from(parser, "Unsupported date value: " + rawValue, e);
            }
        }
    }

    public static void downloadAndInitialiseDataset() throws IOException {
        downloadAndInitialiseDataset(false);
    }

    public static void downloadAndInitialiseDataset(boolean includeVectorEmbeddings) throws IOException {
        CocoDataset cocoDataset = downloadCocoDataset();

        List<Category> categories = getCategories(cocoDataset);
        List<Image> images = getImages(cocoDataset, includeVectorEmbeddings);

        writeToLocalMongoDB(categories, images);

        buildAtlasIndex();
        if (includeVectorEmbeddings) {
            buildAtlasVectorIndex();
        }

        // uncomment to refresh the data in the test resources
//         writeSampleData(images, categories);
    }

    public static void loadPreparedDataset(Path dataDirectory) throws IOException {
        loadPreparedDataset(dataDirectory, "/atlas-vector-search-index.json");
    }

    public static void loadPreparedDataset(Path dataDirectory, String vectorIndexResourcePath) throws IOException {
        if (!Files.isDirectory(dataDirectory)) {
            throw new IOException("Prepared data directory does not exist: %s".formatted(dataDirectory.toAbsolutePath()));
        }

        Path categoryFile = dataDirectory.resolve("%s.%s.json".formatted(MongoConnection.database_name, CategoryDataAccess.collection_name));
        Path imageFile = dataDirectory.resolve("%s.%s.json".formatted(MongoConnection.database_name, ImageDataAccess.collection_name));
        if (Files.notExists(categoryFile)) {
            throw new IOException("Prepared category data file does not exist: %s".formatted(categoryFile.toAbsolutePath()));
        }
        if (Files.notExists(imageFile)) {
            throw new IOException("Prepared image data file does not exist: %s".formatted(imageFile.toAbsolutePath()));
        }

        CategoryDataAccess categoryDataAccess = CategoryDataAccess.getInstance();
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        categoryDataAccess.removeAll();
        imageDataAccess.removeAll();

        importJsonArray(categoryFile, Category.class, categoryDataAccess::insertBulk, CategoryDataAccess.collection_name, _ -> false);
        boolean hasVectorEmbeddings = importJsonArray(imageFile, Image.class, imageDataAccess::insertBulk, ImageDataAccess.collection_name, PrepareDataEntryPoint::imageHasVectorEmbedding);

        buildAtlasIndex();
        if (hasVectorEmbeddings) {
            buildAtlasVectorIndex(vectorIndexResourcePath);
        }
    }

    private static <T> boolean importJsonArray(
            Path file,
            Class<T> documentClass,
            Consumer<List<T>> insertBulk,
            String collectionName,
            Predicate<T> vectorEmbeddingDetector
    ) throws IOException {
        long totalDocuments = countJsonArrayDocuments(file);
        log.info("Loading {} prepared documents from {} into collection {}", totalDocuments, file.toAbsolutePath(), collectionName);
        boolean hasVectorEmbeddings = false;
        BatchedInserter<T> inserter = BatchedInserter.create(insertBulk, collectionName, "prepared documents", totalDocuments);

        JsonFactory jsonFactory = preparedDataMapper.getFactory();
        try (JsonParser parser = jsonFactory.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Prepared data file must contain a JSON array: %s".formatted(file.toAbsolutePath()));
            }
            while (parser.nextToken() != null && parser.currentToken() != JsonToken.END_ARRAY) {
                T document = preparedDataMapper.readValue(parser, documentClass);
                if (!hasVectorEmbeddings && vectorEmbeddingDetector.test(document)) {
                    hasVectorEmbeddings = true;
                }
                inserter.add(document);
            }
        }

        inserter.finish();
        return hasVectorEmbeddings;
    }

    private static long countJsonArrayDocuments(Path file) throws IOException {
        long documents = 0;
        JsonFactory jsonFactory = preparedDataMapper.getFactory();
        try (JsonParser parser = jsonFactory.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Prepared data file must contain a JSON array: %s".formatted(file.toAbsolutePath()));
            }
            while (parser.nextToken() != null && parser.currentToken() != JsonToken.END_ARRAY) {
                parser.skipChildren();
                documents++;
            }
        }
        return documents;
    }

    private static boolean imageHasVectorEmbedding(Image image) {
        return image.captionEmbedding() != null && !image.captionEmbedding().isEmpty();
    }

    private static void writeToLocalMongoDB(List<Category> categories, List<Image> images) {
        CategoryDataAccess categoryDataAccess = CategoryDataAccess.getInstance();
        categoryDataAccess.removeAll();
        insertBulkWithProgress(categories, categoryDataAccess::insertBulk, CategoryDataAccess.collection_name);

        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        imageDataAccess.removeAll();
        insertBulkWithProgress(images, imageDataAccess::insertBulk, ImageDataAccess.collection_name);
    }

    private static <T> void insertBulkWithProgress(List<T> documents, Consumer<List<T>> insertBulk, String collectionName) {
        log.info("Loading {} documents into collection {}", documents.size(), collectionName);
        BatchedInserter<T> inserter = BatchedInserter.create(insertBulk, collectionName, "documents", documents.size());
        for (T document : documents) {
            inserter.add(document);
        }
        inserter.finish();
    }

    private static final class BatchedInserter<T> {
        private final Consumer<List<T>> insertBulk;
        private final LoadProgress progress;
        private final List<T> batch = new ArrayList<>(IMPORT_BATCH_SIZE);

        private BatchedInserter(Consumer<List<T>> insertBulk, String collectionName, String documentLabel, long totalDocuments) {
            this.insertBulk = insertBulk;
            this.progress = LoadProgress.start(collectionName, documentLabel, totalDocuments);
        }

        private static <T> BatchedInserter<T> create(Consumer<List<T>> insertBulk, String collectionName, String documentLabel, long totalDocuments) {
            return new BatchedInserter<>(insertBulk, collectionName, documentLabel, totalDocuments);
        }

        private void add(T document) {
            batch.add(document);
            if (batch.size() == IMPORT_BATCH_SIZE) {
                flush();
            }
        }

        private void finish() {
            flush();
            progress.finish();
        }

        private void flush() {
            if (batch.isEmpty()) {
                return;
            }

            insertBulk.accept(new ArrayList<>(batch));
            progress.loaded(batch.size());
            batch.clear();
        }
    }

    private static final class LoadProgress {
        private final String collectionName;
        private final String documentLabel;
        private final long totalDocuments;
        private final Instant startedAt;
        private Instant lastProgressLogAt;
        private long loadedDocuments;

        private LoadProgress(String collectionName, String documentLabel, long totalDocuments) {
            this.collectionName = collectionName;
            this.documentLabel = documentLabel;
            this.totalDocuments = totalDocuments;
            this.startedAt = Instant.now();
            this.lastProgressLogAt = startedAt;
        }

        private static LoadProgress start(String collectionName, String documentLabel, long totalDocuments) {
            return new LoadProgress(collectionName, documentLabel, totalDocuments);
        }

        private void loaded(long documentCount) {
            loadedDocuments += documentCount;
            Instant now = Instant.now();
            if (Duration.between(lastProgressLogAt, now).compareTo(IMPORT_PROGRESS_LOG_INTERVAL) >= 0) {
                log(now);
                lastProgressLogAt = now;
            }
        }

        private void finish() {
            log(Instant.now());
        }

        private void log(Instant now) {
            double percentage = totalDocuments == 0
                    ? 100.0
                    : loadedDocuments * 100.0 / totalDocuments;
            log.info("Loaded {}/{} {} into collection {} ({}%) after {}",
                    loadedDocuments,
                    totalDocuments,
                    documentLabel,
                    collectionName,
                    "%.2f".formatted(percentage),
                    Duration.between(startedAt, now));
        }
    }

    private static void buildAtlasIndex() throws IOException {
        String indexResource = readIndexResource("/atlas-search-index.json");

        MongoConnection.createAtlasIndex(
            MongoConnection.database_name,
            ImageDataAccess.collection_name,
            MongoConnection.index_name,
            BsonDocument.parse(indexResource)
        );
    }

    private static void buildAtlasVectorIndex() throws IOException {
        buildAtlasVectorIndex("/atlas-vector-search-index.json");
    }

    private static void buildAtlasVectorIndex(String resourcePath) throws IOException {
        String indexResource = readIndexResource(resourcePath);

        MongoConnection.createAtlasIndex(
                MongoConnection.database_name,
                ImageDataAccess.collection_name,
                MongoConnection.vector_index_name,
                BsonDocument.parse(indexResource)
        );
    }

    private static String readIndexResource(String resourcePath) throws IOException {
        URL resource = PrepareDataEntryPoint.class.getResource(resourcePath);
        Path path = Path.of(Objects.requireNonNull(resource).getPath());
        return Files.readString(path);
    }

    private static void writeSampleData(List<Image> images, List<Category> categories) {
        writeSampleData("image.json", getTenOfEachAnimal(images));
        writeSampleData("category.json", categories.stream().filter(c -> c.superCategory().equals("animal")).toList());
    }

    /**
     * Make a sample json file for unit tests with just the first 10 images for each category in the animal superCategory.
     */
    private static void writeSampleData(String resourceFilename, Object data) {
        try {
            Path dataDir = Path.of("src", "test", "java", "resources", "sample-data");
            if (Files.notExists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            writeToFile(dataDir.resolve(resourceFilename).toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write sample data %s.".formatted(resourceFilename), e);
        }
    }

    private static ArrayList<Image> getTenOfEachAnimal(List<Image> images) {
        Map<String, Integer> animalCategoryCounts = new HashMap<>();
        ArrayList<Image> animalImageInfos = new ArrayList<>(images.stream()
                .filter(image -> image.animal() != null)
                .filter(image -> !image.animal().isEmpty())
                .filter(image -> {
                    String animal = image.animal().getFirst();
                    animalCategoryCounts.put(animal, animalCategoryCounts.getOrDefault(animal, 0) + 1);
                    return animalCategoryCounts.get(animal) <= 10;
                })
                .toList());

        //sort by animal string and then date captured
        animalImageInfos
                .sort(Comparator.comparing((Image i) -> i.animal().isEmpty() ? "" : i.animal().getFirst())
                        .thenComparing(Image::dateCaptured));
        return animalImageInfos;
    }

    private static List<Image> getImages(CocoDataset cocoDataset, boolean includeVectorEmbeddings) {
        Map<Integer, List<CaptionDataSource.Annotation>> annotationMap = new HashMap<>();
        for (CaptionDataSource.Annotation annotation : cocoDataset.captionDataSource().annotations()) {
            annotationMap.computeIfAbsent(annotation.image_id(), k -> new ArrayList<>()).add(annotation);
        }

        Map<Integer, InstanceDataSource.Category> categoryMap = new HashMap<>();
        for (InstanceDataSource.Category category : cocoDataset.instanceDataSource().categories()) {
            categoryMap.put(category.id(), category);
        }

        Map<Integer, List<InstanceDataSource.Annotation>> instanceAnnotationMap = new HashMap<>();
        for (InstanceDataSource.Annotation annotation : cocoDataset.instanceDataSource().annotations()) {
            instanceAnnotationMap.computeIfAbsent(annotation.image_id(), k -> new ArrayList<>()).add(annotation);
        }

        List<License> licenses = getLicenses(cocoDataset);
        Map<Integer, License> licenseMap = licenses.stream().collect(Collectors.toMap(License::id, Function.identity()));

        List<Image> images = new ArrayList<>();
        for (CaptionDataSource.Image image : cocoDataset.captionDataSource().images()) {
            List<CaptionDataSource.Annotation> annotations = annotationMap.get(image.id());
            List<InstanceDataSource.Annotation> instanceAnnotations = instanceAnnotationMap.get(image.id());
            List<String> captions = annotations.stream().map(CaptionDataSource.Annotation::caption).toList();

            List<ImageObject> objects;
            if (instanceAnnotations != null) {
                objects = instanceAnnotations.stream()
                        .map(annotation -> {
                            InstanceDataSource.Category category = categoryMap.get(annotation.category_id());
                            return new ImageObject(
                                    category.id(),
                                    category.supercategory(),
                                    category.name(),
                                    annotation.bbox()
                            );
                        }).toList();
            } else {
                objects = null;
            }

            Date dateCaptured = parseDateFromSourceImage(image);

            List<String> accessory = categoriesForSuperCategory(objects, "accessory");
            List<String> animal = categoriesForSuperCategory(objects, "animal");
            List<String> appliance = categoriesForSuperCategory(objects, "appliance");
            List<String> electronic = categoriesForSuperCategory(objects, "electronic");
            List<String> food = categoriesForSuperCategory(objects, "food");
            List<String> furniture = categoriesForSuperCategory(objects, "furniture");
            List<String> indoor = categoriesForSuperCategory(objects, "indoor");
            List<String> kitchen = categoriesForSuperCategory(objects, "kitchen");
            List<String> outdoor = categoriesForSuperCategory(objects, "outdoor");
            List<String> person = categoriesForSuperCategory(objects, "person");
            List<String> sports = categoriesForSuperCategory(objects, "sports");
            List<String> vehicle = categoriesForSuperCategory(objects, "vehicle");

            License license = licenseMap.get(image.license());
            String caption = getCaption(captions);
            LMStudioEmbedding.EmbeddingResult embedding = includeVectorEmbeddings
                    ? LMStudioEmbedding.embed(caption)
                    : null;

            Image imageInfo = new Image(
                    image.id(),
                    caption,
                    embedding == null ? null : embedding.embedding(),
                    embedding == null ? null : embedding.model(),
                    image.coco_url(),
                    image.height(),
                    image.width(),
                    dateCaptured,
                    license.name(),
                    license.url(),
                    person != null && !person.isEmpty(),
                    accessory,
                    animal,
                    appliance,
                    electronic,
                    food,
                    furniture,
                    indoor,
                    kitchen,
                    outdoor,
                    sports,
                    vehicle
            );
            images.add(imageInfo);
        }
        return images;
    }

    private static final Pattern dateRegex = Pattern.compile(
            "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})\\s+(?<hour>\\d{2}):(?<minute>\\d{2}):(?<second>\\d{2})"
    );
    /**
     * Convert date in 'YYYY-MM-DD HH:MM:SS' format e.g. '2013-11-14 11:18:45' to a Date object.
     */
    private static Date parseDateFromSourceImage(CaptionDataSource.Image image) {
        Date dateCaptured;
        Matcher matcher = dateRegex.matcher(image.date_captured());
        if (matcher.find()) {
            String year = matcher.group("year");
            String month = matcher.group("month");
            String day = matcher.group("day");
            String hour = matcher.group("hour");
            String minute = matcher.group("minute");
            String second = matcher.group("second");

            dateCaptured = Date.from(Instant.parse("%s-%s-%sT%s:%s:%s.000Z".formatted(year, month, day, hour, minute, second)));
        } else {
            dateCaptured = null;
        }
        return dateCaptured;
    }

    private static List<Category> getCategories(CocoDataset cocoDataset) {
        List<Category> categories = Arrays.stream(cocoDataset.instanceDataSource().categories())
                .map(c -> new Category(c.id(), c.supercategory(), c.name()))
                .toList();
        return categories;
    }

    private static List<License> getLicenses(CocoDataset cocoDataset) {
        List<License> licenses = Arrays.stream(cocoDataset.captionDataSource().licenses())
                .map(l -> new License(l.id(), l.name(), l.url()))
                .toList();
        return licenses;
    }

    private static CocoDataset downloadCocoDataset() {
        try {
            log.info("Downloading COCO dataset annotations...");
            URL url = new URI("http://images.cocodataset.org/annotations/annotations_trainval2017.zip").toURL();
            try (
                    InputStream inputStream = url.openStream();
                    ZipInputStream zipInputStream = new ZipInputStream(inputStream)
                ) {
                ZipEntry entry;
                CaptionDataSource captionDataSource = null;
                InstanceDataSource instanceDataSource = null;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().equals("annotations/captions_train2017.json")) {
                        captionDataSource = readFromStreamWithoutClosing(zipInputStream, CaptionDataSource.class);
                    }
                    if (entry.getName().equals("annotations/instances_train2017.json")) {
                        instanceDataSource = readFromStreamWithoutClosing(zipInputStream, InstanceDataSource.class);
                    }
                }
                if (captionDataSource == null) {
                    throw new IOException("Could not find captions_train2017.json in the zip file");
                }
                if (instanceDataSource == null) {
                    throw new IOException("Could not find instances_train2017.json in the zip file");
                }
                CocoDataset cocoDataset = new CocoDataset(captionDataSource, instanceDataSource);
                log.info("Downloaded COCO dataset annotations.");
                return cocoDataset;
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to download coco.", e);
        }
    }

    private record CocoDataset(CaptionDataSource captionDataSource, InstanceDataSource instanceDataSource) {
    }

    private static String getCaption(List<String> captions) {
        if (captions == null || captions.isEmpty()) {
            return null;
        }
        String caption = captions.getLast();
        caption = caption.trim();
        caption = caption.substring(0, 1).toUpperCase() + caption.substring(1).toLowerCase();
        if (!caption.endsWith(".")) {
            caption += ".";
        }
        return caption;
    }

    private static List<String> categoriesForSuperCategory(List<ImageObject> objects, String superCategory) {
        if (objects == null) {
            return null;
        }
        List<String> categories = objects.stream()
                .filter(o -> o.superCategory().equals(superCategory))
                .map(ImageObject::category)
                .distinct()
                .toList();
        if (categories.isEmpty()) {
            return null;
        }
        return categories;
    }
}

package com.mycodefu.datapreparation;

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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
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

    public static void main(String[] args) throws IOException {
        CocoDataset cocoDataset = downloadCocoDataset();

        List<Category> categories = getCategories(cocoDataset);
        List<Image> images = getImages(cocoDataset);

        writeToLocalMongoDB(categories, images);

        buildAtlasIndex();

        // uncomment to refresh the data in the test resources
//         writeSampleData(images, categories);
    }

    private static void writeToLocalMongoDB(List<Category> categories, List<Image> images) throws IOException {
        CategoryDataAccess categoryDataAccess = CategoryDataAccess.getInstance();
        categoryDataAccess.insertBulk(categories);

        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        imageDataAccess.insertBulk(images);
    }

    private static void buildAtlasIndex() throws IOException {
        URL resource = PrepareDataEntryPoint.class.getResource("/atlas-search-index.json");
        Path path = Path.of(Objects.requireNonNull(resource).getPath());
        String indexResource = Files.readString(path);

        MongoConnection.createAtlasIndex(
            MongoConnection.database_name,
            CategoryDataAccess.collection_name,
            MongoConnection.index_name,
            BsonDocument.parse(indexResource)
        );
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

    private static List<Image> getImages(CocoDataset cocoDataset) {
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

            Image imageInfo = new Image(
                    image.id(),
                    getCaption(captions),
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
            URL url = new URL("http://images.cocodataset.org/annotations/annotations_trainval2017.zip");
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
        } catch (IOException e) {
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
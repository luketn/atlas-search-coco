package com.mycodefu.datapreparation;

import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.model.License;
import com.mycodefu.datapreparation.source.CaptionDataSource;
import com.mycodefu.datapreparation.source.ImageObject;
import com.mycodefu.datapreparation.source.InstanceDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.mycodefu.datapreparation.util.JsonUtil.readFromStreamWithoutClosing;
import static com.mycodefu.datapreparation.util.JsonUtil.writeToFile;

/**
 * This class reads the COCO dataset annotations from a zip file and writes the data to JSON files.
 */
public class PrepareDataEntryPoint {

    public static void main(String[] args) {
        CocoDataset cocoDataset = downloadCocoDataset();

        List<License> licenses = getLicenses(cocoDataset);
        List<Category> categories = getCategories(cocoDataset);
        List<Image> images = getImages(cocoDataset);

        //todo: write to the local mongodb, then create the atlas index using a utility

        // uncomment to refresh the data in the test resources
        // writeSampleData(images, licenses, categories);
    }

    private static void writeSampleData(List<Image> images, List<License> licenses, List<Category> categories) {
        writeSampleData("image.json", getTenOfEachAnimal(images));
        writeSampleData("license.json", licenses);
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
                .filter(image -> image.animal().length > 0)
                .filter(image -> {
                    String animal = image.animal()[0];
                    animalCategoryCounts.put(animal, animalCategoryCounts.getOrDefault(animal, 0) + 1);
                    return animalCategoryCounts.get(animal) <= 10;
                })
                .toList());

        //sort by animal string and then date captured
        animalImageInfos
                .sort(Comparator.comparing((Image i) -> i.animal().length > 0 ? i.animal()[0] : "")
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

            String[] accessory = categoriesForSuperCategory(objects, "accessory");
            String[] animal = categoriesForSuperCategory(objects, "animal");
            String[] appliance = categoriesForSuperCategory(objects, "appliance");
            String[] electronic = categoriesForSuperCategory(objects, "electronic");
            String[] food = categoriesForSuperCategory(objects, "food");
            String[] furniture = categoriesForSuperCategory(objects, "furniture");
            String[] indoor = categoriesForSuperCategory(objects, "indoor");
            String[] kitchen = categoriesForSuperCategory(objects, "kitchen");
            String[] outdoor = categoriesForSuperCategory(objects, "outdoor");
            String[] person = categoriesForSuperCategory(objects, "person");
            String[] sports = categoriesForSuperCategory(objects, "sports");
            String[] vehicle = categoriesForSuperCategory(objects, "vehicle");

            Image imageInfo = new Image(
                    image.id(),
                    getCaption(captions),
                    image.coco_url(),
                    image.height(),
                    image.width(),
                    dateCaptured,
                    image.license(),
                    person != null && person.length > 0,
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

    private static String[] categoriesForSuperCategory(List<ImageObject> objects, String superCategory) {
        if (objects == null) {
            return null;
        }
        String[] categories = objects.stream()
                .filter(o -> o.superCategory().equals(superCategory))
                .map(ImageObject::category)
                .distinct()
                .toArray(String[]::new);
        if (categories.length == 0) {
            return null;
        }
        return categories;
    }
}
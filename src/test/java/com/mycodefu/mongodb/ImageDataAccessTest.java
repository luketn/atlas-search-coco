package com.mycodefu.mongodb;

import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;


public class ImageDataAccessTest extends AtlasDataTest {

    @Test
    public void get() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        Image exampleImage = imageDataAccess.get(79047);
        assertNotNull(exampleImage);

        assertEquals(79047, exampleImage._id());
        assertEquals("Snow surrounds a standing bear statue on a sidewalk.", exampleImage.caption());
        assertEquals("http://images.cocodataset.org/train2017/000000079047.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384775627000L), exampleImage.dateCaptured());
        assertEquals("Attribution-NonCommercial-ShareAlike License", exampleImage.licenseName());
        assertEquals("http://creativecommons.org/licenses/by-nc-sa/2.0/", exampleImage.licenseUrl());
        assertFalse(exampleImage.hasPerson());
        assertEquals(1, exampleImage.animal().size());
        assertEquals("bear", exampleImage.animal().getFirst());
        assertEquals(1, exampleImage.outdoor().size());
        assertEquals("bench", exampleImage.outdoor().getFirst());
        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.furniture());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.kitchen());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());

    }

    @Test
    public void search_bear() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search("statue", 0, null, List.of("bear"), null, null, null, null, null, null, null, null, null);
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().get(0);
        assertEquals(79047, exampleImage._id());
        assertEquals("Snow surrounds a standing bear statue on a sidewalk.", exampleImage.caption());
        assertEquals("http://images.cocodataset.org/train2017/000000079047.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384775627000L), exampleImage.dateCaptured());
        assertEquals("Attribution-NonCommercial-ShareAlike License", exampleImage.licenseName());
        assertEquals("http://creativecommons.org/licenses/by-nc-sa/2.0/", exampleImage.licenseUrl());
        assertFalse(exampleImage.hasPerson());
        assertEquals(1, exampleImage.animal().size());
        assertEquals("bear", exampleImage.animal().getFirst());
        assertEquals(1, exampleImage.outdoor().size());
        assertEquals("bench", exampleImage.outdoor().getFirst());
        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.furniture());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.kitchen());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());
    }

    @Test
    public void search_bird() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(
                "bread basket", 0,
                true,
                List.of("bird"),
                null, null, null,
                List.of("chair", "dining table"),
                null,
                List.of("cup"),
                null, null, null
        );
        assertNotNull(searchResult);
        assertEquals(1, searchResult.docs().size());
        Image exampleImage = searchResult.docs().get(0);
        assertEquals(527040, exampleImage._id());
        assertEquals("Three birds sitting on a bread basket near a newspaper.", exampleImage.caption());
        assertEquals("http://images.cocodataset.org/train2017/000000527040.jpg", exampleImage.url());
        assertEquals(640, exampleImage.height());
        assertEquals(480, exampleImage.width());
        assertEquals(new Date(1384546777000L), exampleImage.dateCaptured());
        assertEquals("Attribution-NonCommercial License", exampleImage.licenseName());
        assertEquals("http://creativecommons.org/licenses/by-nc/2.0/", exampleImage.licenseUrl());

        assertTrue(exampleImage.hasPerson());

        assertEquals(1, exampleImage.animal().size());
        assertEquals("bird", exampleImage.animal().getFirst());

        assertEquals(2, exampleImage.furniture().size());
        assertEquals("chair", exampleImage.furniture().getFirst());
        assertEquals("dining table", exampleImage.furniture().getLast());

        assertEquals(2, exampleImage.kitchen().size());
        assertEquals("cup", exampleImage.kitchen().getFirst());
        assertEquals("bowl", exampleImage.kitchen().getLast());

        assertNull(exampleImage.accessory());
        assertNull(exampleImage.appliance());
        assertNull(exampleImage.electronic());
        assertNull(exampleImage.food());
        assertNull(exampleImage.indoor());
        assertNull(exampleImage.sports());
        assertNull(exampleImage.vehicle());
    }

    @Test
    public void search_cat_pages() {
        ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
        ImageSearchResult searchResult = imageDataAccess.search(
                "cat",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResult);
        assertEquals(5, searchResult.docs().size());

        assertEquals(13, searchResult.meta().getFirst().count().total());

        ImageSearchResult searchResultPage2 = imageDataAccess.search(
                "cat",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResultPage2);
        assertEquals(5, searchResultPage2.docs().size());

        ImageSearchResult searchResultPage3 = imageDataAccess.search(
                "cat",
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(searchResultPage3);
        assertEquals(3, searchResultPage3.docs().size());
    }
}
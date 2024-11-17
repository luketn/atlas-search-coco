package com.mycodefu.mongodb;

import com.mycodefu.model.Image;
import org.junit.Test;

import java.util.Date;

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
}
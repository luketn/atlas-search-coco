package com.mycodefu.model;

import java.util.Date;
import java.util.List;

public record Image(
        int _id,
        //The caption describes the contents of the image and can be searched on using full text search.
        String caption,
        String url,
        int height,
        int width,
        Date dateCaptured,
        String licenseName,
        String licenseUrl,
        //True if the image shows a person.
        boolean hasPerson,
        //Following fields are 'super categories'.
        // Each item in the list is a category.
        // One or more objects of each category listed is present in the image.
        List<String> accessory,
        List<String> animal,
        List<String> appliance,
        List<String> electronic,
        List<String> food,
        List<String> furniture,
        List<String> indoor,
        List<String> kitchen,
        List<String> outdoor,
        List<String> sports,
        List<String> vehicle
) { }
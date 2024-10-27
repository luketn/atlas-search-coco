package com.mycodefu.model;

import java.util.Date;
import java.util.List;

public record Image(
        int id,
        String caption,
        String url,
        int height,
        int width,
        Date dateCaptured,
        String licenseName,
        String licenseUrl,
        boolean hasPerson,
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
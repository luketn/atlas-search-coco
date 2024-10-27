package com.mycodefu.model;

import java.util.Date;

public record Image(
        int id,
        String caption,
        String url,
        int height,
        int width,
        Date dateCaptured,
        int license,
        boolean hasPerson,
        String[] accessory,
        String[] animal,
        String[] appliance,
        String[] electronic,
        String[] food,
        String[] furniture,
        String[] indoor,
        String[] kitchen,
        String[] outdoor,
        String[] sports,
        String[] vehicle
) { }
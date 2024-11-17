package com.mycodefu.datapreparation.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class JsonUtil {
    private static final ObjectMapper jacksonWriter = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper jacksonReaderNoAutoClose = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    private static final ObjectMapper jacksonReader = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void writeToFile(File file, Object object) {
        try {
            jacksonWriter.writeValue(file, object);
        } catch (Exception e) {
            throw new RuntimeException("Error writing object to file %s".formatted(file.getName()), e);
        }
    }
    public static String writeToString(Object object) {
        try {
            return jacksonWriter.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Error writing object to string.", e);
        }
    }
    public static <T> T readFromStreamWithoutClosing(InputStream inputStream, Class<T> clazz) {
        try {
            return jacksonReaderNoAutoClose.readValue(inputStream, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading object from stream", e);
        }
    }
    public static <T> T readFile(File file, Class<T> clazz) {
        try {
            return jacksonReader.readValue(file, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading object from stream", e);
        }
    }
    public static <T> T readUrl(URL url, Class<T> clazz) {
        try {
            return jacksonReader.readValue(url, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading object from stream", e);
        }
    }
    public static <T> T readStream(InputStream stream, Class<T> clazz) {
        try {
            return jacksonReader.readValue(stream, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading object from stream", e);
        }
    }

}

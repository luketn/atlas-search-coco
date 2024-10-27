package com.mycodefu.datapreparation.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;

public class JsonUtil {
    private static final ObjectMapper jacksonWriter = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper jacksonReaderNoAutoClose = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    public static void writeToFile(File file, Object object) {
        try {
            jacksonWriter.writeValue(file, object);
        } catch (Exception e) {
            throw new RuntimeException("Error writing object to file %s".formatted(file.getName()), e);
        }
    }
    public static <T> T readFromStreamWithoutClosing(InputStream inputStream, Class<T> clazz) {
        try {
            return jacksonReaderNoAutoClose.readValue(inputStream, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading object from stream", e);
        }
    }

}

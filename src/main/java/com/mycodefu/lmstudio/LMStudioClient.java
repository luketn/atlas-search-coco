package com.mycodefu.lmstudio;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class LMStudioClient {
    static final String DEFAULT_BASE_URL = "http://127.0.0.1:1234";
    static final ObjectMapper objectMapper = new ObjectMapper();

    private LMStudioClient() {
    }

    static String baseUrl() {
        String configuredBaseUrl = System.getenv("LM_STUDIO_BASE_URL");
        String baseUrl = configuredBaseUrl == null || configuredBaseUrl.isBlank()
                ? DEFAULT_BASE_URL
                : configuredBaseUrl;
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    static <T> T postJson(String path, Object request, Class<T> responseType) throws IOException {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(baseUrl() + path);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            byte[] requestBytes = objectMapper.writeValueAsBytes(request);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("LM Studio request to %s failed with status %d: %s"
                        .formatted(path, statusCode, readErrorBody(connection)));
            }

            try (InputStream inputStream = connection.getInputStream()) {
                if (responseType == Void.class) {
                    return null;
                }
                return objectMapper.readValue(inputStream, responseType);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readErrorBody(HttpURLConnection connection) throws IOException {
        try (InputStream errorStream = connection.getErrorStream()) {
            return errorStream == null
                    ? ""
                    : new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

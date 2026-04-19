package com.mycodefu.lmstudio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class LMStudioEmbedding {
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:1234";
    public static final String DEFAULT_MODEL = "text-embedding-nomic-embed-text-v1.5";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Function<String, EmbeddingResult> embeddingProvider = LMStudioEmbedding::embedViaHttp;

    private LMStudioEmbedding() {
    }

    public static EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed must not be blank.");
        }
        return Objects.requireNonNull(embeddingProvider.apply(text), "Embedding provider returned null");
    }

    public static void setEmbeddingProviderForTests(Function<String, EmbeddingResult> provider) {
        embeddingProvider = provider == null ? LMStudioEmbedding::embedViaHttp : provider;
    }

    private static EmbeddingResult embedViaHttp(String text) {
        String baseUrl = System.getenv("LM_STUDIO_BASE_URL") != null
                ? System.getenv("LM_STUDIO_BASE_URL")
                : DEFAULT_BASE_URL;
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(baseUrl + "/v1/embeddings");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            EmbeddingRequest request = new EmbeddingRequest(DEFAULT_MODEL, text);
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                try (InputStream errorStream = connection.getErrorStream()) {
                    String errorBody = errorStream == null
                            ? ""
                            : new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    throw new IOException("LM Studio embedding request failed with status %d: %s"
                            .formatted(statusCode, errorBody));
                }
            }

            try (InputStream inputStream = connection.getInputStream()) {
                EmbeddingResponse response = objectMapper.readValue(inputStream, EmbeddingResponse.class);
                if (response.data() == null || response.data().isEmpty()) {
                    throw new IOException("LM Studio embedding response did not contain embedding data.");
                }
                return new EmbeddingResult(response.data().getFirst().embedding(), response.model());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve embedding from LM Studio.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public record EmbeddingResult(List<Double> embedding, String model) {
    }

    private record EmbeddingRequest(String model, String input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(String model, List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(@JsonProperty("embedding") List<Double> embedding) {
    }
}

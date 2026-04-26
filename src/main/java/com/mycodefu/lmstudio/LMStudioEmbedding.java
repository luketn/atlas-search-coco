package com.mycodefu.lmstudio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class LMStudioEmbedding {
    public static final String DEFAULT_MODEL = "text-embedding-nomic-embed-text-v1.5";

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
        try {
            EmbeddingRequest request = new EmbeddingRequest(DEFAULT_MODEL, text);
            EmbeddingResponse response = LMStudioClient.postJson("/v1/embeddings", request, EmbeddingResponse.class);
            if (response.data() == null || response.data().isEmpty()) {
                throw new IOException("LM Studio embedding response did not contain embedding data.");
            }
            return new EmbeddingResult(response.data().getFirst().embedding(), response.model());
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve embedding from LM Studio.", e);
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

package com.mycodefu.lmstudio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LMStudioVisionModel {
    public static final String DEFAULT_MODEL = "mlx-community/gemma-4-e4b-it-8bit";

    private static final Logger log = LoggerFactory.getLogger(LMStudioVisionModel.class);
    private static final AtomicBoolean modelLoadAttempted = new AtomicBoolean(false);

    private LMStudioVisionModel() {
    }

    public static VisionResult caption(ImageInput imageInput, Map<String, List<String>> allowedLabelsByType) {
        Objects.requireNonNull(imageInput, "imageInput must not be null");
        Objects.requireNonNull(allowedLabelsByType, "allowedLabelsByType must not be null");
        try {
            String imageUrl = imageInput.toImageUrl();
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new IllegalArgumentException("Provide either imageUrl or imageBase64.");
            }

            ensureModelLoadAttempted();

            ChatCompletionRequest request = new ChatCompletionRequest(
                    DEFAULT_MODEL,
                    List.of(new ChatMessage("user", List.of(
                            Map.of("type", "text", "text", """
                                    Inspect this image and return structured JSON only.
                                    Create a concise caption, decide whether a person is visible, and choose every visible
                                    label that applies. Labels must only come from the schema enums and the allowed list below.
                                    Prefer specific supported object labels over generic descriptions. If a visible object is
                                    a seat, chair, or stool, use {"type":"furniture","label":"chair"}. If a visible object is
                                    a table used for eating, meetings, or events, use {"type":"furniture","label":"dining table"}.
                                    Include all visible supported furniture, indoor, kitchen, food, electronic, outdoor, sports,
                                    animal, appliance, and vehicle labels. Return an empty labels array only when no supported
                                    label is visible.

                                    Allowed labels by type:
                                    %s
                                    """.formatted(allowedLabelsText(allowedLabelsByType))),
                            Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                    ))),
                    responseFormat(allowedLabelsByType),
                    0.1
            );

            ChatCompletionResponse response;
            response = LMStudioClient.postJson("/v1/chat/completions", request, ChatCompletionResponse.class);
            StructuredImageMetadata metadata = extractStructuredMetadata(response);
            Map<String, List<String>> categoryLabels = toCategoryLabels(metadata.labels(), allowedLabelsByType);
            List<String> labels = categoryLabels.values().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
            LMStudioEmbedding.EmbeddingResult embedding = LMStudioEmbedding.embed(metadata.caption());
            return new VisionResult(
                    metadata.caption(),
                    embedding.embedding(),
                    embedding.model(),
                    DEFAULT_MODEL,
                    metadata.hasPerson(),
                    labels,
                    metadata.labels() == null ? List.of() : metadata.labels(),
                    categoryLabels
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve image metadata from LM Studio.", e);
        }
    }

    private static void ensureModelLoadAttempted() {
        if (!modelLoadAttempted.compareAndSet(false, true)) {
            return;
        }

        try {
            LMStudioClient.postJson(
                    "/api/v1/models/load",
                    Map.of("model", DEFAULT_MODEL, "echo_load_config", false),
                    LoadModelResponse.class
            );
        } catch (IOException e) {
            log.warn("Unable to load LM Studio vision model through /api/v1/models/load. " +
                    "Continuing in case the model is already loaded.", e);
        }
    }

    private static Map<String, Object> responseFormat(Map<String, List<String>> allowedLabelsByType) {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "image_metadata",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "caption", Map.of(
                                                "type", "string",
                                                "description", "One concise natural-language caption for the image."
                                        ),
                                        "hasPerson", Map.of(
                                                "type", "boolean",
                                                "description", "True when a person is visible in the image."
                                        ),
                                        "labels", Map.of(
                                                "type", "array",
                                                "description", "Typed labels selected only from the allowed enums.",
                                                "items", typedLabelSchema(allowedLabelsByType)
                                        )
                                ),
                                "required", List.of("caption", "hasPerson", "labels"),
                                "additionalProperties", false
                        )
                )
        );
    }

    private static Map<String, Object> typedLabelSchema(Map<String, List<String>> allowedLabelsByType) {
        List<Map<String, Object>> variants = allowedLabelsByType.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> Map.<String, Object>of(
                        "type", "object",
                        "properties", Map.of(
                                "type", Map.of("enum", List.of(entry.getKey())),
                                "label", Map.of("enum", entry.getValue())
                        ),
                        "required", List.of("type", "label"),
                        "additionalProperties", false
                ))
                .toList();
        return Map.of("oneOf", variants);
    }

    private static String allowedLabelsText(Map<String, List<String>> allowedLabelsByType) {
        return allowedLabelsByType.entrySet().stream()
                .map(entry -> "%s: %s".formatted(entry.getKey(), String.join(", ", entry.getValue())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(none)");
    }

    private static StructuredImageMetadata extractStructuredMetadata(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("LM Studio vision response did not contain choices.");
        }
        ChatMessageResponse message = response.choices().getFirst().message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new RuntimeException("LM Studio vision response did not contain structured JSON content.");
        }

        try {
            StructuredImageMetadata metadata = LMStudioClient.objectMapper.readValue(
                    message.content(),
                    StructuredImageMetadata.class
            );
            if (metadata.caption() == null || metadata.caption().isBlank()) {
                throw new RuntimeException("LM Studio vision response did not include a caption.");
            }
            return metadata;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse LM Studio structured vision response.", e);
        }
    }

    private static Map<String, List<String>> toCategoryLabels(
            List<TypedLabel> typedLabels,
            Map<String, List<String>> allowedLabelsByType
    ) {
        if (typedLabels == null || typedLabels.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> categoryLabels = new LinkedHashMap<>();
        for (TypedLabel typedLabel : typedLabels) {
            if (typedLabel == null || typedLabel.type() == null || typedLabel.label() == null) {
                continue;
            }
            List<String> allowedLabels = allowedLabelsByType.get(typedLabel.type());
            if (allowedLabels == null || !allowedLabels.contains(typedLabel.label())) {
                continue;
            }
            categoryLabels.merge(
                    typedLabel.type(),
                    List.of(typedLabel.label()),
                    (existing, added) -> {
                        if (existing.contains(typedLabel.label())) {
                            return existing;
                        }
                        return java.util.stream.Stream.concat(existing.stream(), added.stream()).toList();
                    }
            );
        }
        return categoryLabels;
    }

    public record ImageInput(String imageUrl, String imageBase64, String mimeType) {
        private String toImageUrl() throws IOException {
            if (imageUrl != null && !imageUrl.isBlank()) {
                if (imageUrl.startsWith("data:")) {
                    return imageUrl;
                }
                return fetchImageUrlAsDataUrl(imageUrl);
            }
            if (imageBase64 == null || imageBase64.isBlank()) {
                return null;
            }
            String type = mimeType == null || mimeType.isBlank() ? "image/jpeg" : mimeType;
            return "data:%s;base64,%s".formatted(type, imageBase64);
        }

        private static String fetchImageUrlAsDataUrl(String rawImageUrl) throws IOException {
            URI uri = URI.create(rawImageUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Image URL must use http or https.");
            }

            URLConnection connection = uri.toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                contentType = "image/jpeg";
            }

            try (InputStream inputStream = connection.getInputStream()) {
                String base64 = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
                return "data:%s;base64,%s".formatted(contentType, base64);
            }
        }
    }

    public record VisionResult(
            String caption,
            List<Double> captionEmbedding,
            String captionEmbeddingModel,
            String visionModel,
            boolean hasPerson,
            List<String> labels,
            List<TypedLabel> typedLabels,
            Map<String, List<String>> categoryLabels
    ) {
    }

    public record TypedLabel(String type, String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LoadModelResponse(String status) {
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("response_format") Map<String, Object> responseFormat,
            double temperature
    ) {
    }

    private record ChatMessage(String role, Object content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<ChatChoice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChoice(ChatMessageResponse message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessageResponse(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StructuredImageMetadata(
            String caption,
            boolean hasPerson,
            List<TypedLabel> labels
    ) {
    }
}

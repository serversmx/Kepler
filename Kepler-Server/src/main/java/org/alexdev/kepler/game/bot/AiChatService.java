package org.alexdev.kepler.game.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AiChatService {
    private static volatile AiChatService instance;
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final HttpClient httpClient;
    private final ExecutorService executor;

    private AiChatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = Executors.newFixedThreadPool(2);
    }

    public void generateResponse(Bot bot, String playerName, String message, Consumer<String> callback) {
        this.executor.submit(() -> {
            try {
                GameConfiguration config = GameConfiguration.getInstance();
                boolean anthropic = "anthropic".equalsIgnoreCase(config.getString("ai.chat.provider", "openai").trim());

                // OpenAI keeps the legacy keys (ai.chat.api_key / ai.chat.model) so existing installs keep working.
                String apiKey = anthropic ? config.getString("ai.chat.anthropic_api_key", "") : config.getString("ai.chat.api_key", "");
                if (apiKey == null || apiKey.isEmpty()) {
                    callback.accept(null);
                    return;
                }

                String model = anthropic ? config.getString("ai.chat.anthropic_model", "claude-haiku-5-5") : config.getString("ai.chat.model", "gpt-4o-mini");
                int maxTokens = config.getInteger("ai.chat.max_tokens");

                String systemPrompt = bot.getBotData().getAiSystemPrompt();
                if (systemPrompt == null || systemPrompt.isEmpty()) {
                    systemPrompt = "Eres " + bot.getDetails().getName() + ", un personaje en Habbo Hotel. "
                            + "Responde en español, máximo 2 frases cortas y amigables. "
                            + "No uses markdown ni emojis especiales.";
                }

                HttpRequest request = anthropic
                        ? buildAnthropicRequest(apiKey, model, systemPrompt, playerName, message, maxTokens)
                        : buildOpenAiRequest(apiKey, model, systemPrompt, playerName, message, maxTokens);

                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String aiText = anthropic ? parseAnthropicResponse(response.body()) : parseResponse(response.body());
                    callback.accept(aiText);
                } else {
                    log.warn("AI chat API ({}) returned status {}: {}", anthropic ? "anthropic" : "openai", response.statusCode(), response.body());
                    callback.accept(null);
                }
            } catch (Exception e) {
                log.warn("AI chat request failed: {}", e.getMessage());
                callback.accept(null);
            }
        });
    }

    private HttpRequest buildOpenAiRequest(String apiKey, String model, String systemPrompt, String playerName, String message, int maxTokens) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestJson(model, systemPrompt, playerName, message, maxTokens)))
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpRequest buildAnthropicRequest(String apiKey, String model, String systemPrompt, String playerName, String message, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", maxTokens);
        root.addProperty("temperature", 0.8);
        root.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", playerName + ": " + message);
        messages.add(userMsg);
        root.add("messages", messages);

        return HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .timeout(Duration.ofSeconds(8))
                .build();
    }

    private String parseAnthropicResponse(String jsonBody) {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content != null) {
                for (int i = 0; i < content.size(); i++) {
                    JsonObject block = content.get(i).getAsJsonObject();
                    if (block.has("type") && "text".equals(block.get("type").getAsString())) {
                        return block.get("text").getAsString().trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Anthropic response: {}", e.getMessage());
        }
        return null;
    }

    private String buildRequestJson(String model, String systemPrompt, String playerName, String message, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", maxTokens);
        root.addProperty("temperature", 0.8);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", playerName + ": " + message);
        messages.add(userMsg);

        root.add("messages", messages);
        return root.toString();
    }

    private String parseResponse(String jsonBody) {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
        }
        return null;
    }

    public static AiChatService getInstance() {
        if (instance == null) {
            synchronized (AiChatService.class) {
                if (instance == null) {
                    instance = new AiChatService();
                }
            }
        }
        return instance;
    }
}

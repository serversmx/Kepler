package org.alexdev.kepler.game.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.alexdev.kepler.game.texts.TextsManager;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiChatService {
    private static volatile AiChatService instance;
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** Habbo chat bubbles cut text around 100 characters, so never speak more than this. */
    private static final int MAX_SPEECH_CHARS = 100;
    private static final Pattern ITEM_TAG = Pattern.compile("\\[\\s*ITEM\\s*:\\s*([^\\]]+?)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_TEXT = Pattern.compile("\\*[^*]*\\*");

    /** What the bot should say and, optionally, the drink/food to hand to the player. */
    public static class AiReply {
        public final String text;
        public final String item;

        AiReply(String text, String item) {
            this.text = text;
            this.item = item;
        }
    }

    private final HttpClient httpClient;
    private final ExecutorService executor;

    private AiChatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = Executors.newFixedThreadPool(2);
    }

    public void generateResponse(Bot bot, String playerName, String message, Consumer<AiReply> callback) {
        this.executor.submit(() -> {
            try {
                GameConfiguration config = GameConfiguration.getInstance();
                boolean anthropic = "anthropic".equalsIgnoreCase(config.getString("ai.chat.provider", "openai").trim());

                // OpenAI keeps the legacy keys (ai.chat.api_key / ai.chat.model) so existing installs keep working.
                String apiKey = anthropic ? config.getString("ai.chat.anthropic_api_key", "") : config.getString("ai.chat.api_key", "");
                if (apiKey == null || apiKey.isEmpty()) {
                    log.warn("AI chat: no API key configured for provider {}", anthropic ? "anthropic" : "openai");
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

                List<String> menu = getMenu(bot);
                systemPrompt = systemPrompt + buildRules(menu);

                HttpRequest request = anthropic
                        ? buildAnthropicRequest(apiKey, model, systemPrompt, playerName, message, maxTokens)
                        : buildOpenAiRequest(apiKey, model, systemPrompt, playerName, message, maxTokens);

                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String aiText = anthropic ? parseAnthropicResponse(response.body()) : parseResponse(response.body());
                    AiReply reply = aiText == null ? null : toReply(aiText, menu);
                    log.info("AI chat [{}/{}] {} -> raw='{}' text='{}' item={}", anthropic ? "anthropic" : "openai", model, bot.getDetails().getName(),
                            aiText, reply == null ? null : reply.text, reply == null ? null : reply.item);
                    callback.accept(reply);
                } else {
                    log.warn("AI chat API ({}) returned status {}: {}", anthropic ? "anthropic" : "openai", response.statusCode(), response.body());
                    callback.accept(null);
                }
            } catch (Throwable e) {
                log.warn("AI chat request failed", e);
                callback.accept(null);
            }
        });
    }

    /** Items the bot can hand out: its own hand_items, or the hotel's localised drink/food names. */
    private List<String> getMenu(Bot bot) {
        List<String> menu = new ArrayList<>();

        for (String drink : bot.getBotData().getDrinks()) {
            if (!drink.trim().isEmpty()) {
                menu.add(drink.trim());
            }
        }

        if (menu.isEmpty()) {
            for (int i = 1; i <= 25; i++) {
                if (i == 20) {
                    continue; // camera
                }

                String name = TextsManager.getInstance().getValue("handitem" + i);

                if (name != null && !name.trim().isEmpty() && !menu.contains(name.trim())) {
                    menu.add(name.trim());
                }
            }
        }

        return menu;
    }

    private String buildRules(List<String> menu) {
        StringBuilder rules = new StringBuilder();
        rules.append("\n\nReglas obligatorias: responde con UNA sola frase de maximo ")
                .append(MAX_SPEECH_CHARS - 10)
                .append(" caracteres, sin asteriscos, sin acciones entre asteriscos, sin markdown ni emojis.");

        if (!menu.isEmpty()) {
            rules.append(" Puedes dar estas bebidas o comidas: ").append(String.join(", ", menu)).append(".")
                    .append(" Si el jugador te pide algo de beber o comer (o simplemente 'una bebida'), elige UNA del menu")
                    .append(" (la que pidio si esta; si no, la que mejor encaje) y termina tu frase con la etiqueta [ITEM:nombre exacto del menu].")
                    .append(" Si no pide nada, NO escribas la etiqueta. Nunca ofrezcas nada fuera del menu.");
        }

        return rules.toString();
    }

    /** Parse the optional [ITEM:x] tag, strip stage directions and cut the text to chat length. */
    AiReply toReply(String raw, List<String> menu) {
        String item = null;
        Matcher matcher = ITEM_TAG.matcher(raw);

        while (matcher.find()) {
            for (String candidate : menu) {
                if (candidate.equalsIgnoreCase(matcher.group(1).trim())) {
                    item = candidate;
                    break;
                }
            }
        }

        String text = ITEM_TAG.matcher(raw).replaceAll("");
        text = ACTION_TEXT.matcher(text).replaceAll("").replace("*", "");
        text = text.replaceAll("\\s+", " ").trim();

        if (text.length() > MAX_SPEECH_CHARS) {
            String cut = text.substring(0, MAX_SPEECH_CHARS);
            int sentenceEnd = Math.max(cut.lastIndexOf('.'), Math.max(cut.lastIndexOf('!'), cut.lastIndexOf('?')));

            if (sentenceEnd > 20) {
                text = cut.substring(0, sentenceEnd + 1);
            } else {
                int space = cut.lastIndexOf(' ');
                text = (space > 20 ? cut.substring(0, space) : cut).trim();
            }
        }

        return new AiReply(text, item);
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
        // No "temperature": current Claude models reject it with a 400 (deprecated parameter).
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

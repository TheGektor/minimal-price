package ru.minimalprice.minimalprice.features.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import github.scarsz.discordsrv.DiscordSRV;

public class DiscordRestUtil {

    private final String botToken;
    private final HttpClient httpClient;
    private final Gson gson;
    private final JavaPlugin plugin;


    public DiscordRestUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        // Try getting token from JDA first, then config
        String token = DiscordSRV.getPlugin().getJda().getToken();
        if (token == null || token.isEmpty() || token.equalsIgnoreCase("BOT_TOKEN_HERE")) {
             token = DiscordSRV.getPlugin().getConfig().getString("BotToken");
        }
        
        // Sanitize token: remove "Bot " prefix if present (user might have pasted it in config)
        if (token != null && token.startsWith("Bot ")) {
            token = token.substring(4);
        }
        
        if (token != null && token.length() > 10) {
            String masked = token.substring(0, 5) + "..." + token.substring(token.length() - 5);
            plugin.getLogger().info("DiscordRestUtil initialized with token: " + masked);
        } else {
            plugin.getLogger().warning("DiscordRestUtil: Could not retrieve Valid Bot Token!");
        }
        
        this.botToken = token;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    private CompletableFuture<HttpResponse<String>> sendRequestWithRetry(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 429) {
                        // Rate limit hit
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        double retryAfter = json.has("retry_after") ? json.get("retry_after").getAsDouble() : 1.0;
                        long delayMs = (long) (retryAfter * 1000) + 100; // Add buffer
                        
                        plugin.getLogger().warning("Discord Rate Limit hit! Retrying in " + delayMs + "ms...");
                        
                        return CompletableFuture.runAsync(() -> {}, 
                                CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                                .thenCompose(v -> sendRequestWithRetry(request));
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    public CompletableFuture<ThreadResult> createForumPost(String channelId, String title, String content, JsonObject embed, JsonArray components) {
        String url = "https://discord.com/api/v10/channels/" + channelId + "/threads";

        JsonObject body = new JsonObject();
        body.addProperty("name", title);
        
        JsonObject message = new JsonObject();
        message.addProperty("content", content);
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            message.add("embeds", embeds);
        }
        if (components != null) {
            message.add("components", components);
            // IS_COMPONENTS_V2 flag = 1 << 15 = 32768
            message.addProperty("flags", 32768); 
        }
        
        body.add("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return sendRequestWithRetry(request)
                .thenApply(this::parseThreadCreationResponse);
    }

    public CompletableFuture<Void> deleteChannel(String channelId) {
        String url = "https://discord.com/api/v10/channels/" + channelId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .DELETE()
                .build();

        return sendRequestWithRetry(request)
                .thenAccept(response -> {
                    if (response.statusCode() != 204 && response.statusCode() != 200) {
                        plugin.getLogger().warning("Failed to delete channel " + channelId + ": " + response.body());
                    }
                });
    }
    
    public CompletableFuture<Void> updateMessage(String channelId, String messageId, JsonObject embed, JsonArray components) {
        String url = "https://discord.com/api/v10/channels/" + channelId + "/messages/" + messageId;

        JsonObject body = new JsonObject();
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            body.add("embeds", embeds);
        }
        if (components != null) {
            body.add("components", components);
            body.addProperty("flags", 32768); // IS_COMPONENTS_V2
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return sendRequestWithRetry(request)
                 .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        plugin.getLogger().warning("Failed to update message " + messageId + ": " + response.body());
                    }
                });
    }
    
    public CompletableFuture<Void> updateThreadName(String channelId, String newName) {
        String url = "https://discord.com/api/v10/channels/" + channelId;
        
        JsonObject body = new JsonObject();
        body.addProperty("name", newName);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
                
        return sendRequestWithRetry(request)
                 .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                         plugin.getLogger().warning("Failed to update thread name " + channelId + ": " + response.body());
                    }
                 });
    }

    private ThreadResult parseThreadCreationResponse(HttpResponse<String> response) {
        if (response.statusCode() != 201) {
            plugin.getLogger().warning("Error creating forum post: " + response.body());
            return null;
        }
        
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        String threadId = json.get("id").getAsString();
        
        // Discord API (forum thread creation) returns the thread channel object.
        // The starter message ID is stored in the nested "message" field (if present),
        // otherwise we fall back to the thread ID (they may match for starter messages).
        String messageId = threadId; // sensible default
        if (json.has("message") && json.get("message").isJsonObject()) {
            JsonObject msgObj = json.getAsJsonObject("message");
            if (msgObj.has("id") && !msgObj.get("id").isJsonNull()) {
                messageId = msgObj.get("id").getAsString();
            }
        }
        
        plugin.getLogger().info("Forum post created — threadId=" + threadId + " messageId=" + messageId);
        return new ThreadResult(threadId, messageId);
    }

    public static class ThreadResult {
        public final String threadId;
        public final String messageId;

        public ThreadResult(String threadId, String messageId) {
            this.threadId = threadId;
            this.messageId = messageId;
        }
    }
}

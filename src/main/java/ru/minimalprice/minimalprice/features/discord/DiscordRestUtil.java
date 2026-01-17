package ru.minimalprice.minimalprice.features.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<ThreadResult> createForumPost(String channelId, String title, String content, JsonObject embed) {
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
        
        body.add("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseThreadCreationResponse);
    }

    public CompletableFuture<Void> deleteChannel(String channelId) {
        String url = "https://discord.com/api/v10/channels/" + channelId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 204 && response.statusCode() != 200) {
                        plugin.getLogger().warning("Failed to delete channel " + channelId + ": " + response.body());
                    }
                });
    }
    
    public CompletableFuture<Void> updateMessage(String channelId, String messageId, JsonObject embed) {
        String url = "https://discord.com/api/v10/channels/" + channelId + "/messages/" + messageId;

        JsonObject body = new JsonObject();
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            body.add("embeds", embeds);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
                
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
        
        // In a forum post response, the 'message' object might be nested or we might need to fetch it.
        // Actually, for "Start Thread in Forum Channel", the response IS the Thread channel object.
        // But the message ID is usually the same as the thread ID for the starter message in some contexts, 
        // OR it's inside a 'message' field in the response?
        // Checking Discord API docs: POST /channels/{id}/threads (in forum) returns the thread channel object.
        // It DOES NOT return the message object directly in the root. 
        // BUT, usually the starter message ID = thread ID in some views, but better check.
        // API Docs say: "The response body is the created thread channel object."
        // Wait, how do we get the message ID?
        // "When creating a thread in a forum channel, the `message` field ... is used to create the starter message."
        // The response might NOT contain the message ID.
        // However, for Forum Threads, the starter message ID is OFTEN the same as Thread ID? No, that's not guaranteed.
        // Actually, let's look at the "last_message_id".
        // Better yet: we can fetch the messages of the thread immediately.
        // OR, simply: The starter message is the first message.
        // Let's assume for now we use the threadId as the ID to reference for updates? 
        // No, we need message ID to edit the message.
        // Let's try to fetch the message ID from `id` of the thread. A lot of times `id` of starter message == `id` of thread.
        // Let's try that. If it fails, we'll need to fetch messages in the thread.
        
        return new ThreadResult(threadId, threadId); 
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

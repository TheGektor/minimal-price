package ru.minimalprice.minimalprice.features.discord;

import com.google.gson.JsonObject;
import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.minimalprice.minimalprice.MinimalPrice;
import ru.minimalprice.minimalprice.features.discord.storage.DiscordRepository;
import ru.minimalprice.minimalprice.features.price.PriceManager;
import ru.minimalprice.minimalprice.features.price.events.CategoryCreateEvent;
import ru.minimalprice.minimalprice.features.price.events.CategoryRenameEvent;
import ru.minimalprice.minimalprice.features.price.events.ProductRenameEvent;
import ru.minimalprice.minimalprice.features.price.events.ProductUpdateEvent;
import ru.minimalprice.minimalprice.features.price.models.Product;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DiscordManager implements Listener {

    private final MinimalPrice plugin;
    private final DiscordRepository repository;
    private final PriceManager priceManager;
    private final String forumChannelId;
    private DiscordRestUtil restUtil;

    public DiscordManager(MinimalPrice plugin, PriceManager priceManager, String databasePath) {
        this.plugin = plugin;
        this.priceManager = priceManager;
        this.repository = new DiscordRepository(databasePath);
        this.forumChannelId = plugin.getConfig().getString("discord_forum_channel_id");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        initializeDiscordSync();
    }

    private void initializeDiscordSync() {
        plugin.getLogger().info("Waiting for DiscordSRV to be ready...");
        checkReadyAndInit();
    }
    
    private boolean initialized = false;

    private void checkReadyAndInit() {
         Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task -> {
             if (initialized) {
                 task.cancel();
                 return;
             }
             
             if (isDiscordReady()) {
                 plugin.getLogger().info("DiscordSRV is ready! Initializing DiscordRestUtil...");
                 restUtil = new DiscordRestUtil(plugin);
                 performStartupCleanup();
                 initialized = true;
                 task.cancel();
             } else {
                 // plugin.getLogger().info("DiscordSRV not ready yet...");
             }
         }, 100L, 60L);
    }

    private void performStartupCleanup() {
        try {
            plugin.getLogger().info("Starting Discord Forum cleanup...");
            
            // 1. Get all tracked threads
            Map<String, DiscordRepository.SyncData> allSync = repository.getAllSyncData();
            
            // 2. Delete existing threads
            for (Map.Entry<String, DiscordRepository.SyncData> entry : allSync.entrySet()) {
                String threadId = entry.getValue().threadId;
                
                // Using REST to delete channel (thread)
                restUtil.deleteChannel(threadId);
                
                repository.deleteSyncData(entry.getKey());
                
                // Rate limit prevention
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            
            // 3. Create new forum posts/threads
            for (ru.minimalprice.minimalprice.features.price.models.Category cat : priceManager.getCategories()) {
                createForumPostForCategory(cat.getName());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            
            plugin.getLogger().info("Discord Forum cleanup complete.");
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        repository.close();
    }

    @EventHandler
    public void onCategoryCreate(CategoryCreateEvent event) {
        createForumPostForCategory(event.getCategoryName());
    }
    
    @EventHandler
    public void onProductUpdate(ProductUpdateEvent event) {
        updateCategoryPost(event.getCategoryName());
    }
    
    @EventHandler
    public void onCategoryRename(CategoryRenameEvent event) {
         updateCategoryPostTitle(event.getOldName(), event.getNewName());
    }

    @EventHandler
    public void onProductRename(ProductRenameEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
             // Rebuild all? OR just find related.
             // Simplest: update all for safety or just specific one if we knew mapping.
             // We'll iterate all.
            for (ru.minimalprice.minimalprice.features.price.models.Category cat : priceManager.getCategories()) {
                updateCategoryPost(cat.getName());
            }
        });
    }

    private void createForumPostForCategory(String categoryName) {
        if (!isDiscordReady() || restUtil == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (repository.getSyncData(categoryName) != null) return;

                JsonObject embed = buildEmbed(categoryName, List.of());
                
                restUtil.createForumPost(forumChannelId, categoryName, "", embed)
                    .thenAccept(result -> {
                        if (result != null) {
                            try {
                                repository.saveSyncData(categoryName, result.threadId, result.messageId);
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }
                    });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateCategoryPost(String categoryName) {
        if (!isDiscordReady() || restUtil == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DiscordRepository.SyncData syncData = repository.getSyncData(categoryName);
                if (syncData == null) {
                    createForumPostForCategory(categoryName);
                    return;
                }

                List<Product> products = null;
                for (ru.minimalprice.minimalprice.features.price.models.Category cat : priceManager.getCategories()) {
                    if (cat.getName().equals(categoryName)) {
                        products = priceManager.getProducts(cat.getId());
                        break;
                    }
                }
                if (products == null) return;

                JsonObject embed = buildEmbed(categoryName, products);
                restUtil.updateMessage(syncData.threadId, syncData.messageId, embed);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateCategoryPostTitle(String oldName, String newName) {
         if (!isDiscordReady() || restUtil == null) return;
         
         Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
             try {
                DiscordRepository.SyncData syncData = repository.getSyncData(oldName);
                if (syncData == null) return;
                
                restUtil.updateThreadName(syncData.threadId, newName);
                
                repository.deleteSyncData(oldName);
                repository.saveSyncData(newName, syncData.threadId, syncData.messageId);
                
                updateCategoryPost(newName);
                
             } catch (Exception e) {
                 e.printStackTrace();
             }
         });
    }

    private JsonObject buildEmbed(String categoryName, List<Product> products) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", categoryName + " - Minimal Prices");
        embed.addProperty("color", 16753920); // Orange
        
        StringBuilder desc = new StringBuilder();
        String currency = plugin.getConfig().getString("currency", "$");
        
        if (products == null || products.isEmpty()) {
            desc.append("No items yet.");
        } else {
            for (Product p : products) {
                desc.append("**").append(p.getName()).append("**: ")
                    .append(p.getPrice()).append(currency).append("\n");
            }
        }
        
        embed.addProperty("description", desc.toString());
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Updated at " + java.time.LocalDateTime.now().toString());
        embed.add("footer", footer);
        
        return embed;
    }

    private boolean isDiscordReady() {
        return DiscordSRV.getPlugin() != null && DiscordSRV.getPlugin().getJda() != null; 
    }
}

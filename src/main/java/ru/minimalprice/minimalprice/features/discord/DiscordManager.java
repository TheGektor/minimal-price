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
                 initialized = true;
                 plugin.getLogger().info("DiscordSRV is ready! Initializing DiscordRestUtil...");
                 restUtil = new DiscordRestUtil(plugin);
                 performStartupCleanup();
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
        
        // Title? User screenshot shows "Category Name" with icon. We don't have icons, just use Name.
        embed.addProperty("title", "📦 " + categoryName);
        embed.addProperty("color", 3447003); // Dark background-ish or specific color? User used dark grey in screenshot, side bar color?
        // Screenshot side bar is blue-ish or custom. Let's use a nice Blue for "Market". 
        // 0x3498db (3447003) is a nice blue.
        
        ru.minimalprice.minimalprice.configuration.ConfigManager cm = new ru.minimalprice.minimalprice.configuration.ConfigManager(plugin);
        String currency = plugin.getConfig().getString("currency", "$");
        
        StringBuilder desc = new StringBuilder();
        
        // Header: Market Analysis
        desc.append("**").append(cm.getRawMessage("discord_market_analysis")).append("**\n");
        desc.append("▎ ").append(cm.getRawMessage("discord_total_positions").replace("%count%", String.valueOf(products.size()))).append("\n");
        desc.append("────────────────────────\n\n");
        
        if (products.isEmpty()) {
            desc.append("No items.");
        } else {
            for (Product p : products) {
                // Item Header - User's screenshot had a diamond or icon. We'll use a diamond bullet point.
                // Or just the Name as the header.
                desc.append("🔷 **").append(p.getName()).append("**\n");
                
                // Code block with prices
                desc.append("```yaml\n");
                String priceBlock = cm.getRawMessage("discord_price_block")
                        .replace("%price%", String.valueOf(p.getPrice()))
                        .replace("%currency%", currency);
                desc.append(priceBlock).append("\n");
                desc.append("```\n");
            }
        }
        
        embed.addProperty("description", desc.toString());
        
        JsonObject footer = new JsonObject();
        String footerText = cm.getRawMessage("discord_embed_footer").replace("%date%", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        footer.addProperty("text", footerText);
        // Icon for footer?
        // footer.addProperty("icon_url", "...");
        embed.add("footer", footer);
        
        return embed;
    }

    private boolean isDiscordReady() {
        return DiscordSRV.getPlugin() != null && DiscordSRV.getPlugin().getJda() != null; 
    }
}

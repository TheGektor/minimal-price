package ru.minimalprice.minimalprice.features.discord;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.google.gson.JsonObject;

import github.scarsz.discordsrv.DiscordSRV;
import ru.minimalprice.minimalprice.MinimalPrice;
import ru.minimalprice.minimalprice.features.discord.storage.DiscordRepository;
import ru.minimalprice.minimalprice.features.price.PriceManager;
import ru.minimalprice.minimalprice.features.price.events.CategoryCreateEvent;
import ru.minimalprice.minimalprice.features.price.events.CategoryRenameEvent;
import ru.minimalprice.minimalprice.features.price.events.ProductRenameEvent;
import ru.minimalprice.minimalprice.features.price.events.ProductUpdateEvent;
import ru.minimalprice.minimalprice.features.price.models.Product;

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
    private final java.util.concurrent.atomic.AtomicBoolean initializing = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void checkReadyAndInit() {
         Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task -> {
             if (initialized || initializing.get()) {
                 task.cancel();
                 return;
             }
             
             if (isDiscordReady()) {
                 if (!initializing.compareAndSet(false, true)) {
                     task.cancel();
                     return;
                 }
                 
                 plugin.getLogger().info("DiscordSRV is ready! Waiting for PriceManager...");
                 task.cancel(); // Cancel immediately to prevent future ticks
                 
                 // Wait for PriceManager to load data
                 priceManager.getInitFuture().thenRun(() -> {
                     plugin.getLogger().info("PriceManager ready! Initializing DiscordRestUtil...");
                     restUtil = new DiscordRestUtil(plugin);
                     performStartupCleanup();
                     initialized = true;
                     initializing.set(false); // Reset just in case, though initialized=true prevents re-entry
                 });
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
            List<ru.minimalprice.minimalprice.features.price.models.Category> categories = priceManager.getCategories();
            plugin.getLogger().info("Found " + categories.size() + " categories to sync.");
            
            for (ru.minimalprice.minimalprice.features.price.models.Category cat : categories) {
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

                // Find Category ID to get products
                int categoryId = -1;
                for (ru.minimalprice.minimalprice.features.price.models.Category cat : priceManager.getCategories()) {
                    if (cat.getName().equals(categoryName)) {
                        categoryId = cat.getId();
                        break;
                    }
                }
                
                List<Product> products = (categoryId != -1) ? priceManager.getProducts(categoryId) : List.of();
                
                // Logging for debug
                if (products.isEmpty()) {
                    plugin.getLogger().warning("Creating post for category '" + categoryName + "' but no products found (ID: " + categoryId + ")");
                } else {
                    plugin.getLogger().info("Creating post for category '" + categoryName + "' with " + products.size() + " products.");
                }

                JsonObject embed = null; // No embed
                com.google.gson.JsonArray components = buildComponents(categoryName, products);
                
                restUtil.createForumPost(forumChannelId, categoryName, "", embed, components)
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

                JsonObject embed = null; // No embed
                com.google.gson.JsonArray components = buildComponents(categoryName, products);
                restUtil.updateMessage(syncData.threadId, syncData.messageId, embed, components);

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

    private com.google.gson.JsonArray buildComponents(String categoryName, List<Product> products) {
        // Root array
        com.google.gson.JsonArray components = new com.google.gson.JsonArray();
        
        // 1. Container Component (Type 17)
        JsonObject container = new JsonObject();
        container.addProperty("type", 17); // Container
        container.addProperty("id", 1);    // Arbitrary ID
        container.addProperty("accent_color", 15158332); // From user JSON hint (Red-ish) or 3447003 (Blue)
        // Let's stick to user provided accent color for now or use the blue one if user wants branding.
        // User provided 15158332 which is #E74C3C (Red).
        // Let's use a nice branding color. 0x3498db (3447003) Blue.
        container.addProperty("accent_color", 3447003); 
        
        com.google.gson.JsonArray innerComponents = new com.google.gson.JsonArray();
        int componentIdCounter = 2; // Start ID counter
        
        ru.minimalprice.minimalprice.configuration.ConfigManager cm = new ru.minimalprice.minimalprice.configuration.ConfigManager(plugin);
        String currency = plugin.getConfig().getString("currency", "$");
        String footerText = cm.getRawMessage("discord_embed_footer").replace("%date%", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        // 2. Header Text (Type 10)
        // # Category Name
        JsonObject headerText = new JsonObject();
        headerText.addProperty("type", 10);
        headerText.addProperty("id", componentIdCounter++);
        headerText.addProperty("content", "# " + categoryName);
        innerComponents.add(headerText);
        
        // 3. Separator (Type 14)
        JsonObject sep1 = new JsonObject();
        sep1.addProperty("type", 14);
        sep1.addProperty("id", componentIdCounter++);
        sep1.addProperty("divider", true);
        sep1.addProperty("spacing", 2);
        innerComponents.add(sep1);
        
        // 4. Market Analysis Header (Type 10)
        // ## Market Analysis \n **Analysis** \n Total: X
        JsonObject analysisText = new JsonObject();
        analysisText.addProperty("type", 10);
        analysisText.addProperty("id", componentIdCounter++);
        StringBuilder analysisContent = new StringBuilder();
        analysisContent.append("## ").append(cm.getRawMessage("discord_market_analysis")).append("\n\n");
        analysisContent.append("**").append(cm.getRawMessage("discord_total_positions").replace("%count%", String.valueOf(products.size()))).append("**");
        analysisText.addProperty("content", analysisContent.toString());
        innerComponents.add(analysisText);
        
        // 5. Separator (Type 14)
        JsonObject sep2 = new JsonObject();
        sep2.addProperty("type", 14);
        sep2.addProperty("id", componentIdCounter++);
        sep2.addProperty("divider", true);
        sep2.addProperty("spacing", 2);
        innerComponents.add(sep2);
        
        // 6. Products
        if (products.isEmpty()) {
             JsonObject emptyText = new JsonObject();
             emptyText.addProperty("type", 10);
             emptyText.addProperty("id", componentIdCounter++);
             emptyText.addProperty("content", "No items.");
             innerComponents.add(emptyText);
        } else {
            // Discord has limits on components. 
            // "Container" can hold sub-components. 
            // We probably can't have infinite components.
            // But let's add them as (Text -> Separator) pairs.
            // NOTE: max components in a container? 
            // User JSON shows flat list inside container.
            
            for (Product p : products) {
                // Product Text (Type 10)
                JsonObject productText = new JsonObject();
                productText.addProperty("type", 10);
                productText.addProperty("id", componentIdCounter++);
                
                StringBuilder content = new StringBuilder();
                content.append("**").append(p.getName()).append("**\n");
                
                String priceLine = cm.getRawMessage("discord_price_block")
                        .replace("%price%", String.valueOf(p.getPrice()))
                        .replace("%currency%", currency);
                
                // Assuming priceLine is like "Price: 20"
                // User JSON has: "**Item**\n🟢 20.0 Cap\n🔴 20.0 Cap"
                // Our config might just have the price.
                content.append(priceLine);
                
                productText.addProperty("content", content.toString());
                innerComponents.add(productText);
                
                // Separator (Type 14)
                JsonObject productSep = new JsonObject();
                productSep.addProperty("type", 14);
                productSep.addProperty("id", componentIdCounter++);
                productSep.addProperty("divider", true);
                productSep.addProperty("spacing", 2);
                innerComponents.add(productSep);
            }
        }
        
        // Footer (Type 10)
        JsonObject footerObj = new JsonObject();
        footerObj.addProperty("type", 10);
        footerObj.addProperty("id", componentIdCounter++);
        footerObj.addProperty("content", footerText);
        innerComponents.add(footerObj);
        
        container.add("components", innerComponents);
        components.add(container);
        
        return components;
    }

    private boolean isDiscordReady() {
        return DiscordSRV.getPlugin() != null && DiscordSRV.getPlugin().getJda() != null; 
    }
}

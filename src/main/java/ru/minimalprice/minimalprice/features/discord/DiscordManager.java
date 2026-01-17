package ru.minimalprice.minimalprice.features.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
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

import java.awt.Color;
import java.sql.SQLException;
import java.util.List;

public class DiscordManager implements Listener {

    private final MinimalPrice plugin;
    private final DiscordRepository repository;
    private final PriceManager priceManager;
    private final String channelId;

    public DiscordManager(MinimalPrice plugin, PriceManager priceManager, String databasePath) {
        this.plugin = plugin;
        this.priceManager = priceManager;
        this.repository = new DiscordRepository(databasePath);
        // Using same config key, treating it as TextChannel
        this.channelId = plugin.getConfig().getString("discord_forum_channel_id");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void close() {
        repository.close();
    }

    @EventHandler
    public void onCategoryCreate(CategoryCreateEvent event) {
        createMessageForCategory(event.getCategoryName());
    }
    
    @EventHandler
    public void onProductUpdate(ProductUpdateEvent event) {
        updateCategoryMessage(event.getCategoryName());
    }
    
    @EventHandler
    public void onCategoryRename(CategoryRenameEvent event) {
         updateCategoryMessageTitle(event.getOldName(), event.getNewName());
    }

    @EventHandler
    public void onProductRename(ProductRenameEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (ru.minimalprice.minimalprice.features.price.models.Category cat : priceManager.getCategories()) {
                updateCategoryMessage(cat.getName());
            }
        });
    }

    private void createMessageForCategory(String categoryName) {
        if (!isDiscordReady()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (repository.getSyncData(categoryName) != null) return;

                TextChannel channel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelId);
                if (channel == null) {
                    plugin.getLogger().warning("Discord Channel ID " + channelId + " not found!");
                    return;
                }

                MessageEmbed embed = buildEmbed(categoryName, List.of());
                channel.sendMessage(embed).queue(message -> {
                    try {
                        // We use channelId as "threadId" since we aren't using threads
                        repository.saveSyncData(categoryName, channelId, message.getId());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateCategoryMessage(String categoryName) {
        if (!isDiscordReady()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DiscordRepository.SyncData syncData = repository.getSyncData(categoryName);
                
                if (syncData == null) {
                    createMessageForCategory(categoryName);
                    return;
                }
                
                TextChannel channel = DiscordSRV.getPlugin().getJda().getTextChannelById(syncData.threadId);
                if (channel == null) {
                    // Channel lost?
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

                MessageEmbed embed = buildEmbed(categoryName, products);
                channel.editMessageById(syncData.messageId, embed).queue();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateCategoryMessageTitle(String oldName, String newName) {
         if (!isDiscordReady()) return;
         
         Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
             try {
                DiscordRepository.SyncData syncData = repository.getSyncData(oldName);
                if (syncData == null) return;
                
                // Update DB
                repository.deleteSyncData(oldName);
                repository.saveSyncData(newName, syncData.threadId, syncData.messageId);
                
                // Update embed content with new name
                updateCategoryMessage(newName);
                
             } catch (Exception e) {
                 e.printStackTrace();
             }
         });
    }

    private MessageEmbed buildEmbed(String categoryName, List<Product> products) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(categoryName + " - Minimal Prices");
        builder.setColor(Color.ORANGE);
        
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
        
        builder.setDescription(desc.toString());
        builder.setFooter("Updated at " + java.time.LocalDateTime.now().toString(), null);
        
        return builder.build();
    }

    private boolean isDiscordReady() {
        return DiscordSRV.getPlugin() != null && DiscordSRV.getPlugin().getJda() != null; 
    }
}

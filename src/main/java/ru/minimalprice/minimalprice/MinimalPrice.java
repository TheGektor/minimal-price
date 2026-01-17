package ru.minimalprice.minimalprice;

import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import ru.minimalprice.minimalprice.configuration.ConfigManager;
import ru.minimalprice.minimalprice.database.DatabaseManager;
import ru.minimalprice.minimalprice.features.discord.DiscordManager;
import ru.minimalprice.minimalprice.features.price.PriceCommand;
import ru.minimalprice.minimalprice.features.price.PriceManager;
import ru.minimalprice.minimalprice.features.price.PriceTabCompleter;
import ru.minimalprice.minimalprice.features.price.storage.PriceRepository;

public final class MinimalPrice extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PriceManager priceManager;
    private DiscordManager discordManager;

    @Override
    public void onEnable() {
        // 1. Config
        saveDefaultConfig();
        
        // Save languages
        saveResource("languages/messages_en.yml", false);
        saveResource("languages/messages_ru.yml", false);
        
        this.configManager = new ConfigManager(this);

        // 2. Database
        this.databaseManager = new DatabaseManager(getDataFolder().getAbsolutePath() + "/database_v2.db");
        this.databaseManager.initDatabase();

        // 3. Features
        this.priceManager = new PriceManager(new PriceRepository(databaseManager)); // Updated PriceManager initialization
        // Events... // Placeholder for future events

        // 4. Commands
        Objects.requireNonNull(getCommand("minimal")).setExecutor(new PriceCommand(this, priceManager, configManager)); // Updated command registration
        Objects.requireNonNull(getCommand("minimal")).setTabCompleter(new PriceTabCompleter(priceManager)); // Updated command registration

        // Alias /price
        Objects.requireNonNull(getCommand("price")).setExecutor(new PriceCommand(this, priceManager, configManager)); // Updated command registration

        // 5. Discord Integration
        if (getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            this.discordManager = new ru.minimalprice.minimalprice.features.discord.DiscordManager(this, priceManager, getDataFolder().getAbsolutePath() + "/discord.db");
            getLogger().info("DiscordSRV integration enabled!");
        } else {
            getLogger().warning("DiscordSRV plugin not found or not enabled! Integration disabled.");
        }

        getLogger().info("MinimalPrice enabled!");
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) { // Changed to this.databaseManager for consistency
            this.databaseManager.close();
        }
        if (this.discordManager != null) { // Added discordManager close
            this.discordManager.close();
        }
        getLogger().info("MinimalPrice disabled!");
    }
}

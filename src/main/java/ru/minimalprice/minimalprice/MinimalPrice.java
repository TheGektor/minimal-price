package ru.minimalprice.minimalprice;

import org.bukkit.plugin.java.JavaPlugin;

import ru.minimalprice.minimalprice.configuration.ConfigManager;
import ru.minimalprice.minimalprice.database.DatabaseManager;
import ru.minimalprice.minimalprice.features.price.PriceCommand;
import ru.minimalprice.minimalprice.features.price.PriceManager;
import ru.minimalprice.minimalprice.features.price.PriceTabCompleter;
import ru.minimalprice.minimalprice.features.price.storage.PriceRepository;

public final class MinimalPrice extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PriceManager priceManager;

    @Override
    public void onEnable() {
        // 1. Config
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // 2. Database
        this.databaseManager = new DatabaseManager(getDataFolder().getAbsolutePath() + "/database_v2.db");
        this.databaseManager.initDatabase();

        // 3. Features
        PriceRepository priceRepository = new PriceRepository(databaseManager);
        this.priceManager = new PriceManager(priceRepository);

        // 4. Commands
        getCommand("minimal").setExecutor(new PriceCommand(this, priceManager, configManager));
        getCommand("minimal").setTabCompleter(new PriceTabCompleter(priceManager));

        // Alias /price
        getCommand("price").setExecutor(new PriceCommand(this, priceManager, configManager));

        getLogger().info("MinimalPrice enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("MinimalPrice disabled!");
    }
}

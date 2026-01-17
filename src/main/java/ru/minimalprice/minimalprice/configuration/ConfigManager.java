package ru.minimalprice.minimalprice.configuration;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ru.minimalprice.minimalprice.MinimalPrice;

public class ConfigManager {

    private final MinimalPrice plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration languageConfig;

    public ConfigManager(MinimalPrice plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadLocale();
    }

    public void loadLocale() {
        String locale = plugin.getConfig().getString("locale", "en");
        File langFile = new File(plugin.getDataFolder(), "languages/messages_" + locale + ".yml");
        
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file not found: " + langFile.getName() + ". Falling back to en.");
            langFile = new File(plugin.getDataFolder(), "languages/messages_en.yml");
        }
        
        this.languageConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public Component getMessage(String key) {
        String msg = languageConfig.getString(key);
        if (msg == null) return Component.text("Message not found: " + key);
        
        // Append prefix if it exists and we aren't asking for the prefix itself
        if (!key.equals("prefix")) {
            String prefix = languageConfig.getString("prefix");
            if (prefix != null) {
                msg = prefix + msg;
            }
        }
        
        // Replace currency placeholder
        String currency = plugin.getConfig().getString("currency", "$");
        msg = msg.replace("%currency%", currency);
        
        return miniMessage.deserialize(msg);
    }
    
    public Component getMessage(String key, String... placeholders) {
        String msg = languageConfig.getString(key);
        if (msg == null) return Component.text("Message not found: " + key);

        // Append prefix if it exists and we aren't asking for the prefix itself
        if (!key.equals("prefix")) {
            String prefix = languageConfig.getString("prefix");
            if (prefix != null) {
                msg = prefix + msg;
            }
        }
        
        // Replace currency placeholder
        String currency = plugin.getConfig().getString("currency", "$");
        msg = msg.replace("%currency%", currency);
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return miniMessage.deserialize(msg);
    }
    
    public String getRawMessage(String key) {
         return languageConfig.getString(key, "");
    }
}

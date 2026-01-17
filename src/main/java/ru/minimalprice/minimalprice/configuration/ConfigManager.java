package ru.minimalprice.minimalprice.configuration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import ru.minimalprice.minimalprice.MinimalPrice;

public class ConfigManager {

    private final MinimalPrice plugin;
    private final MiniMessage miniMessage;

    public ConfigManager(MinimalPrice plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component getMessage(String key) {
        String msg = plugin.getConfig().getString("messages." + key);
        if (msg == null) return Component.text("Message not found: " + key);
        return miniMessage.deserialize(msg);
    }
    
    public Component getMessage(String key, String... placeholders) {
        String msg = plugin.getConfig().getString("messages." + key);
        if (msg == null) return Component.text("Message not found: " + key);
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return miniMessage.deserialize(msg);
    }
    
    public String getRawMessage(String key) {
         return plugin.getConfig().getString("messages." + key, "");
    }
}

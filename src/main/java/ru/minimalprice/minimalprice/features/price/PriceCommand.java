package ru.minimalprice.minimalprice.features.price;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import ru.minimalprice.minimalprice.configuration.ConfigManager;
import ru.minimalprice.minimalprice.features.price.models.Category;
import ru.minimalprice.minimalprice.features.price.models.Product;

public class PriceCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PriceManager priceManager;
    private final ConfigManager configManager;

    public PriceCommand(JavaPlugin plugin, PriceManager priceManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.priceManager = priceManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // /price or /minimal without args
        if (command.getName().equalsIgnoreCase("price") || (args.length == 0)) {
            if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            showCategories(sender);
            return true;
        }

        // Subcommands for /minimal
        String sub = args[0].toLowerCase();
        
        // View permission check (implied by view, but distinct from edit)
        if (sub.equals("view")) {
             if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            handleView(sender, args);
            return true;
        }

        if (!sender.hasPermission("minimalprice.edit")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }

        switch (sub) {
            case "create":
                handleCreate(sender, args);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sender.sendMessage(configManager.getMessage("usage"));
        }
        return true;
    }

    private void showCategories(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("category_list_header"));
        
        List<Category> categories = priceManager.getCategories();
        for (Category cat : categories) {
             sender.sendMessage(configManager.getMessage("category_item", "%category%", cat.getName()));
        }
    }

    private void handleView(CommandSender sender, String[] args) {
        // /minimal view [category]
        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }
        
        String categoryName = args[1];
        // Find category by name
        Category found = null;
        for (Category cat : priceManager.getCategories()) {
            if (cat.getName().equalsIgnoreCase(categoryName)) {
                found = cat;
                break;
            }
        }
        
        if (found == null) {
            sender.sendMessage(configManager.getMessage("category_not_found"));
            return;
        }
        
        showCategoryItems(sender, found);
    }

    private void showCategoryItems(CommandSender sender, Category category) {
        sender.sendMessage(configManager.getMessage("category_view_header", "%category%", category.getName()));
        
        List<Product> products = priceManager.getProducts(category.getId());
        for (Product prod : products) {
            sender.sendMessage(configManager.getMessage("item_format", 
                    "%item%", prod.getName(), 
                    "%price%", String.valueOf(prod.getPrice())));
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        // /minimal create kategori [name]
        if (args.length < 3 || !args[1].equalsIgnoreCase("kategori")) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }
        String name = args[2];
        priceManager.createCategory(name).thenRun(() -> {
            sender.sendMessage(configManager.getMessage("create_category_success", "%name%", name));
        }).exceptionally(e -> {
            sender.sendMessage(configManager.getMessage("error_generic"));
            e.printStackTrace();
            return null;
        });
    }

    private void handleAdd(CommandSender sender, String[] args) {
        // /minimal add price [category] [item] [price]
        if (args.length < 5 || !args[1].equalsIgnoreCase("price")) {
             sender.sendMessage(configManager.getMessage("usage"));
             return;
        }
        String catName = args[2];
        String itemName = args[3];
        double price;
        try {
            price = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid price number."));
            return;
        }

        priceManager.addProduct(catName, itemName, price).thenRun(() -> {
            sender.sendMessage(configManager.getMessage("add_product_success", 
                    "%category%", catName,
                    "%item%", itemName,
                    "%price%", String.valueOf(price)));
        }).exceptionally(e -> {
            sender.sendMessage(configManager.getMessage("error_generic"));
            e.printStackTrace();
            return null;
        });
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
             sender.sendMessage(configManager.getMessage("usage"));
             return;
        }
        
        String type = args[1].toLowerCase();
        String arg1 = args[2];
        String arg2 = args[3];

        if (type.equals("kategori")) {
            priceManager.renameCategory(arg1, arg2).thenRun(() -> {
                sender.sendMessage(configManager.getMessage("rename_category_success", "%old%", arg1, "%new%", arg2));
            });
        } else if (type.equals("goods")) {
            priceManager.renameProduct(arg1, arg2).thenAccept(count -> {
                if (count > 0) {
                     sender.sendMessage(configManager.getMessage("rename_product_success", "%old%", arg1, "%new%", arg2));
                } else {
                     sender.sendMessage(Component.text("Product not found."));
                }
            });
        } else {
             sender.sendMessage(configManager.getMessage("usage"));
        }
    }

    private void handleReload(CommandSender sender) {
        configManager.reload();
        sender.sendMessage(configManager.getMessage("reload_success"));
    }
}

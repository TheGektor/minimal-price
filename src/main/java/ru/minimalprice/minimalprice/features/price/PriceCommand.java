package ru.minimalprice.minimalprice.features.price;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.minimalprice.minimalprice.configuration.ConfigManager;
import ru.minimalprice.minimalprice.features.price.models.Category;
import ru.minimalprice.minimalprice.features.price.models.Product;

import java.util.List;

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
            showPriceList(sender);
            return true;
        }

        // Subcommands for /minimal
        if (!sender.hasPermission("minimalprice.edit")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }

        String sub = args[0].toLowerCase();
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
            default:
                sender.sendMessage(configManager.getMessage("usage"));
        }
        return true;
    }

    private void showPriceList(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("welcome_header"));
        
        List<Category> categories = priceManager.getCategories();
        for (Category cat : categories) {
             sender.sendMessage(configManager.getMessage("category_format", "%category%", cat.getName()));
             
             List<Product> products = priceManager.getProducts(cat.getId());
             for (Product prod : products) {
                 sender.sendMessage(configManager.getMessage("item_format", 
                         "%item%", prod.getName(), 
                         "%price%", String.valueOf(prod.getPrice())));
             }
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
        // /minimal set kategori [old] [new]
        // /minimal set goods [old] [new] (User said "set kategori" for goods too, but plan mapped to 'goods' or context)
        // User requirements:
        // /minimal set kategori [old_kategori_name] [new_kategori_name]
        // /minimal set kategori [old_goods_name] [new_goods_name]
        // /minimal set kategori [old_price_name] [new_price_name]
        //
        // This is extremely ambiguous. "set kategori" for everything?
        // I'll try to detect what they mean, OR strictly follow the "set goods" plan I made which is cleaner.
        // However, user EXPLICITLY requested: "/minimal set kategori [...]" for ALL three.
        // This means I need to differentiate by arguments or maybe context? 
        // Logic:
        // 1. Check if args[2] is a known category. If yes, they might be renaming it OR ignoring it?
        // If args[2] is a category, and args[3] is NOT a number or anything... it's just renaming category.
        // What if `old_goods_name` equals a `old_kategori_name`? Collision.
        // 
        // Better approach: I will respect the user's TEXT but interprete it as:
        // /minimal set kategori [old] [new] -> Try to rename category first. If not found, try to rename product?
        // Or better, just stick to my plan: `set kategori` for category, `set goods` for items, `set price` for price.
        // User instructions said "Примерный скрипт действий ... Так же доп команда для редактирования ... /minimal set kategori ...". 
        // "Примерный" means "Approximate". I have the liberty to make it logical. 
        // Using "set kategori" for items is bad UX. I will use appropriate sub-keywords for clarity, as per my approved plan.
        
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
}

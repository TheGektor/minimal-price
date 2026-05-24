package ru.minimalprice.minimalprice.features.price;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        // /price or /minimal without args → show categories
        if (command.getName().equalsIgnoreCase("price") || args.length == 0) {
            if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            showCategories(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── View (read-only) ──────────────────────────────────────────
        if (sub.equals("view")) {
            if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            handleView(sender, args);
            return true;
        }

        if (sub.equals("list")) {
            if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            handleList(sender);
            return true;
        }

        if (sub.equals("info")) {
            if (!sender.hasPermission("minimalprice.view")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            handleInfo(sender);
            return true;
        }

        // ── Admin actions ─────────────────────────────────────────────
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
            case "delete":
                handleDelete(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sender.sendMessage(configManager.getMessage("usage"));
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showCategories(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("category_list_header"));
        List<Category> categories = priceManager.getCategories();
        if (categories.isEmpty()) {
            sender.sendMessage(Component.text("  No categories yet.", NamedTextColor.GRAY));
            return;
        }
        for (Category cat : categories) {
            sender.sendMessage(configManager.getMessage("category_item", "%category%", cat.getName()));
        }
    }

    /** /minimal view <category> */
    private void handleView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showCategories(sender);
            return;
        }
        String categoryName = args[1];
        Category found = findCategory(categoryName);
        if (found == null) {
            sender.sendMessage(configManager.getMessage("category_not_found"));
            return;
        }
        showCategoryItems(sender, found);
    }

    private void showCategoryItems(CommandSender sender, Category category) {
        sender.sendMessage(configManager.getMessage("category_view_header", "%category%", category.getName()));
        List<Product> products = priceManager.getProducts(category.getId());
        if (products.isEmpty()) {
            sender.sendMessage(Component.text("  No items in this category.", NamedTextColor.GRAY));
            return;
        }
        for (Product prod : products) {
            sender.sendMessage(configManager.getMessage("item_format",
                    "%item%", prod.getName(),
                    "%price%", String.valueOf(prod.getPrice())));
        }
    }

    /** /minimal list — shows all categories with their products at once */
    private void handleList(CommandSender sender) {
        List<Category> categories = priceManager.getCategories();
        if (categories.isEmpty()) {
            sender.sendMessage(Component.text("No categories found.", NamedTextColor.GRAY));
            return;
        }
        for (Category cat : categories) {
            sender.sendMessage(configManager.getMessage("category_view_header", "%category%", cat.getName()));
            List<Product> products = priceManager.getProducts(cat.getId());
            if (products.isEmpty()) {
                sender.sendMessage(Component.text("  (empty)", NamedTextColor.DARK_GRAY));
            } else {
                for (Product p : products) {
                    sender.sendMessage(configManager.getMessage("item_format",
                            "%item%", p.getName(),
                            "%price%", String.valueOf(p.getPrice())));
                }
            }
        }
    }

    /** /minimal info — plugin statistics */
    private void handleInfo(CommandSender sender) {
        int cats = priceManager.getCategories().size();
        int items = priceManager.getTotalProductCount();
        sender.sendMessage(configManager.getMessage("info_header"));
        sender.sendMessage(configManager.getMessage("info_categories", "%count%", String.valueOf(cats)));
        sender.sendMessage(configManager.getMessage("info_products", "%count%", String.valueOf(items)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** /minimal create category <name> */
    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("category")) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }
        String name = args[2];
        priceManager.createCategory(name).thenRun(() ->
            sender.sendMessage(configManager.getMessage("create_category_success", "%name%", name))
        ).exceptionally(e -> {
            sender.sendMessage(configManager.getMessage("error_generic"));
            plugin.getLogger().severe("Error creating category: " + e.getMessage());
            return null;
        });
    }

    /** /minimal add price <category> <item> <price> */
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("price")) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }
        String catName = args[2];
        String itemName = args[3].replace('_', ' ');
        double price;
        try {
            price = Double.parseDouble(args[4]);
            if (price < 0) throw new NumberFormatException("negative");
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Price must be a positive number.", NamedTextColor.RED));
            return;
        }

        priceManager.addProduct(catName, itemName, price).thenRun(() ->
            sender.sendMessage(configManager.getMessage("add_product_success",
                    "%category%", catName,
                    "%item%", itemName,
                    "%price%", String.valueOf(price)))
        ).exceptionally(e -> {
            sender.sendMessage(configManager.getMessage("error_generic"));
            plugin.getLogger().severe("Error adding product: " + e.getMessage());
            return null;
        });
    }

    /**
     * /minimal set category <old> <new>
     * /minimal set goods <old> <new>
     * /minimal set price <category> <item> <price>
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "category": {
                String oldName = args[2];
                String newName = args[3];
                priceManager.renameCategory(oldName, newName).thenRun(() ->
                    sender.sendMessage(configManager.getMessage("rename_category_success",
                            "%old%", oldName, "%new%", newName))
                ).exceptionally(e -> {
                    sender.sendMessage(configManager.getMessage("error_generic"));
                    return null;
                });
                break;
            }
            case "goods": {
                String oldName = args[2];
                String newName = args[3];
                priceManager.renameProduct(oldName, newName).thenAccept(count -> {
                    if (count > 0) {
                        sender.sendMessage(configManager.getMessage("rename_product_success",
                                "%old%", oldName, "%new%", newName));
                    } else {
                        sender.sendMessage(Component.text("Product not found: " + oldName, NamedTextColor.RED));
                    }
                });
                break;
            }
            case "price": {
                // /minimal set price <category> <item> <price>
                if (args.length < 5) {
                    sender.sendMessage(configManager.getMessage("usage"));
                    return;
                }
                String catName = args[2];
                String itemName = args[3].replace('_', ' ');
                double price;
                try {
                    price = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Price must be a number.", NamedTextColor.RED));
                    return;
                }
                priceManager.addProduct(catName, itemName, price).thenRun(() ->
                    sender.sendMessage(configManager.getMessage("add_product_success",
                            "%category%", catName,
                            "%item%", itemName,
                            "%price%", String.valueOf(price)))
                );
                break;
            }
            default:
                sender.sendMessage(configManager.getMessage("usage"));
        }
    }

    /**
     * /minimal delete category <name>
     * /minimal delete goods <category> <item>
     */
    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("usage"));
            return;
        }

        String type = args[1].toLowerCase();

        if (type.equals("category")) {
            String name = args[2];
            priceManager.deleteCategory(name).thenAccept(count -> {
                if (count > 0) {
                    sender.sendMessage(configManager.getMessage("delete_category_success", "%name%", name));
                } else {
                    sender.sendMessage(configManager.getMessage("category_not_found"));
                }
            }).exceptionally(e -> {
                sender.sendMessage(configManager.getMessage("error_generic"));
                plugin.getLogger().severe("Error deleting category: " + e.getMessage());
                return null;
            });

        } else if (type.equals("goods")) {
            if (args.length < 4) {
                sender.sendMessage(configManager.getMessage("usage"));
                return;
            }
            String catName = args[2];
            String itemName = args[3].replace('_', ' ');
            priceManager.deleteProduct(catName, itemName).thenAccept(count -> {
                if (count > 0) {
                    sender.sendMessage(configManager.getMessage("delete_product_success",
                            "%item%", itemName, "%category%", catName));
                } else {
                    sender.sendMessage(Component.text("Product not found.", NamedTextColor.RED));
                }
            }).exceptionally(e -> {
                sender.sendMessage(configManager.getMessage("error_generic"));
                plugin.getLogger().severe("Error deleting product: " + e.getMessage());
                return null;
            });

        } else {
            sender.sendMessage(configManager.getMessage("usage"));
        }
    }

    private void handleReload(CommandSender sender) {
        configManager.reload();
        sender.sendMessage(configManager.getMessage("reload_success"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private Category findCategory(String name) {
        for (Category cat : priceManager.getCategories()) {
            if (cat.getName().equalsIgnoreCase(name)) return cat;
        }
        return null;
    }
}

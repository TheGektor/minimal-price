package ru.minimalprice.minimalprice.features.price;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ru.minimalprice.minimalprice.features.price.models.Category;
import ru.minimalprice.minimalprice.features.price.models.Product;

public class PriceTabCompleter implements TabCompleter {

    private final PriceManager priceManager;

    public PriceTabCompleter(PriceManager priceManager) {
        this.priceManager = priceManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minimalprice.edit")) return Collections.emptyList();

        // /minimal [sub]
        if (args.length == 1) {
            return filter(List.of("create", "add", "set"), args[0]);
        }
        
        String sub = args[0].toLowerCase();
        
        // /minimal create kategori
        if (sub.equals("create")) {
             if (args.length == 2) return filter(List.of("kategori"), args[1]);
        }
        
        // /minimal add price [category] [item] [price]
        if (sub.equals("add")) {
            if (args.length == 2) return filter(List.of("price"), args[1]);
            if (args.length == 3) {
                // Return categories
                return filter(priceManager.getCategories().stream().map(Category::getName).collect(Collectors.toList()), args[2]);
            }
        }
        
        // /minimal set [kategori|goods]
        if (sub.equals("set")) {
            if (args.length == 2) return filter(List.of("kategori", "goods"), args[1]);
            
            String type = args[1].toLowerCase();
            if (type.equals("kategori")) {
                if (args.length == 3) {
                     return filter(priceManager.getCategories().stream().map(Category::getName).collect(Collectors.toList()), args[2]);
                }
            } else if (type.equals("goods")) {
                if (args.length == 3) {
                     // We don't have a fast "all products" lookup in cache easily without iterating all cats.
                     // But we can iterate.
                     List<String> allProducts = new ArrayList<>();
                     for (Category cat : priceManager.getCategories()) {
                         for (Product p : priceManager.getProducts(cat.getId())) {
                             allProducts.add(p.getName());
                         }
                     }
                     return filter(allProducts, args[2]);
                }
            }
        }

        return Collections.emptyList();
    }
    
    private List<String> filter(List<String> src, String arg) {
        String lower = arg.toLowerCase();
        return src.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}

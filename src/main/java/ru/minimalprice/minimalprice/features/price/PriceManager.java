package ru.minimalprice.minimalprice.features.price;

import ru.minimalprice.minimalprice.features.price.models.Category;
import ru.minimalprice.minimalprice.features.price.models.Product;
import ru.minimalprice.minimalprice.features.price.storage.PriceRepository;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PriceManager {

    private final PriceRepository repository;
    
    // Cache: Category -> List of Products
    private final List<Category> categoriesCache = new CopyOnWriteArrayList<>();
    private final Map<Integer, List<Product>> productsCache = new ConcurrentHashMap<>();

    public PriceManager(PriceRepository repository) {
        this.repository = repository;
        reloadCache();
    }

    public void reloadCache() {
        CompletableFuture.runAsync(() -> {
            try {
                categoriesCache.clear();
                productsCache.clear();
                
                List<Category> cats = repository.getAllCategories();
                categoriesCache.addAll(cats);
                
                for (Category cat : cats) {
                    List<Product> prods = repository.getProductsByCategory(cat.getId());
                    productsCache.put(cat.getId(), prods);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public List<Category> getCategories() {
        return Collections.unmodifiableList(categoriesCache);
    }

    public List<Product> getProducts(int categoryId) {
        return productsCache.getOrDefault(categoryId, Collections.emptyList());
    }

    public CompletableFuture<Void> createCategory(String name) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.createCategory(name);
                reloadCache();
                org.bukkit.Bukkit.getPluginManager().callEvent(new ru.minimalprice.minimalprice.features.price.events.CategoryCreateEvent(name));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> addProduct(String category, String product, double price) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.upsertProduct(category, product, price);
                reloadCache();
                 org.bukkit.Bukkit.getPluginManager().callEvent(new ru.minimalprice.minimalprice.features.price.events.ProductUpdateEvent(category, product, price));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> renameCategory(String oldName, String newName) {
         return CompletableFuture.runAsync(() -> {
            try {
                repository.renameCategory(oldName, newName);
                reloadCache();
                org.bukkit.Bukkit.getPluginManager().callEvent(new ru.minimalprice.minimalprice.features.price.events.CategoryRenameEvent(oldName, newName));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    public CompletableFuture<Integer> renameProduct(String oldName, String newName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int count = repository.renameProduct(oldName, newName);
                reloadCache();
                if (count > 0) {
                     org.bukkit.Bukkit.getPluginManager().callEvent(new ru.minimalprice.minimalprice.features.price.events.ProductRenameEvent(oldName, newName));
                }
                return count;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

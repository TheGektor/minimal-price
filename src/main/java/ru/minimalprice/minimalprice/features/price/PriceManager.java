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
    private final CompletableFuture<Void> initFuture = new CompletableFuture<>();

    public PriceManager(PriceRepository repository) {
        this.repository = repository;
        reloadCache().thenRun(() -> initFuture.complete(null));
    }

    public CompletableFuture<Void> reloadCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Temporary lists to avoid clearing main cache if error occurs
                List<Category> tempCats = repository.getAllCategories();
                Map<Integer, List<Product>> tempProds = new ConcurrentHashMap<>();
                
                for (Category cat : tempCats) {
                    List<Product> prods = repository.getProductsByCategory(cat.getId());
                    tempProds.put(cat.getId(), prods);
                }
                
                // Update main cache atomically-ish
                categoriesCache.clear();
                categoriesCache.addAll(tempCats);
                productsCache.clear();
                productsCache.putAll(tempProds);
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Void> getInitFuture() {
        return initFuture;
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

    public CompletableFuture<Integer> deleteCategory(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int count = repository.deleteCategory(name);
                if (count > 0) {
                    reloadCache();
                    org.bukkit.Bukkit.getPluginManager().callEvent(
                        new ru.minimalprice.minimalprice.features.price.events.CategoryDeleteEvent(name));
                }
                return count;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Integer> deleteProduct(String categoryName, String productName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int count = repository.deleteProduct(categoryName, productName);
                if (count > 0) {
                    reloadCache();
                    org.bukkit.Bukkit.getPluginManager().callEvent(
                        new ru.minimalprice.minimalprice.features.price.events.ProductDeleteEvent(categoryName, productName));
                }
                return count;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Returns the total count of all products across all categories. */
    public int getTotalProductCount() {
        return productsCache.values().stream().mapToInt(List::size).sum();
    }

    public void close() {
        // Nothing to close on this side; DatabaseManager handles the pool.
        // Kept for symmetry and future use.
    }
}

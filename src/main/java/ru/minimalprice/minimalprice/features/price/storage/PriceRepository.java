package ru.minimalprice.minimalprice.features.price.storage;

import ru.minimalprice.minimalprice.database.DatabaseManager;
import ru.minimalprice.minimalprice.features.price.models.Category;
import ru.minimalprice.minimalprice.features.price.models.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PriceRepository {

    private final DatabaseManager databaseManager;

    public PriceRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createCategory(String name) throws SQLException {
        String sql = "INSERT INTO mp_categories (name) VALUES (?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    public void upsertProduct(String categoryName, String productName, double price) throws SQLException {
        // Find Category ID first
        int categoryId = getCategoryId(categoryName);
        if (categoryId == -1) {
            throw new SQLException("Category not found: " + categoryName);
        }

        // Upsert logic for SQLite
        String sql = "INSERT INTO mp_items (category_id, name, price) VALUES (?, ?, ?) " +
                "ON CONFLICT(category_id, name) DO UPDATE SET price = excluded.price";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, categoryId);
            stmt.setString(2, productName);
            stmt.setDouble(3, price);
            stmt.executeUpdate();
        }
    }

    public void renameCategory(String oldName, String newName) throws SQLException {
        String sql = "UPDATE mp_categories SET name = ? WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, oldName);
            stmt.executeUpdate();
        }
    }

    // Renames a product across ALL categories (if multiple exist) or finds the specific one.
    // For simplicity given the ambiguous command, we update where name matches.
    public int renameProduct(String oldName, String newName) throws SQLException {
        String sql = "UPDATE mp_items SET name = ? WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, oldName);
            return stmt.executeUpdate(); // Returns number of affected rows
        }
    }

    public List<Category> getAllCategories() throws SQLException {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT id, name FROM mp_categories";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(new Category(rs.getInt("id"), rs.getString("name")));
            }
        }
        return categories;
    }

    public List<Product> getProductsByCategory(int categoryId) throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT id, name, price FROM mp_items WHERE category_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(new Product(
                            rs.getInt("id"),
                            categoryId,
                            rs.getString("name"),
                            rs.getDouble("price")
                    ));
                }
            }
        }
        return products;
    }

    private int getCategoryId(String name) throws SQLException {
        String sql = "SELECT id FROM mp_categories WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }
}

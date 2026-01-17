package ru.minimalprice.minimalprice.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final String url;
    private HikariDataSource dataSource;

    public DatabaseManager(String path) {
        this.url = "jdbc:sqlite:" + path;
    }

    public void initDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(10);
        
        this.dataSource = new HikariDataSource(config);
        
        createTables();
    }

    private void createTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Categories table
            stmt.execute("CREATE TABLE IF NOT EXISTS mp_categories (" +
                    "name VARCHAR(255) PRIMARY KEY" +
                    ");");

            // Items table
            // We use category_name as FK. If name changes, we need to update items. 
            // Or use an ID. Plan said ID, but user commands use names.
            // Using Name as PK for simplicity with commands, or ID? 
            // Requests: /minimal create kategori [name] -> Name is unique key naturally.
            // /minimal set kategori [old] [new] -> UPDATE mp_categories SET name=new WHERE name=old; + Cascade or manual update.
            // Let's stick to Name as PK for Category to simplify lookup, or ID and a Unique Index on Name.
            // Let's use ID for stability, Name for display/lookup.
            
            stmt.execute("CREATE TABLE IF NOT EXISTS mp_categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name VARCHAR(255) NOT NULL UNIQUE" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS mp_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "category_id INTEGER NOT NULL," +
                    "name VARCHAR(255) NOT NULL," +
                    "price DOUBLE NOT NULL," +
                    "FOREIGN KEY(category_id) REFERENCES mp_categories(id) ON DELETE CASCADE ON UPDATE CASCADE," +
                    "UNIQUE(category_id, name)" +
                    ");");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}

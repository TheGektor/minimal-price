package ru.minimalprice.minimalprice.features.discord.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DiscordRepository {

    private final HikariDataSource dataSource;

    public DiscordRepository(String databasePath) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databasePath);
        config.setMaximumPoolSize(5); 

        this.dataSource = new HikariDataSource(config);
        
        createTables();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Table for mapping Category Name -> Thread ID & Message ID
            stmt.execute("CREATE TABLE IF NOT EXISTS discord_sync (" +
                    "category_name VARCHAR(255) PRIMARY KEY, " +
                    "thread_id VARCHAR(255) NOT NULL, " +
                    "message_id VARCHAR(255) NOT NULL" +
                    ")");
                    
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Get sync info
    public SyncData getSyncData(String categoryName) throws SQLException {
        String sql = "SELECT thread_id, message_id FROM discord_sync WHERE category_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SyncData(rs.getString("thread_id"), rs.getString("message_id"));
                }
            }
        }
        return null;
    }
    
    // Set sync info
    public void saveSyncData(String categoryName, String threadId, String messageId) throws SQLException {
        String sql = "INSERT OR REPLACE INTO discord_sync (category_name, thread_id, message_id) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            pstmt.setString(2, threadId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
        }
    }
    
    public void deleteSyncData(String categoryName) throws SQLException {
        String sql = "DELETE FROM discord_sync WHERE category_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             pstmt.setString(1, categoryName);
             pstmt.executeUpdate();
        }
    }
    
    public java.util.Map<String, SyncData> getAllSyncData() throws SQLException {
        java.util.Map<String, SyncData> result = new java.util.HashMap<>();
        String sql = "SELECT category_name, thread_id, message_id FROM discord_sync";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("category_name"), 
                    new SyncData(rs.getString("thread_id"), rs.getString("message_id")));
            }
        }
        return result;
    }
    
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public static class SyncData {
        public final String threadId;
        public final String messageId;
        
        public SyncData(String threadId, String messageId) {
            this.threadId = threadId;
            this.messageId = messageId;
        }
    }
}

package org.example.dbService;

import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseService {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Connection connection;
    private Logger logger;

    public DatabaseService(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.logger = LoggerFactory.getLogger(DatabaseService.class);
        try {
            this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            logger.error(e.getMessage());
            throw new RuntimeException("Error: cannot connect to database", e);
        }
    }

    public Long getUserIdIfLoggedIn(String tgUsername) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT user_id, is_in, time_at FROM log_in_out WHERE tg_username = ? ORDER BY time_at DESC LIMIT 1");
        statement.setString(1, tgUsername);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            boolean isIn = resultSet.getBoolean("is_in");
            if (isIn) {
                return resultSet.getLong("user_id");
            }
        }
        return null;
    }

    public Long getUserIdIfExists(String username) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?");
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getLong("id");
        }

        return null;
    }

    public Long getCurrencyIdIfExists(String currencyStr) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT id FROM currency WHERE name = ?");
        statement.setString(1, currencyStr);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getLong("id");
        }
        throw new SQLException("No such currency: " + currencyStr);
    }

    public void createUser(String username, String passwordHash, Long defaultPairFromId, Long defaultPairToId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username, password_hash, default_pair_from_id, default_pair_to_id) VALUES (?, ?, ?, ?)");
        statement.setString(1, username);
        statement.setString(2, passwordHash);
        statement.setLong(3, defaultPairFromId);
        statement.setLong(4, defaultPairToId);
        statement.executeUpdate();
    }

    public String getPasswordHash(Long userId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT password_hash FROM users WHERE id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("password_hash");
        }
        throw new RuntimeException("Logic error");
    }

    public void logInUser(Long userId, String tgUsername) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)");
        statement.setLong(1, userId);
        statement.setString(2, tgUsername);
        statement.setBoolean(3, true);
        statement.executeUpdate();
    }

    public void logOutUser(Long userId, String tgUsername) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)");
        statement.setLong(1, userId);
        statement.setString(2, tgUsername);
        statement.setBoolean(3, false);
        statement.executeUpdate();
    }



    // Add other database-related methods as needed
}
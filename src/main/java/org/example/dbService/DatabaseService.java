package org.example.dbService;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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

    public String getHomeCurrency(Long userId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT currency.name FROM users JOIN currency ON default_pair_from_id = currency.id WHERE users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("name");
        }

        throw new RuntimeException("Error: user cannot be without default_pair_from_id, code problem");
    }

    public String getDefaultToCurrency(Long userId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT currency.name FROM users JOIN currency ON default_pair_to_id = currency.id WHERE users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("name");
        }
        throw new RuntimeException("Error: user cannot be without default_pair_to_id, code problem");
    }

    public String getDefaultPair(Long userId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT c1.name AS currencyFrom, c2.name AS currencyTo " +
                "FROM users JOIN currency AS c1 ON default_pair_from_id = c1.id JOIN currency AS c2 ON default_pair_to_id = c2.id WHERE users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("currencyFrom") + "-" + resultSet.getString("currencyTo");
        }

        throw new RuntimeException("Error: user must have currency pair!");
    }

    public void changeHomeCurrency(Long userId, String currency) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("UPDATE users\n" +
                "SET default_pair_from_id = (\n" +
                "    SELECT id\n" +
                "    FROM currency\n" +
                "    WHERE name = ?\n" +
                ")\n" +
                "WHERE id = ?");
        statement.setString(1, currency);
        statement.setLong(2, userId);
        statement.executeUpdate();
    }

    public Boolean isCurrencyExists(String currency) throws SQLException {
        PreparedStatement checkStatement = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM currency WHERE name = ?)");
        checkStatement.setString(1, currency);
        ResultSet resultSet = checkStatement.executeQuery();
        resultSet.next();
        return resultSet.getBoolean(1);
    }

    public void changeDefaultPair(Long userId, String from, String to) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("UPDATE users\n" +
                "SET default_pair_from_id = (\n" +
                "    SELECT id\n" +
                "    FROM currency\n" +
                "    WHERE name = ?\n" +
                "),\n" +
                "default_pair_to_id = (\n" +
                "    SELECT id\n" +
                "    FROM currency\n" +
                "    WHERE name = ?\n" +
                ")\n" +
                "WHERE id = ?");
        statement.setString(1, from);
        statement.setString(2, to);
        statement.setLong(3, userId);
        statement.executeUpdate();
    }

    public Long getCurrencyIdByName(String name) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT id FROM currency WHERE name = ?");
        statement.setString(1, name);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getLong("id");
        }
        throw new NoSuchElementException("There is no currency with this name");
    }

    public void saveLogToConversionHistory(Long userId, Long fromCurrencyId, Long toCurrencyId, Double amount, Double rate) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO conversion_history " +
                "(user_id, from_currency_id, to_currency_id, amount, rate) VALUES (?, ?, ?, ?, ?)");
        statement.setLong(1, userId);
        statement.setLong(2, fromCurrencyId);
        statement.setLong(3, toCurrencyId);
        statement.setDouble(4, amount);
        statement.setDouble(5, rate);
        statement.executeUpdate();
    }

    public List<String> findConversionHistoryByUserIdAndPeriod(Long userId, String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) throws SQLException {
        List<String> conversionHistories = new ArrayList<>();
        ResultSet resultSet;
        // to include the entire day
        endDate = endDate.plusDays(1);
        if (fromCurrency == null && toCurrency == null) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT c1.name AS currencyFrom, c2.name AS currencyTo, ch.amount, ch.rate, ch.created_at " +
                            "FROM conversion_history ch " +
                            "JOIN currency c1 ON ch.from_currency_id = c1.id " +
                            "JOIN currency c2 ON ch.to_currency_id = c2.id " +
                            "WHERE ch.user_id = ? AND ch.created_at BETWEEN ? AND ? ORDER BY ch.created_at");
            statement.setLong(1, userId);
            statement.setDate(2, Date.valueOf(startDate));
            statement.setDate(3, Date.valueOf(endDate));
            resultSet = statement.executeQuery();
        } else if (toCurrency == null) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT c1.name AS currencyFrom, c2.name AS currencyTo, ch.amount, ch.rate, ch.created_at " +
                            "FROM conversion_history ch " +
                            "JOIN currency c1 ON ch.from_currency_id = c1.id " +
                            "JOIN currency c2 ON ch.to_currency_id = c2.id " +
                            "WHERE ch.user_id = ? AND (c1.name = ? OR c2.name = ?) AND ch.created_at BETWEEN ? AND ? ORDER BY ch.created_at");
            statement.setLong(1, userId);
            statement.setString(2, fromCurrency);
            statement.setString(3, fromCurrency);
            statement.setDate(4, Date.valueOf(startDate));
            statement.setDate(5, Date.valueOf(endDate));
            resultSet = statement.executeQuery();
        } else {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT c1.name AS currencyFrom, c2.name AS currencyTo, ch.amount, ch.rate, ch.created_at " +
                            "FROM conversion_history ch " +
                            "JOIN currency c1 ON ch.from_currency_id = c1.id " +
                            "JOIN currency c2 ON ch.to_currency_id = c2.id " +
                            "WHERE ch.user_id = ? AND c1.name = ? AND c2.name = ? AND ch.created_at BETWEEN ? AND ? ORDER BY ch.created_at");
            statement.setLong(1, userId);
            statement.setString(2, fromCurrency);
            statement.setString(3, toCurrency);
            statement.setDate(4, Date.valueOf(startDate));
            statement.setDate(5, Date.valueOf(endDate));
            resultSet = statement.executeQuery();
        }

        while (resultSet.next()) {
            String fromCurrencyName = resultSet.getString("currencyFrom");
            String toCurrencyName = resultSet.getString("currencyTo");
            Double amount = resultSet.getDouble("amount");
            Double rate = resultSet.getDouble("rate");
            LocalDate createdAt = resultSet.getDate("created_at").toLocalDate();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            conversionHistories.add(String.format("%s: %s-%s amount: %.2f, rate: %.2f", createdAt.format(formatter), fromCurrencyName, toCurrencyName, amount, rate));
        }
        return conversionHistories;
    }
}
package org.example.dbService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseService {
    private Logger logger;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long getUserIdIfLoggedIn(String tgUsername) {
        String sql = "SELECT user_id, is_in FROM log_in_out WHERE tg_username = ? ORDER BY time_at DESC LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{tgUsername}, (rs, rowNum) -> rs.getBoolean("is_in") ? rs.getLong("user_id") : null);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Long getUserIdIfExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{username}, Long.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Long getCurrencyIdIfExists(String currencyStr) {
        String sql = "SELECT id FROM currency WHERE name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{currencyStr}, Long.class);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("No such currency: " + currencyStr);
        }
    }

    public void createUser(String username, String passwordHash, Long defaultPairFromId, Long defaultPairToId) {
        String sql = "INSERT INTO users (username, password_hash, default_pair_from_id, default_pair_to_id) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, username, passwordHash, defaultPairFromId, defaultPairToId);
    }

    public String getPasswordHash(Long userId) {
        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{userId}, String.class);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Logic error");
        }
    }

    public void logInUser(Long userId, String tgUsername) {
        String sql = "INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, userId, tgUsername, true);
    }

    public void logOutUser(Long userId, String tgUsername) {
        String sql = "INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, userId, tgUsername, false);
    }

    public String getHomeCurrency(Long userId) {
        String sql = "SELECT currency.name FROM users JOIN currency ON default_pair_from_id = currency.id WHERE users.id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{userId}, String.class);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Error: user cannot be without default_pair_from_id, code problem");
        }
    }

    public String getDefaultToCurrency(Long userId) {
        String sql = "SELECT currency.name FROM users JOIN currency ON default_pair_to_id = currency.id WHERE users.id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{userId}, String.class);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Error: user cannot be without default_pair_to_id, code problem");
        }
    }

    public String getDefaultPair(Long userId) {
        String sql = "SELECT c1.name AS currencyFrom, c2.name AS currencyTo " +
                "FROM users " +
                "JOIN currency c1 ON default_pair_from_id = c1.id " +
                "JOIN currency c2 ON default_pair_to_id = c2.id " +
                "WHERE users.id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{userId}, (rs, rowNum) ->
                    rs.getString("currencyFrom") + "-" + rs.getString("currencyTo"));
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Error: user must have currency pair!");
        }
    }

    public void changeHomeCurrency(Long userId, String currency) {
        String sql = "UPDATE users " +
                "SET default_pair_from_id = (SELECT id FROM currency WHERE name = ?) " +
                "WHERE id = ?";
        jdbcTemplate.update(sql, currency, userId);
    }

    public Boolean isCurrencyExists(String currency) {
        String sql = "SELECT EXISTS(SELECT 1 FROM currency WHERE name = ?)";
        return jdbcTemplate.queryForObject(sql, new Object[]{currency}, Boolean.class);
    }

    public void changeDefaultPair(Long userId, String from, String to) {
        String sql = "UPDATE users " +
                "SET default_pair_from_id = (SELECT id FROM currency WHERE name = ?), " +
                "    default_pair_to_id = (SELECT id FROM currency WHERE name = ?) " +
                "WHERE id = ?";
        jdbcTemplate.update(sql, from, to, userId);
    }

    public Long getCurrencyIdByName(String name) {
        String sql = "SELECT id FROM currency WHERE name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{name}, Long.class);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("There is no currency with this name");
        }
    }

    public void saveLogToConversionHistory(Long userId, Long fromCurrencyId, Long toCurrencyId, Double amount, Double rate) {
        String sql = "INSERT INTO conversion_history (user_id, from_currency_id, to_currency_id, amount, rate) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, userId, fromCurrencyId, toCurrencyId, amount, rate);
    }

    public List<String> findConversionHistoryByUserIdAndPeriod(Long userId, String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) {
        List<String> conversionHistories = new ArrayList<>();
        endDate = endDate.plusDays(1); // to include the entire day

        String sql = "SELECT c1.name AS currencyFrom, c2.name AS currencyTo, ch.amount, ch.rate, ch.created_at " +
                "FROM conversion_history ch " +
                "JOIN currency c1 ON ch.from_currency_id = c1.id " +
                "JOIN currency c2 ON ch.to_currency_id = c2.id " +
                "WHERE ch.user_id = ? " +
                "AND ch.created_at BETWEEN ? AND ? ";

        if (fromCurrency != null && toCurrency != null) {
            sql += "AND c1.name = ? AND c2.name = ? ";
        } else if (fromCurrency != null) {
            sql += "AND (c1.name = ? OR c2.name = ?) ";
        }

        sql += "ORDER BY ch.created_at";

        Object[] args = new Object[fromCurrency == null && toCurrency == null ? 3 : fromCurrency == null ? 4 : 5];
        int index = 0;
        args[index++] = userId;
        args[index++] = startDate;
        args[index++] = endDate;

        if (fromCurrency != null && toCurrency != null) {
            args[index++] = fromCurrency;
            args[index++] = toCurrency;
        } else if (fromCurrency != null) {
            args[index++] = fromCurrency;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        jdbcTemplate.query(sql, args, (rs) -> {
            String fromCurrencyName = rs.getString("currencyFrom");
            String toCurrencyName = rs.getString("currencyTo");
            Double amount = rs.getDouble("amount");
            Double rate = rs.getDouble("rate");
            LocalDate createdAt = rs.getDate("created_at").toLocalDate();
            conversionHistories.add(String.format("%s: %s-%s amount: %.2f, rate: %.2f", createdAt.format(formatter), fromCurrencyName, toCurrencyName, amount, rate));
        });

        return conversionHistories;
    }
}
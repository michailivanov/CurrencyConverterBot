package org.example.config;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Random;

@Configuration
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private final Random random = new Random();

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    public DatabaseConfig(DataSource dataSource, ResourceLoader resourceLoader) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initializeDatabase() {
        // Initialize the database schema
        Resource resource = resourceLoader.getResource("classpath:schema.sql");
        try (Connection connection = dataSource.getConnection();
             BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (line.endsWith(";")) {
                    String sql = sb.toString();
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.execute();
                    }
                    sb.setLength(0);
                }
            }
            logger.info("Database schema initialized successfully");
        } catch (IOException | SQLException e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }

        // Insert initial currency data
        try (Connection connection = dataSource.getConnection()) {
            String[] currencies = {"USD", "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM",
                    "BBD", "BCH", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BRL", "BSD", "BTC", "BTG", "BWP",
                    "BZD", "CAD", "CDF", "CHF", "CLP", "CNH", "CNY", "COP", "CRC", "CUC", "CUP", "CVE", "CZK", "DJF",
                    "DKK", "DOP", "DZD", "EGP", "EOS", "ETB", "ETH", "EUR", "FJD", "GBP", "GEL", "GHS", "GIP", "GMD",
                    "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK", "HTG", "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK",
                    "JMD", "JOD", "JPY", "KES", "KGS", "KHR", "KMF", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP", "LKR",
                    "LRD", "LSL", "LTC", "LYD", "MAD", "MDL", "MKD", "MMK", "MOP", "MUR", "MVR", "MWK", "MXN", "MYR",
                    "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN",
                    "PYG", "QAR", "RON", "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SLL", "SOS",
                    "SRD", "SVC", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS", "UAH", "UGX",
                    "UYU", "UZS", "VND", "XAF", "XAG", "XAU", "XCD", "XLM", "XOF", "XRP", "YER", "ZAR", "ZMW"};
            String insertSql = "INSERT INTO currency (name) VALUES (?)";
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (String currency : currencies) {
                    statement.setString(1, currency);
                    statement.executeUpdate();
                }
            }
            logger.info("Initial currency data inserted successfully");

            // Create a user with username "user", password "1234", and currencies "USD" and "CNY"
            String username = "user";
            String password = "1234";
            String passwordHash = DigestUtils.sha256Hex(password);
            String defaultPairFrom = "USD";
            String defaultPairTo = "CNY";

            String insertUserSql = "INSERT INTO users (username, password_hash, default_pair_from_id, default_pair_to_id) VALUES (?, ?, (SELECT id FROM currency WHERE name = ?), (SELECT id FROM currency WHERE name = ?))";
            try (PreparedStatement statement = connection.prepareStatement(insertUserSql)) {
                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, defaultPairFrom);
                statement.setString(4, defaultPairTo);
                statement.executeUpdate();
            }
            logger.info("User created successfully");

            // Populate conversion history for the user from 01.05.2024 to 24.05.2024
            String[] conversionCurrencies = {"USD", "EUR", "GBP", "JPY", "CNY", "RUB"};
            LocalDate startDate = LocalDate.of(2024, 5, 1);
            LocalDate endDate = LocalDate.of(2024, 5, 24);

            String insertConversionSql = "INSERT INTO conversion_history (user_id, from_currency_id, to_currency_id, amount, rate, created_at) VALUES ((SELECT id FROM users WHERE username = ?), (SELECT id FROM currency WHERE name = ?), (SELECT id FROM currency WHERE name = ?), ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertConversionSql)) {
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    String fromCurrency = conversionCurrencies[random.nextInt(conversionCurrencies.length)];
                    String toCurrency = conversionCurrencies[random.nextInt(conversionCurrencies.length)];
                    double amount = Math.round(random.nextDouble() * 1000) / 100.0;
                    double rate = Math.round(random.nextDouble() * 10) / 100.0;

                    statement.setString(1, username);
                    statement.setString(2, fromCurrency);
                    statement.setString(3, toCurrency);
                    statement.setDouble(4, amount);
                    statement.setDouble(5, rate);
                    statement.setDate(6, Date.valueOf(date));
                    statement.executeUpdate();
                }
            }
            logger.info("Conversion history populated successfully");
        } catch (SQLException e) {
            logger.error("Failed to insert initial data", e);
            throw new RuntimeException("Failed to insert initial data", e);
        }
    }

    @PostConstruct
    public void registerH2Driver() {
        try {
            Class.forName("org.h2.Driver");
            logger.info("H2 Driver registered successfully");
        } catch (ClassNotFoundException e) {
            logger.error("Failed to register H2 driver", e);
            throw new RuntimeException("Failed to register H2 driver", e);
        }
    }
}
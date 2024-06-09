package org.example.config;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Random;

@Configuration
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private final Random random = new Random();

    private final String dataSourceUrl;
    private final String dataSourceUsername;
    private final String dataSourcePassword;
    private final ResourceLoader resourceLoader;

    public DatabaseConfig(String dataSourceUrl, String dataSourceUsername, String dataSourcePassword, ResourceLoader resourceLoader) {
        this.dataSourceUrl = dataSourceUrl;
        this.dataSourceUsername = dataSourceUsername;
        this.dataSourcePassword = dataSourcePassword;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initializeDatabase() {
        try (Connection connection = DriverManager.getConnection(dataSourceUrl, dataSourceUsername, dataSourcePassword)) {
            connection.setAutoCommit(false); // Disable auto-commit mode

            try (Statement statement = connection.createStatement()) {
                // Initialize the database schema
                Resource resource = resourceLoader.getResource("classpath:schema.sql");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    statement.execute(sb.toString());
                    logger.info("Database schema initialized successfully");
                } catch (IOException e) {
                    logger.error("Failed to initialize database schema", e);
                    connection.rollback(); // Rollback the transaction
                    throw new RuntimeException("Failed to initialize database schema", e);
                }

                // Insert initial currency data
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
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (String currency : currencies) {
                        ps.setString(1, currency);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                connection.commit(); // Commit the transaction
                logger.info("Initial currency data inserted successfully");
            } catch (SQLException e) {
                logger.error("Failed to initialize database", e);
                connection.rollback(); // Rollback the transaction
                throw new RuntimeException("Failed to initialize database", e);
            }
        } catch (SQLException e) {
            logger.error("Failed to obtain database connection", e);
            throw new RuntimeException("Failed to obtain database connection", e);
        }
    }

}
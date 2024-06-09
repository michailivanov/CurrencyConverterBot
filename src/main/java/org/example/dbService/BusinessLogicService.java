package org.example.dbService;

import org.example.telegram.SendToUserException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils; // for sha256Hex

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.example.dbService.DatabaseService;
import org.example.telegram.SendToUserException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;

import java.sql.SQLException;

public class BusinessLogicService {
    private final String currenciesRateApiUrl;
    private final DatabaseService databaseService;

    public BusinessLogicService(
            DatabaseService databaseService,
            @Value("${currenciesRateApiUrl}") String currenciesRateApiUrl) {
        this.databaseService = databaseService;
        this.currenciesRateApiUrl = currenciesRateApiUrl;
    }

    public void signUp(String tgUsername, String username, String password, String defaultPairFrom, String defaultPairTo) throws SendToUserException, SQLException {
        // Check that tgUsername is not already logged in
        if (databaseService.getUserIdIfLoggedIn(tgUsername) != null) {
            throw new SendToUserException("You are already logged in!");
        }

        // Check if username already exists
        if (databaseService.getUserIdIfExists(username) != null) {
            throw new SendToUserException("This username already exists!");
        }

        // Create new user
        String passwordHash = DigestUtils.sha256Hex(password);
        Long defaultPairFromId = databaseService.getCurrencyIdIfExists(defaultPairFrom);
        Long defaultPairToId = databaseService.getCurrencyIdIfExists(defaultPairTo);

        databaseService.createUser(username, passwordHash, defaultPairFromId, defaultPairToId);

        // Verify that user created
        Long userId = databaseService.getUserIdIfExists(username);
        if (userId == null) {
            throw new SendToUserException("Internal error: User hasn't created");
        }

        // Log in this user
        databaseService.logInUser(userId, tgUsername);
    }

    public void logIn(String tgUsername, String username, String password) throws SendToUserException, SQLException {
        String passwordHash = DigestUtils.sha256Hex(password);

        // Check that tgUsername is not logged in
        if (databaseService.getUserIdIfLoggedIn(tgUsername) != null) {
            throw new SendToUserException("You are already logged in!");
        }

        // Check if this user exists
        Long userId = databaseService.getUserIdIfExists(username);
        if (userId == null) {
            throw new SendToUserException("This user doesn't exist! You can register this user using /sign_up command.");
        }

        // Get password hash
        String passwordHashFromDB = databaseService.getPasswordHash(userId);

        if (!passwordHash.equals(passwordHashFromDB)) {
            throw new SendToUserException("Incorrect password for user " + username);
        }
        databaseService.logInUser(userId, tgUsername);
    }

    public void logOut(String tgUsername) throws SendToUserException, SQLException {
        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in");
        }
        databaseService.logOutUser(userId, tgUsername);
    }

    public String getHomeCurrency(String tgUsername) throws SendToUserException, SQLException {
        // Implementation for getting home currency
        return null;
    }

    public String getDefaultToCurrency(String tgUsername) throws SendToUserException, SQLException {
        // Implementation for getting default to currency
        return null;
    }

    public String getDefaultPair(String tgUsername) throws SendToUserException, SQLException {
        // Implementation for getting default pair
        return null;
    }

    public void chHomeCurrency(String tgUsername, String currency) throws SendToUserException, SQLException {
        // Implementation for changing home currency
    }

    public Boolean isCurrencyExists(String currency) throws SQLException {
        // Implementation for checking if currency exists
        return null;
    }

    public void chDefaultPair(String tgUsername, String from, String to) throws SendToUserException, SQLException {
        // Implementation for changing default pair
    }

    public String getExchangeRate(String tgUsername, String fromS, String toS, String amountS) throws SendToUserException, SQLException {
        // Implementation for getting exchange rate
        return null;
    }

    public void saveLogToConversionHistory(Long userId, Long fromCurrencyId, Long toCurrencyId, Double amount, Double rate) throws SQLException {
        // Implementation for saving log to conversion history
    }

    public String getHistory(String tgUsername, String dateFrom, String dateTo, String curFrom, String curTo) throws SendToUserException, IllegalArgumentException, SQLException {
        // Implementation for getting history
        return null;
    }

    private List<String> findConversionHistoryByUserIdAndPeriod(Long userId, String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) throws SQLException {
        // Implementation for finding conversion history by user ID and period
        return null;
    }
}
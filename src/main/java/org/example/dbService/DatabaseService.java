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

@Service
public class DatabaseService {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String currenciesRateApiUrl;
    private final Connection connection;
    private Logger logger;

    public DatabaseService(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${currenciesRateApiUrl}") String currenciesRateApiUrl) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.logger = LoggerFactory.getLogger(DatabaseService.class);
        this.currenciesRateApiUrl = currenciesRateApiUrl;
        try {
            this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            logger.error(e.getMessage());
            throw new RuntimeException("Error: cannot connect to dbService", e);
        }
    }

    public String getCurrencyNameById(Long id) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT name FROM currency WHERE id = ?");
        statement.setLong(1, id);

        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("name");
        }
        throw new NoSuchElementException("There is no currency with this id");
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

    public Long getCurrencyIdIfExists(String currencyStr) throws SQLException, SendToUserException {
        if(currencyStr.length() != 3){
            throw new SendToUserException("Currency should contain 3 chars only (For ex. USD)");
        }
        PreparedStatement statement = connection.prepareStatement("SELECT id FROM currency WHERE name = ?");
        statement.setString(1, currencyStr);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getLong("id");
        }
        throw new SendToUserException("No such currency: " + currencyStr);
    }

    public void signUp(String tgUsername, String username, String password, String defaultPairFrom, String defaultPairTo) throws SendToUserException, SQLException {

        // Check that tgUsername is not already loged in
        if (getUserIdIfLoggedIn(tgUsername) != null) {
            throw new SendToUserException("You already logged in!");
        }

        // Check if username already exists
        if (getUserIdIfExists(username) != null) {
            throw new SendToUserException("This username already exists!");
        }

        // Create new user
        String passwordHash = DigestUtils.sha256Hex(password);
        Long defaultPairFromId = getCurrencyIdIfExists(defaultPairFrom);
        Long defaultPairToId = getCurrencyIdIfExists(defaultPairTo);

        PreparedStatement statement1 = connection.prepareStatement("INSERT INTO users (username, password_hash, default_pair_from_id, default_pair_to_id) VALUES (?, ?, ?, ?)");
        statement1.setString(1, username);
        statement1.setString(2, passwordHash);
        statement1.setLong(3, defaultPairFromId);
        statement1.setLong(4, defaultPairToId);
        statement1.executeUpdate();

        // Verify that user created
        Long userId = getUserIdIfExists(username);
        if (userId == null) {
            throw new SendToUserException("Internal error: User hasn't created");
        }

        // log in this user
        PreparedStatement statement2 = connection.prepareStatement("INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)");
        statement2.setLong(1, userId);
        statement2.setString(2, tgUsername);
        statement2.setBoolean(3, true);
        statement2.executeUpdate();
    }

    public void logIn(String tgUsername, String username, String password) throws SendToUserException, SQLException {
        String passwordHash = DigestUtils.sha256Hex(password);

        // Check that tgUsername is not loged in
        if (getUserIdIfLoggedIn(tgUsername) != null) {
            throw new SendToUserException("You already logged in!");
        }

        // Check if this user exists
        Long userId = getUserIdIfExists(username);
        if (userId == null)
        {
            throw new SendToUserException("This user doesn't exist! You can register this user using /sign_up command.");
        }

        // Get password hash
        PreparedStatement statement1 = connection.prepareStatement("SELECT password_hash FROM users WHERE id = ?");
        statement1.setLong(1, userId);
        ResultSet resultSet = statement1.executeQuery();
        String passwordHashFromDB = "";
        if (resultSet.next()) {
            passwordHashFromDB = resultSet.getString("password_hash");
        } else {
            throw new RuntimeException("Logic error");
        }

        if (!passwordHash.equals(passwordHashFromDB)) {
            throw new SendToUserException("Incorrect password for user " + username);
        }
        PreparedStatement statement = connection.prepareStatement("INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)");
        statement.setLong(1, userId);
        statement.setString(2, tgUsername);
        statement.setBoolean(3, true);
        statement.executeUpdate();
    }

    public void logOut(String tgUsername) throws SendToUserException, SQLException {
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in");
        }
        PreparedStatement statement = connection.prepareStatement("INSERT INTO log_in_out (user_id, tg_username, is_in) VALUES (?, ?, ?)");
        statement.setLong(1, userId);
        statement.setString(2, tgUsername);
        statement.setBoolean(3, false);
        statement.executeUpdate();
    }

    public String getHomeCurrency(String tgUsername) throws SendToUserException, SQLException {

        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        // Get default_pair_from_id of this user
        PreparedStatement statement = connection.prepareStatement("SELECT currency.name FROM users JOIN currency ON default_pair_from_id = currency.id where users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("name");
        }

        throw new RuntimeException("Error: user cannot be without default_pair_from_id, code problem");
    }

    public String getDefaultToCurrency(String tgUsername) throws SendToUserException, SQLException {
        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        // Get default_pair_to_id of this user
        PreparedStatement statement = connection.prepareStatement("SELECT currency.name FROM users JOIN currency ON default_pair_to_id = currency.id where users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("name");
        }
        throw new RuntimeException("Error: user cannot be without default_pair_to_id, code problem");
    }

    public String getDefaultPair(String tgUsername) throws SendToUserException, SQLException {
        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        // Get both in pair
        PreparedStatement statement = connection.prepareStatement("SELECT c1.name AS currencyFrom, c2.name AS currencyTo " +
                "FROM users JOIN currency AS c1 ON default_pair_from_id = c1.id JOIN currency AS c2 ON default_pair_to_id = c2.id where users.id = ?");
        statement.setLong(1, userId);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("currencyFrom") + "-" + resultSet.getString("currencyTo");
        }

        throw new RuntimeException("Error: user must have currency pair!");
    }

    public void chHomeCurrency(String tgUsername, String currency) throws SendToUserException, SQLException {
        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        // Check that valid string
        if(currency.length() != 3){
            throw new SendToUserException("Currency should contain 3 chars (For ex. USD)");
        }

        // Check that this currency exists
        if (!isCurrencyExists(currency)) {
            throw new SendToUserException("Currency '" + currency + "' does not exist!");
        }

        // Update
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

    public void chDefaultPair(String tgUsername, String from, String to) throws SendToUserException, SQLException {
        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        // Check that valid string
        if(from.length() != 3 || to.length() != 3){
            throw new SendToUserException("Currency should contain 3 chars (For ex. USD)");
        }

        // Check that this currency exists
        if (!isCurrencyExists(from)) {
            throw new SendToUserException("Currency '" + from + "' does not exist!");
        }

        if (!isCurrencyExists(to)) {
            throw new SendToUserException("Currency '" + to + "' does not exist!");
        }

        // Update
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

    public String getExchangeRate(String tgUsername, String fromS, String toS, String amountS) throws SendToUserException, SQLException {
        // Check that tgUsername is logged in and get userId
        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        String from = fromS;
        String to = toS;

        Double amount;
        if (amountS == null) {
            amount = 1.;
        } else {
            amount = Double.valueOf(amountS);
        }
        if(from == null || to == null) {
            // Get both in pair
            PreparedStatement statement = connection.prepareStatement("SELECT c1.name AS currencyFrom, c2.name AS currencyTo " +
                    "FROM users JOIN currency AS c1 ON default_pair_from_id = c1.id JOIN currency AS c2 ON default_pair_to_id = c2.id where users.id = ?");
            statement.setLong(1, userId);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();

            if (from == null && to != null) {
                from = resultSet.getString("currencyFrom");
            } else {
                from = resultSet.getString("currencyFrom");
                to = resultSet.getString("currencyTo");
            }
        }

        // Capitalise
        from = from.toUpperCase();
        to = to.toUpperCase();

        // Check that this currency exists
        if (!isCurrencyExists(from)) {
            throw new SendToUserException("Currency '" + from + "' does not exist!");
        }
        if (!isCurrencyExists(to)) {
            throw new SendToUserException("Currency '" + to + "' does not exist!");
        }
        /** FOR RELEASE: (limited)
         * */
        /*
            String response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(currenciesRateApiUrl))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            JSONObject jsonObject = new JSONObject(response);
         */

        /** FOR DEBUG:
        * */
        JSONObject jsonObject = new JSONObject("{\"valid\":true,\"updated\":1716386402,\"base\":\"USD\",\"rates\":{\"AED\":3.673,\"AFN\":71.9014,\"ALL\":92.64645,\"AMD\":388.37,\"ANG\":1.803234,\"AOA\":850.5,\"ARS\":889.7541,\"AUD\":1.503127,\"AWG\":1.8,\"AZN\":1.7,\"BAM\":1.805659,\"BBD\":2.020191,\"BCH\":0.002007064,\"BDT\":117.2121,\"BGN\":1.8048,\"BHD\":0.3768049,\"BIF\":2872.046,\"BMD\":1,\"BND\":1.349398,\"BOB\":6.914157,\"BRL\":5.1465,\"BSD\":1.000586,\"BTC\":0.00001438353,\"BTG\":0.027824923,\"BWP\":13.52075,\"BZD\":2.016821,\"CAD\":1.36683,\"CDF\":2802,\"CHF\":0.91353,\"CLP\":896.15,\"CNH\":7.251865,\"CNY\":7.2025,\"COP\":3821.98,\"CRC\":513.0382,\"CUC\":1,\"CUP\":24.01329,\"CVE\":101.7998,\"CZK\":22.8157,\"DASH\":0.0325,\"DJF\":178.1446,\"DKK\":6.8863,\"DOP\":58.71763,\"DZD\":134.404,\"EGP\":46.74993,\"EOS\":1.179532,\"ETB\":57.48398,\"ETH\":0.0002718048,\"EUR\":0.92288,\"FJD\":2.261,\"GBP\":0.78485,\"GEL\":2.73,\"GHS\":14.50874,\"GIP\":0.78485,\"GMD\":67.775,\"GNF\":8601.644,\"GTQ\":7.774439,\"GYD\":209.331,\"HKD\":7.80635,\"HNL\":24.72788,\"HRK\":6.8253273,\"HTG\":133.3241,\"HUF\":357.438,\"IDR\":16032.9,\"ILS\":3.6779,\"INR\":83.26845,\"IQD\":1310.714,\"IRR\":42075,\"ISK\":138.51,\"JMD\":156.1003,\"JOD\":0.7089,\"JPY\":156.421,\"KES\":132,\"KGS\":88.021,\"KHR\":4076.998,\"KMF\":453.75,\"KRW\":1366.455,\"KWD\":0.30692,\"KYD\":0.8338565,\"KZT\":443.0073,\"LAK\":21373.05,\"LBP\":89601.44,\"LKR\":300.1062,\"LRD\":193.55,\"LSL\":18.09,\"LTC\":0.0116117,\"LYD\":4.842358,\"MAD\":9.906938,\"MDL\":17.6798,\"MKD\":56.84827,\"MMK\":2101.167,\"MOP\":8.044887,\"MUR\":46.16,\"MVR\":15.46,\"MWK\":1734.823,\"MXN\":16.6507,\"MYR\":4.6925,\"MZN\":63.5,\"NAD\":18.09,\"NGN\":1443.9,\"NIO\":36.82726,\"NOK\":10.67478,\"NPR\":133.2881,\"NZD\":1.634475,\"OMR\":0.3848351,\"PAB\":1.000609,\"PEN\":3.736181,\"PGK\":3.888658,\"PHP\":58.0815,\"PKR\":278.5074,\"PLN\":3.938657,\"PYG\":7528.043,\"QAR\":3.6415,\"RON\":4.5915,\"RSD\":108.106,\"RUB\":90.33,\"RWF\":1315.687,\"SAR\":3.750433,\"SBD\":8.511255,\"SCR\":13.73689,\"SDG\":601,\"SEK\":10.72004,\"SGD\":1.349045,\"SLL\":19750,\"SOS\":571,\"SRD\":32.3565,\"SVC\":8.754899,\"SZL\":18.19113,\"THB\":36.391,\"TJS\":10.79097,\"TMT\":3.51,\"TND\":3.1125,\"TOP\":2.36205,\"TRY\":32.18232,\"TTD\":6.790443,\"TWD\":32.276,\"TZS\":2595,\"UAH\":39.8099,\"UGX\":3813.823,\"USD\":1,\"UYU\":38.44343,\"UZS\":12715.69,\"VND\":25465,\"XAF\":605.6013,\"XAG\":0.031598971137499765,\"XAU\":0.0004146654738123203,\"XCD\":2.70255,\"XLM\":9.012988,\"XOF\":605.5985,\"XRP\":1.886632,\"YER\":249.9,\"ZAR\":18.16636,\"ZMW\":26.08989}}");

        JSONObject ratesObject = jsonObject.getJSONObject("rates");
        Double rate = ratesObject.getDouble(to) / ratesObject.getDouble(from);

        saveLogToConversionHistory(userId, getCurrencyIdByName(from), getCurrencyIdByName(to), amount, rate);

        // Rate with dollar: 1 dollar: ratesObject.getDouble(currency)
        return String.format(Locale.US, "%.2f", amount * rate);

    }

    public void saveLogToConversionHistory(Long userId, Long fromCurrencyId,
                                           Long toCurrencyId, Double amount, Double rate) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO conversion_history " +
                "(user_id, from_currency_id, to_currency_id, amount, rate) VALUES (?, ?, ?, ?, ?)");
                statement.setLong(1, userId);
                statement.setLong(2, fromCurrencyId);
                statement.setLong(3, toCurrencyId);
                statement.setDouble(4, amount);
                statement.setDouble(5, rate);
                statement.executeUpdate();

    }

    public String getHistory(String tgUsername, String dateFrom, String dateTo, String curFrom, String curTo) throws SendToUserException, IllegalArgumentException, SQLException {
        String res;

        Long userId = getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            throw new SendToUserException("You are not logged in!");
        }

        LocalDate startDate;
        LocalDate endDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        if (dateFrom == null && dateTo == null) {
            startDate = LocalDate.now();
            endDate = LocalDate.now();
        } else if (dateFrom != null && dateTo != null) {
            startDate = LocalDate.parse(dateFrom, formatter);
            endDate = LocalDate.parse(dateTo, formatter);
        } else {
            throw new IllegalArgumentException("dateFrom and dateTo both should be non null or null");
        }


        if (startDate.isBefore(endDate) || startDate.equals(endDate)) {
            List<String> history = findConversionHistoryByUserIdAndPeriod(userId, curFrom, curTo, startDate, endDate);

            if (history.isEmpty()) {
                res = "No conversion history found for the specified period and currencies.";
            } else {
                if (startDate.equals(endDate)) {
                    res = String.format("Today's conversion history (%s)", endDate.format(formatter));
                } else {
                    res = String.format("Conversion history %s-%s", dateFrom, dateTo);
                }

                if (curFrom == null && curTo == null) {
                    res += "\n";
                } else if (curTo == null) {
                    res += " where " + curFrom + " appears:\n";
                } else if (curFrom != null && curTo != null) {
                    res += " with " + curFrom + "-" + curTo + " :\n";
                } else {
                    throw new RuntimeException("Error: logic error");
                }
                res += "-----------------------------------------------------------------------\n";

                for (String conversionHistory : history) {
                    res += conversionHistory + "\n";
                }
            }
        } else {
            res = "The period is invalid the start date cannot be after the end date!";
        }

        return res;
    }

    private List<String> findConversionHistoryByUserIdAndPeriod(Long userId, String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) throws SQLException {
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
            LocalDateTime createdAt = resultSet.getTimestamp("created_at").toLocalDateTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            conversionHistories.add(String.format(Locale.US, "%s: %s-%s amount: %.2f, rate: %.2f",createdAt.format(formatter), fromCurrencyName, toCurrencyName, amount, rate));
        }
        return conversionHistories;
    }
}

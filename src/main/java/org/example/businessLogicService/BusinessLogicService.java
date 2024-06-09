package org.example.businessLogicService;

import org.example.telegram.SendToUserException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.apache.commons.codec.digest.DigestUtils; // for sha256Hex

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.example.dbService.DatabaseService;

import java.sql.SQLException;

public class BusinessLogicService {
    private final String currenciesRateApiUrl;
    private final DatabaseService databaseService;
    private final Logger logger = LoggerFactory.getLogger(BusinessLogicService.class);

    public BusinessLogicService(
            DatabaseService databaseService,
            @Value("${currenciesRateApiUrl}") String currenciesRateApiUrl) {
        this.databaseService = databaseService;
        this.currenciesRateApiUrl = currenciesRateApiUrl;
    }

    public void signUp(String tgUsername, String username, String password, String defaultPairFrom, String defaultPairTo) throws SendToUserException, SQLException {
        logger.info("Signing up user with tgUsername: {}, username: {}, defaultPairFrom: {}, defaultPairTo: {}", tgUsername, username, defaultPairFrom, defaultPairTo);

        // Check that tgUsername is not already logged in
        if (databaseService.getUserIdIfLoggedIn(tgUsername) != null) {
            logger.info("FAILED(user already logged in): Signing up user with tgUsername: {}, username: {}, defaultPairFrom: {}, defaultPairTo: {}", tgUsername, username, defaultPairFrom, defaultPairTo);
            throw new SendToUserException("You are already logged in!");
        }

        // Check if username already exists
        if (databaseService.getUserIdIfExists(username) != null) {
            logger.info("FAILED(username is already exists): Signing up user with tgUsername: {}, username: {}, defaultPairFrom: {}, defaultPairTo: {}", tgUsername, username, defaultPairFrom, defaultPairTo);
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
            logger.error("FAILED(internalError): Signing up user with tgUsername: {}, username: {}, defaultPairFrom: {}, defaultPairTo: {}", tgUsername, username, defaultPairFrom, defaultPairTo);
            throw new SendToUserException("Internal error: User hasn't created");
        }

        // Log in this user
        databaseService.logInUser(userId, tgUsername);

        logger.info("SUCCESS: User {} successfully signed up", username);
    }

    public void logIn(String tgUsername, String username, String password) throws SendToUserException, SQLException {
        logger.info("Logging in user with tgUsername: {}, username: {}", tgUsername, username);

        String passwordHash = DigestUtils.sha256Hex(password);

        // Check that tgUsername is not logged in
        if (databaseService.getUserIdIfLoggedIn(tgUsername) != null) {
            logger.info("FAILED(user already logged in): Logging in user with tgUsername: {}, username: {}", tgUsername, username);
            throw new SendToUserException("You are already logged in!");
        }

        // Check if this user exists
        Long userId = databaseService.getUserIdIfExists(username);
        if (userId == null) {
            logger.info("FAILED(this account doesn't exist): Logging in user with tgUsername: {}, username: {}", tgUsername, username);
            throw new SendToUserException("This user doesn't exist! You can register this user using /sign_up command.");
        }

        // Get password hash
        String passwordHashFromDB = databaseService.getPasswordHash(userId);

        if (!passwordHash.equals(passwordHashFromDB)) {
            logger.info("FAILED(incorrect password is typed): Logging in user with tgUsername: {}, username: {}", tgUsername, username);
            throw new SendToUserException("Incorrect password for user " + username);
        }
        databaseService.logInUser(userId, tgUsername);
        logger.info("SUCCESS: User {} successfully logged in", username);
    }

    public void logOut(String tgUsername) throws SendToUserException, SQLException {
        logger.info("Logging out user with tgUsername: {}", tgUsername);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Logging out user with tgUsername: {}", tgUsername);
            throw new SendToUserException("You are not logged in");
        }
        databaseService.logOutUser(userId, tgUsername);

        logger.info("SUCCESS: User with tgUsername '{}' successfully logged out", tgUsername);
    }

    public String getHomeCurrency(String tgUsername) throws SendToUserException, SQLException {
        logger.info("Getting home currency for user with tgUsername: {}", tgUsername);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user are not logged in): Getting home currency for user with tgUsername: {}", tgUsername);
            throw new SendToUserException("You are not logged in!");
        }

        logger.info("SUCCESS: Getting home currency for user with tgUsername: {}", tgUsername);
        return databaseService.getHomeCurrency(userId);
    }

    public String getDefaultToCurrency(String tgUsername) throws SendToUserException, SQLException {
        logger.info("Getting default 'to' currency for user with tgUsername: {}", tgUsername);
        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Getting default 'to' currency for user with tgUsername: {}", tgUsername);
            throw new SendToUserException("You are not logged in!");
        }
        logger.info("SUCCESS: Getting default 'to' currency for user with tgUsername: {}", tgUsername);
        return databaseService.getDefaultToCurrency(userId);
    }

    public String getDefaultPair(String tgUsername) throws SendToUserException, SQLException {
        logger.info("Getting default currency pair for user with tgUsername: {}", tgUsername);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Getting default currency pair for user with tgUsername: {}", tgUsername);
            throw new SendToUserException("You are not logged in!");
        }

        logger.info("SUCCESS: Getting default currency pair for user with tgUsername: {}", tgUsername);
        return databaseService.getDefaultPair(userId);
    }

    public void chHomeCurrency(String tgUsername, String currency) throws SendToUserException, SQLException {
        logger.info("Changing home currency for user with tgUsername: {} to {}", tgUsername, currency);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Changing home currency for user with tgUsername: {} to {}", tgUsername, currency);
            throw new SendToUserException("You are not logged in!");
        }

        if (currency.length() != 3) {
            logger.info("FAILED(user has inputted a currency with more than 3 chars): Changing home currency for user with tgUsername: {} to {}", tgUsername, currency);
            throw new SendToUserException("Currency should contain 3 chars (For ex. USD)");
        }

        if (!databaseService.isCurrencyExists(currency)) {
            logger.info("FAILED(user has inputted currency name that doesn't exist): Changing home currency for user with tgUsername: {} to {}", tgUsername, currency);
            throw new SendToUserException("Currency '" + currency + "' does not exist!");
        }

        databaseService.changeHomeCurrency(userId, currency);

        logger.info("SUCCESS: Home currency for user with tgUsername: {} successfully changed to {}", tgUsername, currency);
    }

    public void chDefaultPair(String tgUsername, String from, String to) throws SendToUserException, SQLException {
        logger.info("Changing default currency pair for user with tgUsername: {} to {}-{}", tgUsername, from, to);

        // Check that tgUsername is logged in and get userId
        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Changing default currency pair for user with tgUsername: {} to {}-{}", tgUsername, from, to);
            throw new SendToUserException("You are not logged in!");
        }

        // Check that valid string
        if (from.length() != 3 || to.length() != 3) {
            logger.info("FAILED(user has inputted a currency with more than 3 chars): Changing default currency pair for user with tgUsername: {} to {}-{}", tgUsername, from, to);
            throw new SendToUserException("Currency should contain 3 chars (For ex. USD)");
        }

        if (!databaseService.isCurrencyExists(from)) {
            logger.info("FAILED(user has inputted currency name (from) that doesn't exist): Changing default currency pair for user with tgUsername: {} to {}-{}", tgUsername, from, to);
            throw new SendToUserException("Currency '" + from + "' does not exist!");
        }

        if (!databaseService.isCurrencyExists(to)) {
            logger.info("FAILED(user has inputted currency name (to) that doesn't exist): Changing default currency pair for user with tgUsername: {} to {}-{}", tgUsername, from, to);
            throw new SendToUserException("Currency '" + to + "' does not exist!");
        }

        databaseService.changeDefaultPair(userId, from, to);
        logger.info("SUCCESS: Default currency pair for user with tgUsername: {} successfully changed to {}-{}", tgUsername, from, to);
    }

    public String getExchangeRate(String tgUsername, String fromS, String toS, String amountS) throws SendToUserException, SQLException {
        logger.info("Getting exchange rate for user with tgUsername: {}, from: {}, to: {}, amount: {}", tgUsername, fromS, toS, amountS);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Getting exchange rate for user with tgUsername: {}, from: {}, to: {}, amount: {}", tgUsername, fromS, toS, amountS);
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
        if (from == null || to == null) {
            String defaultPair = databaseService.getDefaultPair(userId);
            String[] parts = defaultPair.split("-");
            if (from == null && to != null) {
                from = parts[0];
            } else {
                from = parts[0];
                to = parts[1];
            }
        }

        from = from.toUpperCase();
        to = to.toUpperCase();

        if (!databaseService.isCurrencyExists(from)) {
            logger.info("FAILED(user has inputted currency name (from) that doesn't exist): Getting exchange rate for user with tgUsername: {}, from: {}, to: {}, amount: {}", tgUsername, fromS, toS, amountS);
            throw new SendToUserException("Currency '" + from + "' does not exist!");
        }
        if (!databaseService.isCurrencyExists(to)) {
            logger.info("FAILED(user has inputted currency name (to) that doesn't exist): Getting exchange rate for user with tgUsername: {}, from: {}, to: {}, amount: {}", tgUsername, fromS, toS, amountS);
            throw new SendToUserException("Currency '" + to + "' does not exist!");
        }

        // FOR RELEASE: (limited)
        /*
            String response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(currenciesRateApiUrl))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            JSONObject jsonObject = new JSONObject(response);
         */

        // Fetch exchange rate from API (or use a mock response for demonstration purposes)
        JSONObject jsonObject = new JSONObject("{\"valid\":true,\"updated\":1716386402,\"base\":\"USD\",\"rates\":{\"AED\":3.673,\"AFN\":71.9014,\"ALL\":92.64645,\"AMD\":388.37,\"ANG\":1.803234,\"AOA\":850.5,\"ARS\":889.7541,\"AUD\":1.503127,\"AWG\":1.8,\"AZN\":1.7,\"BAM\":1.805659,\"BBD\":2.020191,\"BCH\":0.002007064,\"BDT\":117.2121,\"BGN\":1.8048,\"BHD\":0.3768049,\"BIF\":2872.046,\"BMD\":1,\"BND\":1.349398,\"BOB\":6.914157,\"BRL\":5.1465,\"BSD\":1.000586,\"BTC\":0.00001438353,\"BTG\":0.027824923,\"BWP\":13.52075,\"BZD\":2.016821,\"CAD\":1.36683,\"CDF\":2802,\"CHF\":0.91353,\"CLP\":896.15,\"CNH\":7.251865,\"CNY\":7.2025,\"COP\":3821.98,\"CRC\":513.0382,\"CUC\":1,\"CUP\":24.01329,\"CVE\":101.7998,\"CZK\":22.8157,\"DASH\":0.0325,\"DJF\":178.1446,\"DKK\":6.8863,\"DOP\":58.71763,\"DZD\":134.404,\"EGP\":46.74993,\"EOS\":1.179532,\"ETB\":57.48398,\"ETH\":0.0002718048,\"EUR\":0.92288,\"FJD\":2.261,\"GBP\":0.78485,\"GEL\":2.73,\"GHS\":14.50874,\"GIP\":0.78485,\"GMD\":67.775,\"GNF\":8601.644,\"GTQ\":7.774439,\"GYD\":209.331,\"HKD\":7.80635,\"HNL\":24.72788,\"HRK\":6.8253273,\"HTG\":133.3241,\"HUF\":357.438,\"IDR\":16032.9,\"ILS\":3.6779,\"INR\":83.26845,\"IQD\":1310.714,\"IRR\":42075,\"ISK\":138.51,\"JMD\":156.1003,\"JOD\":0.7089,\"JPY\":156.421,\"KES\":132,\"KGS\":88.021,\"KHR\":4076.998,\"KMF\":453.75,\"KRW\":1366.455,\"KWD\":0.30692,\"KYD\":0.8338565,\"KZT\":443.0073,\"LAK\":21373.05,\"LBP\":89601.44,\"LKR\":300.1062,\"LRD\":193.55,\"LSL\":18.09,\"LTC\":0.0116117,\"LYD\":4.842358,\"MAD\":9.906938,\"MDL\":17.6798,\"MKD\":56.84827,\"MMK\":2101.167,\"MOP\":8.044887,\"MUR\":46.16,\"MVR\":15.46,\"MWK\":1734.823,\"MXN\":16.6507,\"MYR\":4.6925,\"MZN\":63.5,\"NAD\":18.09,\"NGN\":1443.9,\"NIO\":36.82726,\"NOK\":10.67478,\"NPR\":133.2881,\"NZD\":1.634475,\"OMR\":0.3848351,\"PAB\":1.000609,\"PEN\":3.736181,\"PGK\":3.888658,\"PHP\":58.0815,\"PKR\":278.5074,\"PLN\":3.938657,\"PYG\":7528.043,\"QAR\":3.6415,\"RON\":4.5915,\"RSD\":108.106,\"RUB\":90.33,\"RWF\":1315.687,\"SAR\":3.750433,\"SBD\":8.511255,\"SCR\":13.73689,\"SDG\":601,\"SEK\":10.72004,\"SGD\":1.349045,\"SLL\":19750,\"SOS\":571,\"SRD\":32.3565,\"SVC\":8.754899,\"SZL\":18.19113,\"THB\":36.391,\"TJS\":10.79097,\"TMT\":3.51,\"TND\":3.1125,\"TOP\":2.36205,\"TRY\":32.18232,\"TTD\":6.790443,\"TWD\":32.276,\"TZS\":2595,\"UAH\":39.8099,\"UGX\":3813.823,\"USD\":1,\"UYU\":38.44343,\"UZS\":12715.69,\"VND\":25465,\"XAF\":605.6013,\"XAG\":0.031598971137499765,\"XAU\":0.0004146654738123203,\"XCD\":2.70255,\"XLM\":9.012988,\"XOF\":605.5985,\"XRP\":1.886632,\"YER\":249.9,\"ZAR\":18.16636,\"ZMW\":26.08989}}");

        JSONObject ratesObject = jsonObject.getJSONObject("rates");
        Double rate = ratesObject.getDouble(to) / ratesObject.getDouble(from);

        databaseService.saveLogToConversionHistory(userId, databaseService.getCurrencyIdByName(from), databaseService.getCurrencyIdByName(to), amount, rate);
        logger.info("SUCCESS: Getting exchange rate for user with tgUsername: {}, from: {}, to: {}, amount: {}", tgUsername, fromS, toS, amountS);
        return String.format(Locale.US, "%.2f", amount * rate);
    }

    public void saveLogToConversionHistory(Long userId, Long fromCurrencyId, Long toCurrencyId, Double amount, Double rate) throws SQLException {
        databaseService.saveLogToConversionHistory(userId, fromCurrencyId, toCurrencyId, amount, rate);
    }

    public String getHistory(String tgUsername, String dateFrom, String dateTo, String curFrom, String curTo) throws SendToUserException, IllegalArgumentException, SQLException {
        logger.info("Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);

        Long userId = databaseService.getUserIdIfLoggedIn(tgUsername);
        if (userId == null) {
            logger.info("FAILED(user is not logged in): Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);

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
            logger.error("FAILED(dateFrom and dateTo should be both non null or both null: Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);
            throw new IllegalArgumentException("dateFrom and dateTo both should be both non null or both null");
        }

        if (startDate.isBefore(endDate) || startDate.equals(endDate)) {
            List<String> history = findConversionHistoryByUserIdAndPeriod(userId, curFrom, curTo, startDate, endDate);

            if (history.isEmpty()) {
                return "No conversion history found for the specified period and currencies.";
            } else {
                StringBuilder sb = new StringBuilder();
                if (startDate.equals(endDate)) {
                    sb.append(String.format("Today's conversion history (%s)", endDate.format(formatter)));
                } else {
                    sb.append(String.format("Conversion history %s-%s", dateFrom, dateTo));
                }

                if (curFrom == null && curTo == null) {
                    sb.append("\n");
                } else if (curTo == null) {
                    sb.append(" where ").append(curFrom).append(" appears:\n");
                } else if (curFrom != null && curTo != null) {
                    sb.append(" with ").append(curFrom).append("-").append(curTo).append(" :\n");
                } else {
                    logger.error("FAILED(logic error): Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);
                    throw new RuntimeException("Error: logic error");
                }
                sb.append("-----------------------------------------------------------------------\n");

                for (String conversionHistory : history) {
                    sb.append(conversionHistory).append("\n");
                }
                logger.info("SUCCESS: Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);
                return sb.toString();
            }
        } else {
            logger.info("FAILED(start date cannot be after the end date): Getting conversion history for user with tgUsername: {}, dateFrom: {}, dateTo: {}, curFrom: {}, curTo: {}", tgUsername, dateFrom, dateTo, curFrom, curTo);
            return "The period is invalid the start date cannot be after the end date!";
        }
    }

    private List<String> findConversionHistoryByUserIdAndPeriod(Long userId, String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) throws SQLException {
        return databaseService.findConversionHistoryByUserIdAndPeriod(userId, fromCurrency, toCurrency, startDate, endDate);
    }
}
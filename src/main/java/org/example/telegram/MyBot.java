package org.example.telegram;

import org.example.util.InputValidator;
import org.example.dbService.BusinessLogicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MyBot extends TelegramWebhookBot {
    private String botToken;
    private String botUsername;
    private final BusinessLogicService businessLogicService;
    private Map<String, String> commandUsageMap;
    private Map<String, String> commandInfo;
    private Logger logger;

    public MyBot(String botToken, String botUsername, BusinessLogicService businessLogicService) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.businessLogicService = businessLogicService;
        this.logger = LoggerFactory.getLogger(MyBot.class);

        commandUsageMap = new HashMap<>();
        commandInfo = new HashMap<>();

        commandInfo.put("/start", "/start");
        commandInfo.put("/help", "/help");
        commandUsageMap.put("/signup", "/signup <username> <password> <fromCurrency> <toCurrency>");
        commandUsageMap.put("/login", "/login <username> <password>");
        commandUsageMap.put("/logout", "/logout");
        commandUsageMap.put("/home", "/home");
        commandUsageMap.put("/pair", "/pair");
        commandUsageMap.put("/chhome", "/chhome <currency>");
        commandUsageMap.put("/chpair", "/chpair <fromCurrency> <toCurrency>");
        commandUsageMap.put("/rate", "/rate <fromCurrency> (optional) <toCurrency> (optional) <amount> (optional)");
        commandUsageMap.put("/history", "/history <dateFrom> (optional 1) <dateTo> (optional 1) <currency1> (optional 2) <currency2> (optional 3)");

        commandInfo.put("/start", "Say hi to the bot.");
        commandInfo.put("/help", "Get help and information about available commands");
        commandInfo.put("/signup", "Register a new account");
        commandInfo.put("/login", "Log in to an account");
        commandInfo.put("/logout", "Log out of the current account");
        commandInfo.put("/home", "Display the current home currency");
        commandInfo.put("/chhome", "Update the home currency to a different one");
        commandInfo.put("/pair", "Show the default currency pair for exchange rate queries");
        commandInfo.put("/chpair", "Modify the default currency pair for exchange rate queries");
        commandInfo.put("/rate", "Fetch the current exchange rate for a specified currency pair (optional) and amount (optional)");
        commandInfo.put("/history", "Retrieve exchange rate requests history for a specified period (optional) and a currency/pair (optional)");
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotPath() {
        return "/";
    }

    @Override
    public SendMessage onWebhookUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String tgUsername = update.getMessage().getFrom().getUserName();
            String tgFirstName = update.getMessage().getFrom().getFirstName();
            String userInput = update.getMessage().getText();
            String[] inputParts = userInput.split(" ");
            String command = inputParts[0];
            String answerMessageText = "ERROR";
            try {
                logger.info("User {} sended '{}'", tgUsername, userInput);
                switch (command.toLowerCase()) {
                    case "/start":
                        answerMessageText = String.format("Hi %s, I'm a currency converter bot!\nUse /help, to see what I can do!", tgFirstName);
                        break;
                    case "/help":
                        answerMessageText = handleHelp(inputParts);
                        break;
                    case "/signup":
                        answerMessageText = handleSignUp(tgUsername, inputParts);
                        break;
                    case "/login":
                        answerMessageText = handleLogin(tgUsername, inputParts);
                        break;
                    case "/logout":
                        answerMessageText = handleLogout(tgUsername);
                        break;
                    case "/home":
                        answerMessageText = getHomeCurrencyMessage(tgUsername, inputParts);
                        break;
                    case "/pair":
                        answerMessageText = getDefaultPairMessage(tgUsername, inputParts);
                        break;
                    case "/chhome":
                        answerMessageText = handleChangeHomeCurrency(tgUsername, inputParts);
                        break;
                    case "/chpair":
                        answerMessageText = handleChangeDefaultPair(tgUsername, inputParts);
                        break;
                    case "/rate":
                        answerMessageText = handleExchangeRate(tgUsername, inputParts);
                        break;
                    case "/history":
                        answerMessageText = handleHistory(tgUsername, inputParts);
                        break;
                    default:
                        answerMessageText = "Unknown command!";
                }
            } catch (SendToUserException e) {
                answerMessageText = e.getMessage();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            } catch (IllegalArgumentException e) {
                answerMessageText = "Date should be in this format: dd.MM.yyyy";
            }

            logger.info("Answer to user {} for his message {}: {}", tgUsername, userInput, answerMessageText);

            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId().toString());
            message.setText(answerMessageText);
            return message;
        }
        return null;
    }

    public String handleHelp(String[] inputParts) {
        if (inputParts.length == 1) {
            StringBuilder res = new StringBuilder();
            res.append("Available commands:\n\n");
            for (Map.Entry<String, String> entry : commandUsageMap.entrySet()) {
                String command = entry.getKey();
                String usage = entry.getValue();
                String info = commandInfo.get(command);
                res.append(usage).append("\n➡\uFE0F ").append(info).append("\n\n");
            }
            return res.toString();
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleSignUp(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if (inputParts.length == 5) {
            String username = inputParts[1];
            String password = inputParts[2];
            String defaultPairFrom = inputParts[3];
            String defaultPairTo = inputParts[4];
            businessLogicService.signUp(tgUsername, username, password, defaultPairFrom, defaultPairTo);
            return "Sign up successful!";
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleLogin(String tgUsername, String[] inputParts) throws SQLException, SendToUserException {
        if (inputParts.length == 3) {
            String username = inputParts[1];
            String password = inputParts[2];
            businessLogicService.logIn(tgUsername, username, password);
            return "Log in successful!";
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleLogout(String tgUsername) throws SendToUserException, SQLException {
        businessLogicService.logOut(tgUsername);
        return "Log out successful!";
    }

    private String getHomeCurrencyMessage(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if(inputParts.length == 1) {
            String homeCurrency = businessLogicService.getHomeCurrency(tgUsername);
            return "Your home currency is " + homeCurrency;
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String getDefaultPairMessage(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if(inputParts.length == 1) {
            String currencyPair = businessLogicService.getDefaultPair(tgUsername);
            return "Your currency pair is " + currencyPair;
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleChangeHomeCurrency(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if(inputParts.length == 2) {
            String prevCur = businessLogicService.getHomeCurrency(tgUsername);
            businessLogicService.chHomeCurrency(tgUsername, inputParts[1]);
            return "Your home currency has been successfully changed: " + prevCur + " -> " + businessLogicService.getHomeCurrency(tgUsername);
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleChangeDefaultPair(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if(inputParts.length == 3) {
            businessLogicService.chDefaultPair(tgUsername, inputParts[1], inputParts[2]);
            return "Your default pair has been successfully changed";
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleExchangeRate(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if (inputParts.length == 4) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, inputParts[1], inputParts[2], inputParts[3]);
            return inputParts[3] + " " + inputParts[1] + " = " + convResult + " " + inputParts[2];

        } else if (inputParts.length == 3 && InputValidator.isNumeric(inputParts[2])) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, null, inputParts[1], inputParts[2]);
            return inputParts[2] + " " + businessLogicService.getHomeCurrency(tgUsername) + " = " + convResult + " " + inputParts[1];

        } else if (inputParts.length == 3 && InputValidator.isCurrency(inputParts[2])) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, inputParts[1], inputParts[2], null);
            return "1.00 " + inputParts[1] + " = " + convResult + " " + inputParts[2];

        } else if (inputParts.length == 2 && InputValidator.isNumeric(inputParts[1])) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, null, null, inputParts[1]);
            return inputParts[1] + " " + businessLogicService.getHomeCurrency(tgUsername) + " = " + convResult + " " + businessLogicService.getDefaultToCurrency(tgUsername);
        } else if (inputParts.length == 2 && InputValidator.isCurrency(inputParts[1])) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, null, inputParts[1], null);
            return "1.00 " + businessLogicService.getHomeCurrency(tgUsername) + " = " + convResult + " " + inputParts[1];
        } else if (inputParts.length == 1) {
            String convResult = businessLogicService.getExchangeRate(tgUsername, null, null, null);
            return "1.00 " + businessLogicService.getHomeCurrency(tgUsername) + " = " + convResult + " " + businessLogicService.getDefaultToCurrency(tgUsername);
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    private String handleHistory(String tgUsername, String[] inputParts) throws SendToUserException, SQLException {
        if (inputParts.length == 1) {
            return businessLogicService.getHistory(tgUsername, null, null, null, null);
        } else if (inputParts.length == 2 && InputValidator.isCurrency(inputParts[1])) {
            return businessLogicService.getHistory(tgUsername, null, null, inputParts[1], null);
        } else if (inputParts.length == 3 && InputValidator.isCurrency(inputParts[1]) && InputValidator.isCurrency(inputParts[2])) {
            return businessLogicService.getHistory(tgUsername, null, null, inputParts[1], inputParts[2]);
        } else if (inputParts.length == 3 && InputValidator.isDate(inputParts[1]) && InputValidator.isDate(inputParts[2])) {
            return businessLogicService.getHistory(tgUsername, inputParts[1], inputParts[2], null, null);
        } else if (inputParts.length == 4 && InputValidator.isDate(inputParts[1]) && InputValidator.isDate(inputParts[2])
                && InputValidator.isCurrency(inputParts[3])) {
            return businessLogicService.getHistory(tgUsername, inputParts[1], inputParts[2], inputParts[3], null);
        } else if (inputParts.length == 5 && InputValidator.isDate(inputParts[1]) && InputValidator.isDate(inputParts[2])
                && InputValidator.isCurrency(inputParts[3]) && InputValidator.isCurrency(inputParts[4])) {
            return businessLogicService.getHistory(tgUsername, inputParts[1], inputParts[2], inputParts[3], inputParts[4]);
        } else {
            return "Usage: " + commandUsageMap.get(inputParts[0]);
        }
    }

    public Map<String, String> getCommandUsageMap() {
        return commandUsageMap;
    }

    public Map<String, String> getCommandInfo() {
        return commandInfo;
    }
}
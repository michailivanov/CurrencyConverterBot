package org.example.config;

import org.example.telegram.MyBot;
import org.example.controller.WebhookController;
import org.example.dbService.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:application.properties")
public class ApplicationConfig {
    @Value("${spring.datasource.url}")
    private String dataSourceUrl;

    @Value("${spring.datasource.username}")
    private String dataSourceUsername;

    @Value("${spring.datasource.password}")
    private String dataSourcePassword;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
//        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(dataSourceUrl);
        dataSource.setUsername(dataSourceUsername);
        dataSource.setPassword(dataSourcePassword);
        return dataSource;
    }

    @Bean
    public DatabaseConfig databaseConfig(DataSource dataSource, ResourceLoader resourceLoader) {
        return new DatabaseConfig(dataSource, resourceLoader);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public DatabaseService databaseService(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${currenciesRateApiUrl}") String currenciesRateApiUrl) {
        return new DatabaseService(jdbcUrl, username, password, currenciesRateApiUrl);
    }

    @Bean
    public MyBot myBot(@Value("${bot.token}") String botToken, @Value("${bot.username}") String botUsername, DatabaseService databaseService) {
        return new MyBot(botToken, botUsername, databaseService);
    }

    @Bean
    public WebhookController webhookController(MyBot myBot) {
        return new WebhookController(myBot);
    }
}

package edu.JavaCourse.CurrencyConverterBot.config;

import edu.JavaCourse.CurrencyConverterBot.dbService.DatabaseService;
import edu.JavaCourse.CurrencyConverterBot.telegram.MyBot;
import edu.JavaCourse.CurrencyConverterBot.controller.WebhookController;
import edu.JavaCourse.CurrencyConverterBot.businessLogicService.BusinessLogicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
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
        dataSource.setUrl(dataSourceUrl);
        dataSource.setUsername(dataSourceUsername);
        dataSource.setPassword(dataSourcePassword);
        return dataSource;
    }

    @Bean
    @DependsOn("databaseConfig")
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public DatabaseConfig databaseConfig(ResourceLoader resourceLoader) {
        return new DatabaseConfig(dataSourceUrl, dataSourceUsername, dataSourcePassword, resourceLoader);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public DatabaseService databaseService(JdbcTemplate jdbcTemplate) {
        return new DatabaseService(jdbcTemplate);
    }

    @Bean
    public BusinessLogicService businessLogicService(DatabaseService databaseService, @Value("${currenciesRateApiUrl}") String currenciesRateApiUrl) {
        return new BusinessLogicService(databaseService, currenciesRateApiUrl);
    }

    @Bean
    public MyBot myBot(@Value("${bot.token}") String botToken, @Value("${bot.username}") String botUsername, BusinessLogicService businessLogicService) {
        return new MyBot(botToken, botUsername, businessLogicService);
    }

    @Bean
    public WebhookController webhookController(MyBot myBot) {
        return new WebhookController(myBot);
    }
}

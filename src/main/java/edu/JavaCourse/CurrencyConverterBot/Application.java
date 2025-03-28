package edu.JavaCourse.CurrencyConverterBot;

import edu.JavaCourse.CurrencyConverterBot.config.ApplicationConfig;
import edu.JavaCourse.CurrencyConverterBot.controller.WebhookController;
import edu.JavaCourse.CurrencyConverterBot.telegram.MyBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

public class Application {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ApplicationConfig.class);
        Logger logger = LoggerFactory.getLogger(Application.class);

        MyBot bot = context.getBean(MyBot.class);
        String botToken = context.getEnvironment().getProperty("bot.token");
        String localtunnelSubdomain = context.getEnvironment().getProperty("localtunnel.subdomain", "mybot");
        
        // LocalTunnel URL (предполагаем, что субдомен известен)
        String localtunnelUrl = "https://" + localtunnelSubdomain + ".loca.lt";
        logger.info("Using LocalTunnel URL: {}", localtunnelUrl);

        // Create a SetWebhook request
        SetWebhook setWebhook = new SetWebhook();
        setWebhook.setUrl(localtunnelUrl + "/" + botToken);

        try {
            bot.setWebhook(setWebhook);
            logger.info("Webhook successfully set to: {}", setWebhook.getUrl());
        } catch (TelegramApiException e) {
            logger.error("Failed to set webhook: {}", e.getMessage());
            System.exit(1);
        }

        // Configure server
        HttpHandler httpHandler = RouterFunctions.toHttpHandler(
                RouterFunctions.route(POST("/" + botToken + "/callback/"), request ->
                        context.getBean(WebhookController.class).handleWebhook(request.bodyToMono(Update.class))
                                .flatMap(message -> message != null
                                        ? ok().contentType(TEXT_PLAIN).bodyValue(message.getText())
                                        : ok().contentType(TEXT_PLAIN).bodyValue(""))
                ).andRoute(GET("/"), request -> ServerResponse.ok().bodyValue("Server is running"))
        );

        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        int port = 8081;

        DisposableServer server = HttpServer.create()
                .host("0.0.0.0")
                .port(port)
                .handle(adapter)
                .bindNow();

        logger.info("Server started on port {}", port);
        server.onDispose().block();
    }
}

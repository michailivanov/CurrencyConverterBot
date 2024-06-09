package edu.JavaCourse.CurrencyConverterBot;

import edu.JavaCourse.CurrencyConverterBot.config.ApplicationConfig;
import edu.JavaCourse.CurrencyConverterBot.controller.WebhookController;
import edu.JavaCourse.CurrencyConverterBot.telegram.MyBot;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

        // Read the ngrok API response to extract the forwarding URL
        String ngrokUrl = null;
        try {
            URL url = new URL("http://localhost:4040/api/tunnels");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray tunnels = jsonResponse.getJSONArray("tunnels");
                for (int i = 0; i < tunnels.length(); i++) {
                    JSONObject tunnel = tunnels.getJSONObject(i);
                    if (tunnel.getString("proto").equals("https")) {
                        ngrokUrl = tunnel.getString("public_url");
                        break;
                    }
                }
            } else {
                logger.error("Failed to retrieve ngrok API response. Response Code: {}", responseCode);
                logger.error("Response Message: {}", connection.getResponseMessage());
            }
        } catch (IOException e) {
            logger.error("Failed to connect to ngrok API: {}", e.getMessage());
        }

        if (ngrokUrl == null) {
            logger.error("Failed to retrieve ngrok forwarding URL");
            System.exit(1);
        }

        // Create a SetWebhook request
        SetWebhook setWebhook = new SetWebhook();
        setWebhook.setUrl(ngrokUrl + "/" + botToken);

        try {
            // Set the webhook
            bot.setWebhook(setWebhook);
        } catch (TelegramApiException e) {
            logger.error("Failed to set webhook: {}", e.getMessage());
            System.exit(1); // Exit if an exception occurs
        }

        // Start server
        HttpHandler httpHandler = RouterFunctions.toHttpHandler(
                RouterFunctions.route(POST("/" + botToken + "/callback/"), request ->
                        context.getBean(WebhookController.class).handleWebhook(request.bodyToMono(Update.class))
                                .flatMap(message -> message != null
                                        ? ok().contentType(TEXT_PLAIN).bodyValue(message.getText())
                                        : ok().contentType(TEXT_PLAIN).bodyValue(""))
                ).andRoute(GET("/"), request -> ServerResponse.ok().bodyValue("Server is running"))
        );

        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        Integer port = 8081;

        DisposableServer server = HttpServer.create()
                .tcpConfiguration(tcpServer -> tcpServer.host("0.0.0.0").port(8081))
                .handle(adapter)
                .bindNow();

        logger.info("Server started on port {}", port);
        server.onDispose().block();
    }
}
package org.example.controller;

import org.example.telegram.MyBot;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import reactor.core.publisher.Mono;


@RestController
@PropertySource("classpath:application.properties")
public class WebhookController {
    private final MyBot bot;

    public WebhookController(MyBot bot) {
        this.bot = bot;
    }

    @PostMapping("/${bot.token}/callback/")
    public Mono<SendMessage> handleWebhook(@RequestBody Mono<Update> updateMono) {
        return updateMono.flatMap(update -> {
            SendMessage sendMessage = bot.onWebhookUpdateReceived(update);
            if (sendMessage != null) {
                return Mono.fromCallable(() -> {
                    try {
                        bot.execute(sendMessage);
                        return sendMessage;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                return Mono.empty();
            }
        });
    }
}
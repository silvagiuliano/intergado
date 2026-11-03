package br.com.intergado.purchases;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);
    private final IntegrationSettings settings;
    private final EvolutionClient evolution;
    private final WhatsAppNotifier whatsApp;
    private final TelegramClient telegram;
    private final TelegramNotifier telegramNotifier;

    public IntegrationController(IntegrationSettings settings, EvolutionClient evolution, WhatsAppNotifier whatsApp,
                                 TelegramClient telegram, TelegramNotifier telegramNotifier) {
        this.settings = settings;
        this.evolution = evolution;
        this.whatsApp = whatsApp;
        this.telegram = telegram;
        this.telegramNotifier = telegramNotifier;
    }

    @GetMapping
    public List<IntegrationSettings.IntegrationView> list() {
        return settings.views();
    }

    @PutMapping("/{integration}")
    public IntegrationSettings.IntegrationView save(@PathVariable String integration, @RequestBody Map<String, String> values,
                                                    HttpServletRequest request) {
        settings.save(integration, values, (String) request.getAttribute(AuthController.EMAIL_ATTRIBUTE));
        return settings.view(IntegrationSettings.definition(integration));
    }

    @GetMapping("/evolution/connection")
    public EvolutionClient.Connection connection() {
        return evolution.state();
    }

    @PostMapping("/evolution/connect")
    public EvolutionClient.Connection connect() {
        return evolution.connect();
    }

    @GetMapping("/evolution/groups")
    public List<EvolutionClient.Group> groups() {
        return evolution.groups();
    }

    @PostMapping("/evolution/logout")
    public EvolutionClient.Connection logout() {
        evolution.logout();
        return evolution.state();
    }

    @PostMapping("/evolution/test")
    public List<WhatsAppNotifier.Delivery> test(HttpServletRequest request) {
        if (settings.list("evolution", "testNumbers").isEmpty() && settings.list("evolution", "groups").isEmpty()) {
            throw new IllegalArgumentException("Cadastre ao menos um número de teste ou grupo antes de enviar.");
        }
        return whatsApp.sendTest((String) request.getAttribute(AuthController.EMAIL_ATTRIBUTE));
    }

    @GetMapping("/telegram/bot")
    public TelegramClient.Bot telegramBot() {
        return telegram.me();
    }

    @GetMapping("/telegram/chats")
    public List<TelegramClient.Chat> telegramChats() {
        return telegram.chats();
    }

    @PostMapping("/telegram/test")
    public List<TelegramNotifier.Delivery> telegramTest(HttpServletRequest request) {
        if (settings.list("telegram", "chats").isEmpty()) {
            throw new IllegalArgumentException("Cadastre ao menos uma conversa ou grupo antes de enviar.");
        }
        if (!telegram.configured()) throw new IllegalStateException("TELEGRAM_BOT_TOKEN não configurado no .env");
        return telegramNotifier.sendTest((String) request.getAttribute(AuthController.EMAIL_ATTRIBUTE));
    }

    @ExceptionHandler(TelegramClient.Failure.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> telegramUnavailable(TelegramClient.Failure error) {
        log.warn("Falha na chamada ao Telegram: {}", error.getMessage());
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> notConfigured(IllegalStateException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> evolutionUnavailable(RestClientException error) {
        log.warn("Falha na chamada ao Evolution API", error);
        String detail = error instanceof RestClientResponseException response
            ? "respondeu " + response.getStatusCode().value() + ": " + response.getResponseBodyAsString()
            : "não respondeu (" + error.getMostSpecificCause().getMessage() + ")";
        return Map.of("error", "Evolution API " + detail);
    }
}

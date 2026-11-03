package br.com.intergado.purchases;

import jakarta.annotation.PreDestroy;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends Telegram messages through the bot, only to the chats chosen on the Integrations screen. */
@Component
public class TelegramNotifier {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final IntegrationSettings settings;
    private final TelegramClient telegram;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TelegramNotifier(IntegrationSettings settings, TelegramClient telegram) {
        this.settings = settings;
        this.telegram = telegram;
    }

    public record Delivery(String number, boolean sent, String error) {}

    /** Runs in the background so a slow Telegram never blocks the status change. */
    public void statusChanged(PurchaseController.Purchase purchase, PurchaseController.Status previous) {
        if (!settings.enabled("telegram", "enabled") || !telegram.configured()) return;
        if (!settings.list("telegram", "notifyStatuses").contains(purchase.status().name())) return;
        String money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(purchase.amount());
        String text = """
            <b>Intergado</b> · solicitação #%04d
            %s (%s)
            Status: %s → <b>%s</b>
            Valor estimado: %s""".formatted(purchase.id(), TelegramClient.escape(purchase.title()),
            TelegramClient.escape(purchase.farm()), WhatsAppNotifier.LABELS.get(previous),
            WhatsAppNotifier.LABELS.get(purchase.status()), money);
        executor.submit(() -> send(text));
    }

    public List<Delivery> sendTest(String requestedBy) {
        return send("<b>Intergado</b> · mensagem de teste enviada por " + TelegramClient.escape(requestedBy)
            + ". Se você recebeu, a integração com o Telegram está funcionando.");
    }

    private List<Delivery> send(String text) {
        List<Delivery> result = new ArrayList<>();
        for (String chat : settings.list("telegram", "chats")) {
            try {
                telegram.sendText(chat, text);
                result.add(new Delivery(chat, true, null));
            } catch (RuntimeException error) {
                // Messages from TelegramClient never carry the token, so they are safe to log.
                log.error("Falha ao enviar Telegram para {}: {}", chat, error.getMessage());
                result.add(new Delivery(chat, false, error.getMessage()));
            }
        }
        return result;
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}

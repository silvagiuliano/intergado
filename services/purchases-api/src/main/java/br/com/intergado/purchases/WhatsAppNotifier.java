package br.com.intergado.purchases;

import jakarta.annotation.PreDestroy;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends WhatsApp messages through Evolution API, only to the configured test numbers. */
@Component
public class WhatsAppNotifier {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotifier.class);
    private static final Map<PurchaseController.Status, String> LABELS = Map.of(
        PurchaseController.Status.RECEIVED, "Recebido",
        PurchaseController.Status.QUOTING, "Em cotação",
        PurchaseController.Status.AWAITING_APPROVAL, "Aguardando aprovação",
        PurchaseController.Status.APPROVED, "Aprovado",
        PurchaseController.Status.ORDERED, "Pedido feito",
        PurchaseController.Status.DELIVERED, "Entregue",
        PurchaseController.Status.PAID, "Pago");

    private final IntegrationSettings settings;
    private final EvolutionClient evolution;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WhatsAppNotifier(IntegrationSettings settings, EvolutionClient evolution) {
        this.settings = settings;
        this.evolution = evolution;
    }

    public record Delivery(String number, boolean sent, String error) {}

    /** Runs in the background so a slow or offline Evolution API never blocks the status change. */
    public void statusChanged(PurchaseController.Purchase purchase, PurchaseController.Status previous) {
        if (!settings.enabled("evolution", "enabled") || !evolution.configured()) return;
        if (!settings.list("evolution", "notifyStatuses").contains(purchase.status().name())) return;
        String money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(purchase.amount());
        String text = """
            *Intergado* · solicitação #%04d
            %s (%s)
            Status: %s → *%s*
            Valor estimado: %s""".formatted(purchase.id(), purchase.title(), purchase.farm(),
            LABELS.get(previous), LABELS.get(purchase.status()), money);
        executor.submit(() -> sendToTestNumbers(text));
    }

    public List<Delivery> sendTest(String requestedBy) {
        return sendToTestNumbers("*Intergado* · mensagem de teste enviada por " + requestedBy
            + ". Se você recebeu, a integração com o Evolution API está funcionando.");
    }

    private List<Delivery> sendToTestNumbers(String text) {
        List<Delivery> result = new ArrayList<>();
        List<String> numbers = settings.list("evolution", "testNumbers");
        // Evolution holds sends to an unpaired instance until the read timeout, so check first.
        String state;
        try {
            state = evolution.state().state();
        } catch (RuntimeException error) {
            log.warn("Evolution API indisponível; WhatsApp não enviado: {}", error.getMessage());
            numbers.forEach(number -> result.add(new Delivery(number, false, "Evolution API indisponível")));
            return result;
        }
        if (!"open".equals(state)) {
            log.warn("WhatsApp não conectado (estado {}); mensagem não enviada.", state);
            numbers.forEach(number -> result.add(new Delivery(number, false, "WhatsApp não conectado (estado: " + state + ")")));
            return result;
        }
        for (String number : numbers) {
            try {
                evolution.sendText(number, text);
                result.add(new Delivery(number, true, null));
            } catch (RuntimeException error) {
                log.error("Falha ao enviar WhatsApp para {} pelo Evolution API", number, error);
                result.add(new Delivery(number, false, error.getMessage()));
            }
        }
        return result;
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}

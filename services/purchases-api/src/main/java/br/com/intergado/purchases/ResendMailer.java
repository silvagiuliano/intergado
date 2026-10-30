package br.com.intergado.purchases;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ResendMailer {
    private static final Logger log = LoggerFactory.getLogger(ResendMailer.class);
    private final RestClient client = RestClient.create("https://api.resend.com");
    private final String apiKey;
    private final String from;

    public ResendMailer(@Value("${app.resend.api-key}") String apiKey, @Value("${app.resend.from}") String from) {
        this.apiKey = apiKey.trim();
        this.from = from;
        if (this.apiKey.isEmpty()) {
            log.warn("RESEND_API_KEY não configurada: os códigos de acesso serão apenas registrados no log.");
        }
    }

    public void sendLoginCode(String email, String code, int minutes) {
        if (apiKey.isEmpty()) {
            log.warn("Código de acesso para {}: {}", email, code);
            return;
        }
        String text = "Seu código de acesso ao Intergado é " + code + ". Ele expira em " + minutes
            + " minutos. Se você não pediu este código, ignore este e-mail.";
        String html = """
            <p>Seu código de acesso ao <strong>Intergado</strong> é:</p>
            <p style="font-size:28px;font-weight:700;letter-spacing:6px">%s</p>
            <p>Ele expira em %d minutos. Se você não pediu este código, ignore este e-mail.</p>
            """.formatted(code, minutes);
        client.post().uri("/emails")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("from", from, "to", List.of(email), "subject", "Código de acesso: " + code,
                "text", text, "html", html))
            .retrieve()
            .toBodilessEntity();
    }
}

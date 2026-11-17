package br.com.intergado.purchases;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Minimal client for Evolution API v2 (instance, pairing and text messages). */
@Component
public class EvolutionClient {
    private static final String INTEGRATION = "evolution";
    private final IntegrationSettings settings;

    public EvolutionClient(IntegrationSettings settings) {
        this.settings = settings;
    }

    /** Connection state plus, when not connected yet, the pairing code and QR Code image. */
    public record Connection(String instance, String state, String pairingCode, String qrBase64) {}

    /** A WhatsApp group the connected number belongs to; announce means only admins can send. */
    public record Group(String id, String subject, Integer size, boolean announce) {}

    public boolean configured() {
        return !apiKey().isEmpty();
    }

    public Connection state() {
        String instance = instance();
        try {
            Map<?, ?> body = client().get().uri("/instance/connectionState/{instance}", instance).retrieve().body(Map.class);
            Object state = body == null || !(body.get("instance") instanceof Map<?, ?> data) ? null : data.get("state");
            return new Connection(instance, state == null ? "unknown" : state.toString(), null, null);
        } catch (HttpClientErrorException.NotFound error) {
            return new Connection(instance, "not_created", null, null);
        }
    }

    /** Creates the instance if needed and asks for a pairing code tied to the sender number. */
    public Connection connect() {
        String instance = instance();
        String number = settings.get(INTEGRATION, "senderNumber");
        Connection current = state();
        if ("open".equals(current.state())) return current;

        Map<?, ?> qrcode;
        if ("not_created".equals(current.state())) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("instanceName", instance);
            request.put("integration", "WHATSAPP-BAILEYS");
            request.put("qrcode", true);
            if (!number.isEmpty()) request.put("number", number);
            Map<?, ?> body = client().post().uri("/instance/create").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(Map.class);
            qrcode = body != null && body.get("qrcode") instanceof Map<?, ?> data ? data : Map.of();
        } else {
            qrcode = client().get().uri(builder -> {
                builder.path("/instance/connect/{instance}");
                if (!number.isEmpty()) builder.queryParam("number", number);
                return builder.build(instance);
            }).retrieve().body(Map.class);
        }
        Connection after = state();
        return new Connection(instance, after.state(), text(qrcode, "pairingCode"), text(qrcode, "base64"));
    }

    public void sendText(String number, String text) {
        client().post().uri("/message/sendText/{instance}", instance()).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("number", number, "text", text)).retrieve().toBodilessEntity();
    }

    public List<Group> groups() {
        if (!"open".equals(state().state())) throw new IllegalStateException("Conecte o WhatsApp antes de buscar os grupos.");
        List<?> body = client().get().uri("/group/fetchAllGroups/{instance}?getParticipants=false", instance())
            .retrieve().body(List.class);
        if (body == null) return List.of();
        // A community's parent group does not accept messages; its announcement subgroup is listed separately.
        return body.stream().filter(Map.class::isInstance).map(Map.class::cast)
            .filter(group -> !Boolean.TRUE.equals(group.get("isCommunity"))).map(group -> new Group(
                text(group, "id"), text(group, "subject"),
                group.get("size") instanceof Number size ? size.intValue() : null,
                Boolean.TRUE.equals(group.get("announce"))))
            .filter(group -> group.id() != null)
            .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.subject()), String.valueOf(b.subject())))
            .toList();
    }

    public void logout() {
        client().delete().uri("/instance/logout/{instance}", instance()).retrieve().toBodilessEntity();
    }

    private String instance() {
        return settings.get(INTEGRATION, "instanceName");
    }

    private String apiKey() {
        return settings.env("EVOLUTION_API_KEY", "");
    }

    private RestClient client() {
        if (!configured()) throw new IllegalStateException("EVOLUTION_API_KEY não configurada no .env");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory)
            .baseUrl(settings.env("EVOLUTION_API_URL", "http://127.0.0.1:8090"))
            .defaultHeader("apikey", apiKey()).build();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }
}

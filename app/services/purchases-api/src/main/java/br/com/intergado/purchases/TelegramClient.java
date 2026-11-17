package br.com.intergado.purchases;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Minimal client for the Telegram Bot API (getMe, getUpdates and sendMessage).
 * The bot token is part of every URL, so errors are rethrown as {@link Failure} without it.
 */
@Component
public class TelegramClient {
    private final IntegrationSettings settings;

    public TelegramClient(IntegrationSettings settings) {
        this.settings = settings;
    }

    /** Bot identity, from getMe. */
    public record Bot(long id, String username, String name) {}

    /** A chat the bot has seen recently: private, group, supergroup or channel. */
    public record Chat(String id, String title, String type) {}

    /** A Telegram error with the token removed; safe to log and to show on the screen. */
    public static class Failure extends RuntimeException {
        public Failure(String message) {
            super(message);
        }
    }

    public boolean configured() {
        return !token().isEmpty();
    }

    public Bot me() {
        Map<?, ?> result = (Map<?, ?>) call("getMe", null);
        return new Bot(((Number) result.get("id")).longValue(), text(result, "username"), text(result, "first_name"));
    }

    /**
     * Chats found in the updates Telegram still holds (up to 24 hours). getUpdates is read without
     * confirming the updates, so the list does not shrink each time the screen asks for it.
     */
    public List<Chat> chats() {
        List<?> updates = (List<?>) call("getUpdates", Map.of("limit", 100,
            "allowed_updates", List.of("message", "channel_post", "my_chat_member")));
        Map<String, Chat> chats = new LinkedHashMap<>();
        for (Object update : updates) {
            if (!(update instanceof Map<?, ?> data)) continue;
            for (String kind : List.of("message", "channel_post", "my_chat_member")) {
                if (data.get(kind) instanceof Map<?, ?> event && event.get("chat") instanceof Map<?, ?> chat) {
                    Chat found = chat(chat);
                    chats.put(found.id(), found);
                }
            }
        }
        return chats.values().stream()
            .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.title()), String.valueOf(b.title())))
            .toList();
    }

    /** text is HTML (parse_mode HTML); escape user data with {@link #escape}. */
    public void sendText(String chatId, String text) {
        call("sendMessage", Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML",
            "link_preview_options", Map.of("is_disabled", true)));
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Object call(String method, Map<String, Object> body) {
        if (!configured()) throw new IllegalStateException("TELEGRAM_BOT_TOKEN não configurado no .env");
        Map<?, ?> response;
        try {
            response = client().post().uri("/bot{token}/{method}", token(), method)
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                .retrieve().body(Map.class);
        } catch (RestClientResponseException error) {
            throw new Failure("Telegram respondeu " + error.getStatusCode().value() + ": " + description(error));
        } catch (RestClientException error) {
            throw new Failure("Telegram não respondeu (" + redact(error.getMostSpecificCause().getMessage()) + ")");
        }
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new Failure("Telegram recusou " + method + ": " + text(response, "description"));
        }
        return response.get("result");
    }

    private static Chat chat(Map<?, ?> chat) {
        String title = text(chat, "title");
        if (title == null) {
            String first = text(chat, "first_name"), last = text(chat, "last_name");
            title = first == null ? text(chat, "username") : last == null ? first : first + " " + last;
        }
        return new Chat(String.valueOf(chat.get("id")), title, text(chat, "type"));
    }

    private String description(RestClientResponseException error) {
        try {
            Map<?, ?> body = error.getResponseBodyAs(Map.class);
            String description = text(body, "description");
            if (description != null) return redact(description);
        } catch (RuntimeException ignored) {
            // Body was not JSON; fall back to the raw text below.
        }
        return redact(error.getResponseBodyAsString());
    }

    private String redact(String message) {
        String token = token();
        return message == null || token.isEmpty() ? String.valueOf(message) : message.replace(token, "***");
    }

    private String token() {
        return settings.env("TELEGRAM_BOT_TOKEN", "");
    }

    private RestClient client() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory)
            .baseUrl(settings.env("TELEGRAM_API_URL", "https://api.telegram.org")).build();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }
}

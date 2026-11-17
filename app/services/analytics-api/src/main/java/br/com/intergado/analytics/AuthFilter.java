package br.com.intergado.analytics;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;

/** Accepts only sessions created by the purchases API login (same SQLite file). */
@Provider
public class AuthFilter implements ContainerRequestFilter {
    private static final String COOKIE = "intergado_session";
    @Inject Database database;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!(path.startsWith("/") ? path.substring(1) : path).startsWith("api/")) return;
        Cookie cookie = request.getCookies().get(COOKIE);
        if (cookie == null || !validSession(cookie.getValue())) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Sessão inválida ou expirada")).build());
        }
    }

    private boolean validSession(String token) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                 SELECT 1 FROM sessions s JOIN users u ON u.email = s.email AND u.active = 1
                 WHERE s.token_hash = ? AND s.expires_at > strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                 """)) {
            statement.setString(1, sha256(token));
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Não foi possível validar a sessão", error);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

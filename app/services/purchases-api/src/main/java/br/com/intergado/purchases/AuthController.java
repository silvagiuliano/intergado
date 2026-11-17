package br.com.intergado.purchases;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String COOKIE = "intergado_session";
    public static final String EMAIL_ATTRIBUTE = "authEmail";
    public static final String ROLE_ATTRIBUTE = "authRole";
    static final String NOW = "strftime('%Y-%m-%dT%H:%M:%fZ', 'now')";
    private static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final SecureRandom random = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ResendMailer mailer;
    private final UserService users;
    private final int codeMinutes;
    private final int sessionDays;
    private final boolean cookieSecure;

    public AuthController(JdbcTemplate jdbc, ResendMailer mailer, UserService users,
                          @Value("${app.auth.code-minutes}") int codeMinutes,
                          @Value("${app.auth.session-days}") int sessionDays,
                          @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.users = users;
        this.codeMinutes = codeMinutes;
        this.sessionDays = sessionDays;
        this.cookieSecure = cookieSecure;
    }

    public record CodeRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record CodeVerification(@NotBlank @Email @Size(max = 254) String email,
                                   @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
    public record Session(String email, String name, Role role) {}

    /** Always answers the same way, so the response does not reveal which emails are authorized. */
    @PostMapping("/request-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestCode(@Valid @RequestBody CodeRequest input) {
        String email = normalize(input.email());
        Map<String, String> answer = Map.of("message", "Se o e-mail estiver autorizado, enviaremos um código de acesso.");
        Integer allowed = jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ? AND active = 1", Integer.class, email);
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM login_codes WHERE email = ? AND created_at > "
            + "strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-60 seconds')", Integer.class, email);
        if (allowed == 0 || recent > 0) return answer;

        String code = "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update("UPDATE login_codes SET used_at = " + NOW + " WHERE email = ? AND used_at IS NULL", email);
        Long id = jdbc.queryForObject("INSERT INTO login_codes (email, code_hash, expires_at) VALUES (?, ?, "
            + "strftime('%Y-%m-%dT%H:%M:%fZ', 'now', ?)) RETURNING id", Long.class,
            email, sha256(email + ":" + code), "+" + codeMinutes + " minutes");
        try {
            mailer.sendLoginCode(email, code, codeMinutes);
        } catch (RuntimeException error) {
            jdbc.update("DELETE FROM login_codes WHERE id = ?", id);
            log.error("Falha ao enviar o código de acesso pelo Resend para {}", email, error);
        }
        return answer;
    }

    @PostMapping("/verify")
    public Session verify(@Valid @RequestBody CodeVerification input, HttpServletResponse response) {
        String email = normalize(input.email());
        var candidate = jdbc.query("""
            SELECT c.id, c.code_hash FROM login_codes c JOIN users u ON u.email = c.email AND u.active = 1
            WHERE c.email = ? AND c.used_at IS NULL AND c.attempts < ? AND c.expires_at > %s
            ORDER BY c.created_at DESC LIMIT 1
            """.formatted(NOW), (rs, row) -> Map.entry(rs.getLong("id"), rs.getString("code_hash")),
            email, MAX_ATTEMPTS).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido ou expirado"));

        jdbc.update("UPDATE login_codes SET attempts = attempts + 1 WHERE id = ?", candidate.getKey());
        byte[] expected = candidate.getValue().getBytes(StandardCharsets.UTF_8);
        byte[] informed = sha256(email + ":" + input.code()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, informed)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido ou expirado");
        }

        jdbc.update("UPDATE login_codes SET used_at = " + NOW + " WHERE id = ?", candidate.getKey());
        jdbc.update("DELETE FROM sessions WHERE expires_at <= " + NOW);
        jdbc.update("DELETE FROM login_codes WHERE created_at < strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-1 day')");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO sessions (token_hash, email, expires_at) VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%fZ', 'now', ?))",
            sha256(token), email, "+" + sessionDays + " days");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(sessionDays)).toString());
        users.recordLogin(email);
        return session(email);
    }

    @GetMapping("/me")
    public Session me(HttpServletRequest request) {
        return session((String) request.getAttribute(EMAIL_ATTRIBUTE));
    }

    private Session session(String email) {
        UserService.User user = users.find(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new Session(user.email(), user.name(), user.role());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletResponse response) {
        if (token != null) jdbc.update("DELETE FROM sessions WHERE token_hash = ?", sha256(token));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
            .path("/").maxAge(maxAge).build();
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

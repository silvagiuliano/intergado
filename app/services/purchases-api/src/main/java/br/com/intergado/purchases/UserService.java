package br.com.intergado.purchases;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users who may log in, with their role. The screen is the source of truth; AUTH_ADMIN_EMAILS only
 * creates the initial administrators and, if no active administrator is left, restores them.
 */
@Service
public class UserService {
    /** created_by / updated_by of rows written by the startup bootstrap. */
    public static final String ENV = ".env";
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String NOW = AuthController.NOW;

    private final JdbcTemplate jdbc;
    private final List<String> adminEmails;

    public UserService(JdbcTemplate jdbc, @Value("${app.auth.admin-emails}") String adminEmails) {
        this.jdbc = jdbc;
        this.adminEmails = parseEmails(adminEmails);
    }

    public record User(String email, String name, Role role, boolean active, String createdAt, String createdBy,
                       String updatedAt, String updatedBy, String lastLoginAt) {
        public boolean fromEnv() {
            return ENV.equals(createdBy);
        }
    }

    public record Changes(String name, Role role, Boolean active) {}

    static List<String> parseEmails(String value) {
        return Arrays.stream(value == null ? new String[0] : value.split(","))
            .map(AuthController::normalize).filter(email -> !email.isEmpty()).distinct().toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        for (String email : adminEmails) {
            if (!EMAIL.matcher(email).matches()) {
                log.warn("AUTH_ADMIN_EMAILS: e-mail inválido ignorado: {}", email);
                continue;
            }
            int created = jdbc.update("INSERT INTO users (email, role, active, created_by, updated_by) VALUES (?, 'ADMIN', 1, ?, ?) "
                + "ON CONFLICT(email) DO NOTHING", email, ENV, ENV);
            if (created > 0) log.info("Administrador inicial criado a partir do .env: {}", email);
        }
        if (activeAdmins() == 0 && !adminEmails.isEmpty()) {
            // Recovery: nobody could manage users anymore, so the .env administrators come back.
            String marks = adminEmails.stream().map(e -> "?").collect(Collectors.joining(","));
            jdbc.update("UPDATE users SET role = 'ADMIN', active = 1, updated_at = " + NOW + ", updated_by = '" + ENV
                + "' WHERE email IN (" + marks + ")", adminEmails.toArray());
            log.warn("Nenhum administrador ativo: os e-mails de AUTH_ADMIN_EMAILS foram reativados como administradores.");
        }
        jdbc.update("DELETE FROM sessions WHERE email IN (SELECT email FROM users WHERE active = 0)");
        Integer active = jdbc.queryForObject("SELECT count(*) FROM users WHERE active = 1", Integer.class);
        if (activeAdmins() == 0) log.warn("Nenhum administrador ativo e AUTH_ADMIN_EMAILS vazio: ninguém poderá gerenciar usuários.");
        log.info("{} usuário(s) ativo(s), {} administrador(es).", active, activeAdmins());
    }

    /**
     * Bases created before roles existed: add the new columns; their users keep full access as ADMIN.
     * Runs at bean creation (after schema.sql), before the server accepts requests.
     */
    @PostConstruct
    void migrate() {
        Set<String> columns = jdbc.query("PRAGMA table_info(users)", (rs, row) -> rs.getString("name"))
            .stream().collect(Collectors.toSet());
        if (!columns.contains("name")) jdbc.execute("ALTER TABLE users ADD COLUMN name TEXT CHECK (name IS NULL OR length(name) <= 100)");
        if (!columns.contains("created_by")) jdbc.execute("ALTER TABLE users ADD COLUMN created_by TEXT");
        if (!columns.contains("updated_at")) jdbc.execute("ALTER TABLE users ADD COLUMN updated_at TEXT");
        if (!columns.contains("updated_by")) jdbc.execute("ALTER TABLE users ADD COLUMN updated_by TEXT");
        if (!columns.contains("role")) {
            jdbc.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'LEITOR' "
                + "CHECK (role IN ('LEITOR','OPERADOR','ADMIN'))");
            // Until now every user came from AUTH_ALLOWED_EMAILS and had full access.
            jdbc.update("UPDATE users SET role = 'ADMIN', created_by = ?", ENV);
            log.info("Tabela users migrada: usuários existentes passaram a ser administradores.");
        }
        if (!columns.contains("last_login_at")) jdbc.execute("ALTER TABLE users ADD COLUMN last_login_at TEXT");
    }

    public List<User> list() {
        return jdbc.query("SELECT * FROM users ORDER BY active DESC, email", this::map);
    }

    public Optional<User> find(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", this::map, AuthController.normalize(email)).stream().findFirst();
    }

    @Transactional
    public User create(String email, String name, Role role, String actor) {
        String normalized = AuthController.normalize(email == null ? "" : email);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) throw new IllegalArgumentException("E-mail inválido");
        if (role == null) throw new IllegalArgumentException("Informe o perfil");
        if (find(normalized).isPresent()) throw new IllegalStateException("Este e-mail já está cadastrado");
        jdbc.update("INSERT INTO users (email, name, role, active, created_by, updated_at, updated_by) VALUES (?, ?, ?, 1, ?, " + NOW + ", ?)",
            normalized, cleanName(name), role.name(), actor, actor);
        return find(normalized).orElseThrow();
    }

    @Transactional
    public User update(String email, Changes changes, String actor) {
        User current = find(email).orElseThrow(() -> new NotFound("Usuário não encontrado"));
        Role role = changes.role() == null ? current.role() : changes.role();
        boolean active = changes.active() == null ? current.active() : changes.active();
        String name = changes.name() == null ? current.name() : cleanName(changes.name());

        boolean self = current.email().equals(AuthController.normalize(actor));
        if (self && (role != current.role() || !active)) {
            throw new IllegalStateException("Você não pode alterar o próprio perfil nem desativar o próprio acesso; peça a outro administrador");
        }
        boolean losesAdmin = current.active() && current.role() == Role.ADMIN && (role != Role.ADMIN || !active);
        if (losesAdmin && activeAdmins() <= 1) throw new IllegalStateException("É preciso manter ao menos um administrador ativo");

        jdbc.update("UPDATE users SET name = ?, role = ?, active = ?, updated_at = " + NOW + ", updated_by = ? WHERE email = ?",
            name, role.name(), active ? 1 : 0, actor, current.email());
        if (!active) jdbc.update("DELETE FROM sessions WHERE email = ?", current.email());
        return find(current.email()).orElseThrow();
    }

    public void recordLogin(String email) {
        jdbc.update("UPDATE users SET last_login_at = " + NOW + " WHERE email = ?", email);
    }

    int activeAdmins() {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE active = 1 AND role = 'ADMIN'", Integer.class);
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        if (trimmed.length() > 100) throw new IllegalArgumentException("Nome com mais de 100 caracteres");
        return trimmed;
    }

    private User map(ResultSet rs, int row) throws SQLException {
        return new User(rs.getString("email"), rs.getString("name"), Role.valueOf(rs.getString("role")), rs.getInt("active") == 1,
            rs.getString("created_at"), rs.getString("created_by"), rs.getString("updated_at"), rs.getString("updated_by"),
            rs.getString("last_login_at"));
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}

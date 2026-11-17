package br.com.intergado.purchases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class UserServiceTest {
    @TempDir Path dir;
    JdbcTemplate jdbc;

    @BeforeEach
    void database() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + dir.resolve("test.db"), true);
        jdbc = new JdbcTemplate(dataSource);
    }

    private void schema() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
    }

    private UserService service(String adminEmails) {
        var service = new UserService(jdbc, adminEmails);
        service.migrate();
        service.bootstrap();
        return service;
    }

    @Test
    void envCreatesInitialAdminsOnlyOnce() {
        schema();
        var users = service(" Admin@Example.com ,ops@example.com,invalid");
        assertEquals(2, users.activeAdmins());
        var admin = users.find("admin@example.com").orElseThrow();
        assertTrue(admin.fromEnv());
        assertEquals(Role.ADMIN, admin.role());

        users.create("third@example.com", "Third", Role.ADMIN, "admin@example.com");
        users.update("ops@example.com", new UserService.Changes(null, Role.LEITOR, false), "admin@example.com");
        // Restart: the screen wins; the .env does not re-enable or promote.
        var again = service("admin@example.com,ops@example.com");
        var ops = again.find("ops@example.com").orElseThrow();
        assertFalse(ops.active());
        assertEquals(Role.LEITOR, ops.role());
    }

    @Test
    void envAdminCanBeDisabledByAnotherAdmin() {
        schema();
        var users = service("admin@example.com");
        users.create("boss@example.com", null, Role.ADMIN, "admin@example.com");
        users.update("admin@example.com", new UserService.Changes(null, null, false), "boss@example.com");
        assertFalse(users.find("admin@example.com").orElseThrow().active());
        assertFalse(service("admin@example.com").find("admin@example.com").orElseThrow().active());
    }

    @Test
    void envAdminsComeBackWhenNoActiveAdminIsLeft() {
        schema();
        service("admin@example.com");
        jdbc.update("UPDATE users SET active = 0");
        var users = service("admin@example.com");
        assertEquals(1, users.activeAdmins());
        assertTrue(users.find("admin@example.com").orElseThrow().active());
    }

    @Test
    void lastActiveAdminAndSelfChangesAreProtected() {
        schema();
        var users = service("admin@example.com");
        users.create("reader@example.com", null, Role.LEITOR, "admin@example.com");
        var self = assertThrows(IllegalStateException.class,
            () -> users.update("admin@example.com", new UserService.Changes(null, Role.OPERADOR, null), "admin@example.com"));
        assertTrue(self.getMessage().contains("próprio"));
        users.update("reader@example.com", new UserService.Changes(null, Role.ADMIN, null), "admin@example.com");
        users.update("reader@example.com", new UserService.Changes(null, null, false), "admin@example.com");
        // Only admin@ is left active: nobody else can demote it.
        jdbc.update("INSERT INTO users (email, role) VALUES ('op@example.com', 'OPERADOR')");
        var last = assertThrows(IllegalStateException.class,
            () -> users.update("admin@example.com", new UserService.Changes(null, null, false), "op@example.com"));
        assertEquals("É preciso manter ao menos um administrador ativo", last.getMessage());
        // Renaming oneself is fine.
        assertEquals("Ana", users.update("admin@example.com", new UserService.Changes(" Ana ", null, null), "admin@example.com").name());
    }

    @Test
    void disablingRemovesSessionsAndDuplicatesAreRejected() {
        schema();
        var users = service("admin@example.com");
        users.create("Op@Example.com", "", Role.OPERADOR, "admin@example.com");
        jdbc.update("INSERT INTO sessions (token_hash, email, expires_at) VALUES ('h', 'op@example.com', '2999-01-01T00:00:00Z')");
        users.update("op@example.com", new UserService.Changes(null, null, false), "admin@example.com");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class));
        assertThrows(IllegalStateException.class, () -> users.create("op@example.com", null, Role.LEITOR, "admin@example.com"));
        assertThrows(IllegalArgumentException.class, () -> users.create("not-an-email", null, Role.LEITOR, "admin@example.com"));
        assertThrows(UserService.NotFound.class,
            () -> users.update("ghost@example.com", new UserService.Changes(null, null, false), "admin@example.com"));
    }

    @Test
    void legacyBaseIsMigratedAndExistingUsersBecomeAdmins() throws Exception {
        jdbc.execute("CREATE TABLE users (email TEXT PRIMARY KEY, active INTEGER NOT NULL DEFAULT 1, "
            + "created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')))");
        jdbc.update("INSERT INTO users (email, active) VALUES ('old@example.com', 1), ('gone@example.com', 0)");
        schema();
        var users = service("");
        assertEquals(Role.ADMIN, users.find("old@example.com").orElseThrow().role());
        assertTrue(users.find("old@example.com").orElseThrow().fromEnv());
        assertFalse(users.find("gone@example.com").orElseThrow().active());
        assertTrue(Files.exists(dir.resolve("test.db")));
    }
}

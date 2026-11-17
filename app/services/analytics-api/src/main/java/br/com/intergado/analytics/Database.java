package br.com.intergado.analytics;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Database {
    @ConfigProperty(name = "app.db.path")
    String path;

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }
}

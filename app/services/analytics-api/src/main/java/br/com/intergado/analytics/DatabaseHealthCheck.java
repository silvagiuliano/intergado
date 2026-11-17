package br.com.intergado.analytics;

import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
public class DatabaseHealthCheck implements HealthCheck {
    @Inject Database database;

    @Override
    public HealthCheckResponse call() {
        try (var connection = database.open();
             var statement = connection.prepareStatement("SELECT count(*) FROM purchase_requests");
             var result = statement.executeQuery()) {
            return result.next() ? HealthCheckResponse.up("sqlite") : HealthCheckResponse.down("sqlite");
        } catch (Exception error) {
            return HealthCheckResponse.down("sqlite");
        }
    }
}

package br.com.intergado.analytics;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/analytics")
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {
    @Inject Database database;

    public record Group(String name, long count, BigDecimal amount) {}
    public record Day(String date, long count) {}
    public record Overview(long total, long active, long awaitingApproval, long completed,
                           BigDecimal estimatedAmount, BigDecimal paidAmount, boolean demoData,
                           List<Group> byStatus, List<Group> byCategory, List<Group> byFarm,
                           List<Day> recentDays) {}

    @GET
    @Path("/overview")
    public Overview overview() throws SQLException {
        try (Connection connection = database.open()) {
            long total, active, awaiting, completed;
            BigDecimal estimated, paid;
            boolean demo;
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) AS total,
                  count(*) FILTER (WHERE status NOT IN ('DELIVERED','PAID')) AS active,
                  count(*) FILTER (WHERE status = 'AWAITING_APPROVAL') AS awaiting,
                  count(*) FILTER (WHERE status IN ('DELIVERED','PAID')) AS completed,
                  coalesce(sum(amount),0) AS estimated,
                  coalesce(sum(amount) FILTER (WHERE status = 'PAID'),0) AS paid,
                  coalesce(max(demo),0) AS demo
                FROM purchase_requests
                """); ResultSet rs = statement.executeQuery()) {
                rs.next();
                total = rs.getLong("total");
                active = rs.getLong("active");
                awaiting = rs.getLong("awaiting");
                completed = rs.getLong("completed");
                estimated = rs.getBigDecimal("estimated");
                paid = rs.getBigDecimal("paid");
                demo = rs.getBoolean("demo");
            }
            return new Overview(total, active, awaiting, completed, estimated, paid, demo,
                groups(connection, "status"), groups(connection, "category"), groups(connection, "farm"),
                recentDays(connection));
        }
    }

    private List<Group> groups(Connection connection, String column) throws SQLException {
        // The column comes only from the three fixed calls above.
        String sql = "SELECT " + column + " AS name, count(*) AS total, coalesce(sum(amount),0) AS amount "
            + "FROM purchase_requests GROUP BY " + column + " ORDER BY total DESC, name";
        List<Group> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(new Group(rs.getString("name"), rs.getLong("total"), rs.getBigDecimal("amount")));
        }
        return result;
    }

    private List<Day> recentDays(Connection connection) throws SQLException {
        List<Day> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT substr(requested_at, 1, 10) AS day, count(*) AS total
            FROM purchase_requests
            WHERE substr(requested_at, 1, 10) >= ?
            GROUP BY day
            """)) {
            LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
            statement.setString(1, today.minusDays(6).toString());
            Map<String, Long> counts = new HashMap<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) counts.put(rs.getString("day"), rs.getLong("total"));
            }
            for (int offset = 6; offset >= 0; offset--) {
                String day = today.minusDays(offset).toString();
                result.add(new Day(day, counts.getOrDefault(day, 0L)));
            }
        }
        return result;
    }
}

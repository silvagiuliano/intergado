package br.com.intergado.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final JdbcTemplate jdbc;
    private final WhatsAppNotifier whatsApp;
    private final TelegramNotifier telegram;

    public PurchaseController(JdbcTemplate jdbc, WhatsAppNotifier whatsApp, TelegramNotifier telegram) {
        this.jdbc = jdbc;
        this.whatsApp = whatsApp;
        this.telegram = telegram;
    }

    public enum Status { RECEIVED, QUOTING, AWAITING_APPROVAL, APPROVED, ORDERED, DELIVERED, PAID }

    public record Purchase(long id, String title, String farm, String category, String requester,
                           String supplier, BigDecimal amount, Status status, OffsetDateTime requestedAt,
                           OffsetDateTime updatedAt, boolean demo) {}

    public record NewPurchase(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 100) String farm,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 100) String requester,
        @Size(max = 120) String supplier,
        @NotNull @DecimalMin("0.00") BigDecimal amount
    ) {}

    public record StatusChange(@NotNull Status status) {}

    private Purchase map(ResultSet rs, int row) throws SQLException {
        return new Purchase(rs.getLong("id"), rs.getString("title"), rs.getString("farm"),
            rs.getString("category"), rs.getString("requester"), rs.getString("supplier"),
            rs.getBigDecimal("amount"), Status.valueOf(rs.getString("status")),
            OffsetDateTime.parse(rs.getString("requested_at")),
            OffsetDateTime.parse(rs.getString("updated_at")), rs.getBoolean("demo"));
    }

    @GetMapping
    public List<Purchase> list(@RequestParam(required = false) Status status) {
        String columns = "SELECT * FROM purchase_requests";
        if (status == null) {
            return jdbc.query(columns + " ORDER BY requested_at DESC, id DESC LIMIT 100", this::map);
        }
        return jdbc.query(columns + " WHERE status = ? ORDER BY requested_at DESC, id DESC LIMIT 100",
            this::map, status.name());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Purchase create(@Valid @RequestBody NewPurchase input) {
        Long id = jdbc.queryForObject("""
            INSERT INTO purchase_requests (title, farm, category, requester, supplier, amount, status)
            VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED') RETURNING id
            """, Long.class, input.title().trim(), input.farm().trim(), input.category().trim(),
            input.requester().trim(), input.supplier(), input.amount());
        return get(id);
    }

    @PutMapping("/{id}/status")
    public Purchase updateStatus(@PathVariable long id, @Valid @RequestBody StatusChange input) {
        Status previous = get(id).status();
        int changed = jdbc.update("UPDATE purchase_requests SET status = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now') WHERE id = ?",
            input.status().name(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada");
        Purchase purchase = get(id);
        if (previous != purchase.status()) {
            whatsApp.statusChanged(purchase, previous);
            telegram.statusChanged(purchase, previous);
        }
        return purchase;
    }

    @PutMapping("/{id}")
    public Purchase update(@PathVariable long id, @Valid @RequestBody NewPurchase input) {
        int changed = jdbc.update("""
            UPDATE purchase_requests SET title = ?, farm = ?, category = ?, requester = ?, supplier = ?,
              amount = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
            WHERE id = ?
            """, input.title().trim(), input.farm().trim(), input.category().trim(),
            input.requester().trim(), input.supplier(), input.amount(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada");
        return get(id);
    }

    @GetMapping("/{id}")
    public Purchase get(@PathVariable long id) {
        return jdbc.query("SELECT * FROM purchase_requests WHERE id = ?", this::map, id)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException error) {
        return Map.of("error", "Valor inválido");
    }
}

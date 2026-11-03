package br.com.intergado.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/farms")
public class FarmController {
    private final JdbcTemplate jdbc;

    public FarmController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Farm(long id, String name, LocalDate balanceDate, int herdBalance,
                       int adeparaBalance, String sources, boolean demo) {}

    public record FarmInput(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate balanceDate,
        @NotNull @Min(0) Integer herdBalance,
        @NotNull @Min(0) Integer adeparaBalance,
        @NotNull String sources
    ) {}

    private Farm map(ResultSet rs, int row) throws SQLException {
        return new Farm(rs.getLong("id"), rs.getString("name"), LocalDate.parse(rs.getString("balance_date")),
            rs.getInt("herd_balance"), rs.getInt("adepara_balance"), rs.getString("sources"), rs.getBoolean("demo"));
    }

    @GetMapping
    public List<Farm> list() {
        return jdbc.query("SELECT * FROM farms ORDER BY name", this::map);
    }

    @GetMapping("/{id}")
    public Farm get(@PathVariable long id) {
        return jdbc.query("SELECT * FROM farms WHERE id = ?", this::map, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fazenda não encontrada"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Farm create(@Valid @RequestBody FarmInput input) {
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO farms (name, balance_date, herd_balance, adepara_balance, sources)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, input.name().trim(), input.balanceDate().toString(),
                input.herdBalance(), input.adeparaBalance(), input.sources().trim());
            return get(id);
        } catch (DataAccessException e) {
            throw databaseError(e);
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Farm update(@PathVariable long id, @Valid @RequestBody FarmInput input) {
        try {
            Farm current = get(id);
            int changed = jdbc.update("""
                UPDATE farms SET name = ?, balance_date = ?, herd_balance = ?, adepara_balance = ?, sources = ?
                WHERE id = ?
                """, input.name().trim(), input.balanceDate().toString(), input.herdBalance(),
                input.adeparaBalance(), input.sources().trim(), id);
            if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fazenda não encontrada");
            if (!current.name().equals(input.name().trim())) {
                jdbc.update("UPDATE purchase_requests SET farm = ? WHERE farm = ?", input.name().trim(), current.name());
            }
            return get(id);
        } catch (DataAccessException e) {
            throw databaseError(e);
        }
    }

    private RuntimeException databaseError(DataAccessException error) {
        if (error instanceof DuplicateKeyException ||
            error instanceof UncategorizedSQLException sql && sql.getSQLException().getErrorCode() == 19) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma fazenda com este nome", error);
        }
        return error;
    }

    /** Answers { error } so the screen shows the reason (e.g. duplicated name) instead of a generic message. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
            .body(Map.of("error", error.getReason() == null ? "Não foi possível concluir a operação." : error.getReason()));
    }
}

package br.com.intergado.purchases;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings of external integrations. Each editable field is read from integration_settings
 * (saved on the Integrations screen) and falls back to its .env variable, then to a default.
 * Secrets stay in the environment only; the screen just reports whether they are set.
 */
@Component
public class IntegrationSettings {
    public enum FieldType { BOOLEAN, TEXT, PHONE, PHONE_LIST, STATUS_LIST }

    public record Field(String name, String label, FieldType type, String envName, String fallback,
                        String pattern, String help) {}
    public record EnvInfo(String label, String envName, boolean secret, String fallback) {}
    public record Definition(String key, String label, String description, List<Field> fields, List<EnvInfo> environment) {}

    public record FieldView(String name, String label, FieldType type, String value, String source,
                            String envName, String help, List<String> options) {}
    public record EnvView(String label, String envName, boolean secret, boolean configured, String value) {}
    public record IntegrationView(String key, String label, String description, List<FieldView> fields,
                                  List<EnvView> environment) {}

    static final String ALL_STATUSES = String.join(",",
        Arrays.stream(PurchaseController.Status.values()).map(Enum::name).toList());

    public static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
    static {
        DEFINITIONS.put("evolution", new Definition("evolution", "WhatsApp · Evolution API",
            "Envio de mensagens de WhatsApp pelo Evolution API (fase de testes). As mensagens só vão para os números de teste.",
            List.of(
                new Field("enabled", "Enviar avisos automáticos", FieldType.BOOLEAN, "WHATSAPP_ENABLED", "false", null,
                    "Avisa os números de teste quando uma solicitação de compra muda de status."),
                new Field("instanceName", "Nome da instância", FieldType.TEXT, "EVOLUTION_INSTANCE", "intergado",
                    "[A-Za-z0-9_-]{1,50}", "Identificador da conexão no Evolution API. Letras, números, - e _."),
                new Field("senderNumber", "WhatsApp que envia", FieldType.PHONE, "EVOLUTION_INSTANCE_NUMBER", "", null,
                    "Número do celular conectado, com DDI e DDD, só dígitos. Ex.: 5591999990000. Usado no código de pareamento."),
                new Field("testNumbers", "Números de teste (destinatários)", FieldType.PHONE_LIST, "WHATSAPP_TEST_NUMBERS", "", null,
                    "Um por linha ou separados por vírgula, com DDI e DDD. Só esses números recebem mensagens."),
                new Field("notifyStatuses", "Status que geram aviso", FieldType.STATUS_LIST, "WHATSAPP_NOTIFY_STATUSES",
                    ALL_STATUSES, null, "Avisar quando a solicitação passar para um destes status.")),
            List.of(
                new EnvInfo("URL do Evolution API", "EVOLUTION_API_URL", false, "http://127.0.0.1:8090"),
                new EnvInfo("Chave global do Evolution API", "EVOLUTION_API_KEY", true, ""))));
        DEFINITIONS.put("resend", new Definition("resend", "E-mail · Resend",
            "Envio do código de acesso por e-mail.",
            List.of(new Field("from", "Remetente", FieldType.TEXT, "AUTH_EMAIL_FROM", "Intergado <onboarding@resend.dev>",
                ".{3,200}", "Endereço de um domínio verificado no Resend, como Intergado <login@seu-dominio.com.br>.")),
            List.of(new EnvInfo("Chave da API do Resend", "RESEND_API_KEY", true, ""))));
    }

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public IntegrationSettings(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    public String get(String integration, String name) {
        Field field = field(integration, name);
        return saved(integration, name).orElseGet(() -> envOrDefault(field.envName(), field.fallback()));
    }

    public boolean enabled(String integration, String name) {
        return Boolean.parseBoolean(get(integration, name));
    }

    public List<String> list(String integration, String name) {
        return Arrays.stream(get(integration, name).split("[,\\s]+")).filter(item -> !item.isBlank()).toList();
    }

    public String env(String envName, String fallback) {
        return envOrDefault(envName, fallback);
    }

    public List<IntegrationView> views() {
        return DEFINITIONS.values().stream().map(this::view).toList();
    }

    public IntegrationView view(Definition definition) {
        List<FieldView> fields = definition.fields().stream().map(field -> {
            Optional<String> saved = saved(definition.key(), field.name());
            String fromEnv = environment.getProperty(field.envName(), "").trim();
            String source = saved.isPresent() ? "tela" : !fromEnv.isEmpty() ? ".env" : "padrão";
            List<String> options = field.type() == FieldType.STATUS_LIST ? List.of(ALL_STATUSES.split(",")) : List.of();
            return new FieldView(field.name(), field.label(), field.type(), get(definition.key(), field.name()), source,
                field.envName(), field.help(), options);
        }).toList();
        List<EnvView> env = definition.environment().stream().map(info -> {
            String value = envOrDefault(info.envName(), info.fallback());
            return new EnvView(info.label(), info.envName(), info.secret(), !value.isEmpty(), info.secret() ? null : value);
        }).toList();
        return new IntegrationView(definition.key(), definition.label(), definition.description(), fields, env);
    }

    /** Blank values remove the saved override, so the field falls back to .env. */
    @Transactional
    public void save(String integration, Map<String, String> values, String email) {
        Definition definition = definition(integration);
        for (Field field : definition.fields()) {
            if (!values.containsKey(field.name())) continue;
            String value = normalize(field, values.get(field.name()));
            if (value.isEmpty()) {
                jdbc.update("DELETE FROM integration_settings WHERE integration = ? AND name = ?", integration, field.name());
            } else {
                jdbc.update("""
                    INSERT INTO integration_settings (integration, name, value, updated_by) VALUES (?, ?, ?, ?)
                    ON CONFLICT(integration, name) DO UPDATE SET value = excluded.value,
                      updated_by = excluded.updated_by, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                    """, integration, field.name(), value, email);
            }
        }
    }

    static String normalize(Field field, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        return switch (field.type()) {
            case BOOLEAN -> {
                if (!value.equals("true") && !value.equals("false")) throw invalid(field, "use true ou false");
                yield value;
            }
            case TEXT -> {
                if (field.pattern() != null && !value.matches(field.pattern())) throw invalid(field, "formato inválido");
                yield value;
            }
            case PHONE -> phone(field, value);
            case PHONE_LIST -> String.join(",", Arrays.stream(value.split("[,;\\n]+")).map(String::trim)
                .filter(item -> !item.isEmpty()).map(item -> phone(field, item)).distinct().toList());
            case STATUS_LIST -> {
                List<String> statuses = Arrays.stream(value.split("[,\\s]+")).filter(item -> !item.isBlank()).distinct().toList();
                for (String status : statuses) {
                    if (!List.of(ALL_STATUSES.split(",")).contains(status)) throw invalid(field, "status desconhecido: " + status);
                }
                yield String.join(",", statuses);
            }
        };
    }

    private static String phone(Field field, String value) {
        String digits = value.replaceAll("[\\s()+.-]", "");
        if (!digits.matches("\\d{10,15}")) throw invalid(field, "use DDI + DDD + número, só dígitos (ex.: 5591999990000)");
        return digits;
    }

    private static IllegalArgumentException invalid(Field field, String reason) {
        return new IllegalArgumentException(field.label() + ": " + reason);
    }

    static Definition definition(String integration) {
        Definition definition = DEFINITIONS.get(integration);
        if (definition == null) throw new IllegalArgumentException("Integração desconhecida: " + integration);
        return definition;
    }

    private static Field field(String integration, String name) {
        return definition(integration).fields().stream().filter(field -> field.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Campo desconhecido: " + name));
    }

    private Optional<String> saved(String integration, String name) {
        return jdbc.query("SELECT value FROM integration_settings WHERE integration = ? AND name = ?",
            (rs, row) -> rs.getString(1), integration, name).stream().findFirst();
    }

    private String envOrDefault(String envName, String fallback) {
        String value = environment.getProperty(envName, "").trim();
        return value.isEmpty() ? fallback : value;
    }
}

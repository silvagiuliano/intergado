package br.com.intergado.purchases;

import java.util.Locale;

/** Access profiles, from the least to the most privileged: each one includes the previous. */
public enum Role {
    LEITOR("Leitor", "Consulta painel, fazendas, compras e indicadores."),
    OPERADOR("Operador", "Também cadastra e edita compras e fazendas e altera status."),
    ADMIN("Administrador", "Também gerencia usuários e integrações.");

    public final String label;
    public final String description;

    Role(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public boolean includes(Role other) {
        return compareTo(other) >= 0;
    }

    public static Role parse(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Perfil inválido: use LEITOR, OPERADOR ou ADMIN");
        }
    }

    /**
     * Minimum role for a /api route of this service. Reads are open to every active user;
     * users and integrations are admin-only (they expose configuration); other writes need OPERADOR.
     * Unknown write routes default to ADMIN, so a new endpoint is never open by accident.
     */
    public static Role required(String method, String path) {
        if (path.equals("/api/auth") || path.startsWith("/api/auth/")) return LEITOR;
        if (under(path, "/api/users") || under(path, "/api/integrations")) return ADMIN;
        boolean read = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
        if (read) return LEITOR;
        if (under(path, "/api/purchases") || under(path, "/api/farms")) return OPERADOR;
        return ADMIN;
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}

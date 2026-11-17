package br.com.intergado.purchases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoleTest {
    @Test
    void readsAreOpenToEveryRole() {
        assertEquals(Role.LEITOR, Role.required("GET", "/api/purchases"));
        assertEquals(Role.LEITOR, Role.required("GET", "/api/farms/3"));
        assertEquals(Role.LEITOR, Role.required("GET", "/api/auth/me"));
        assertEquals(Role.LEITOR, Role.required("POST", "/api/auth/logout"));
    }

    @Test
    void purchaseAndFarmWritesNeedOperator() {
        assertEquals(Role.OPERADOR, Role.required("POST", "/api/purchases"));
        assertEquals(Role.OPERADOR, Role.required("PUT", "/api/purchases/7/status"));
        assertEquals(Role.OPERADOR, Role.required("PUT", "/api/farms/1"));
    }

    @Test
    void usersAndIntegrationsAreAdminOnlyEvenForReads() {
        assertEquals(Role.ADMIN, Role.required("GET", "/api/users"));
        assertEquals(Role.ADMIN, Role.required("PUT", "/api/users/a@b.com"));
        assertEquals(Role.ADMIN, Role.required("GET", "/api/integrations"));
        assertEquals(Role.ADMIN, Role.required("POST", "/api/integrations/evolution/test"));
    }

    @Test
    void unknownWritesDefaultToAdminAndPrefixesDoNotLeak() {
        assertEquals(Role.ADMIN, Role.required("DELETE", "/api/something-new"));
        assertEquals(Role.ADMIN, Role.required("POST", "/api/purchasesX"));
        assertEquals(Role.LEITOR, Role.required("GET", "/api/users-export"), "starts with /api/users but is not under it");
    }

    @Test
    void hierarchyAndParsing() {
        assertTrue(Role.ADMIN.includes(Role.OPERADOR));
        assertTrue(Role.OPERADOR.includes(Role.LEITOR));
        assertFalse(Role.LEITOR.includes(Role.OPERADOR));
        assertEquals(Role.OPERADOR, Role.parse(" operador "));
        assertThrows(IllegalArgumentException.class, () -> Role.parse("ROOT"));
    }
}

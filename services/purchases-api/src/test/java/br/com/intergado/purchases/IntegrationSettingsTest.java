package br.com.intergado.purchases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegrationSettingsTest {
    private static IntegrationSettings.Field field(String name) {
        return IntegrationSettings.DEFINITIONS.get("evolution").fields().stream()
            .filter(field -> field.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void groupsAreSplitDeduplicatedAndJoined() {
        assertEquals("120363025246125486@g.us,5591999990000-1600000000@g.us", IntegrationSettings.normalize(field("groups"),
            " 120363025246125486@g.us\n5591999990000-1600000000@g.us, 120363025246125486@g.us "));
    }

    @Test
    void invalidGroupIdIsRejected() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> IntegrationSettings.normalize(field("groups"), "120363025246125486@g.us\nabc@g.us"));
        assertEquals("Grupos de destino: identificador de grupo inválido: abc@g.us", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> IntegrationSettings.normalize(field("groups"), "5591999990000"));
    }

    @Test
    void phonesKeepOnlyDigits() {
        assertEquals("5591999990000,5591999990001",
            IntegrationSettings.normalize(field("testNumbers"), "+55 (91) 99999-0000\n5591999990001"));
    }
}

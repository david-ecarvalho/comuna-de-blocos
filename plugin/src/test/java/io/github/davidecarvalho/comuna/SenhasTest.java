package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SenhasTest {

    @Test
    void confereSoASenhaCerta() {
        String h = Senhas.hash("foice-e-martelo");
        assertTrue(Senhas.confere("foice-e-martelo", h));
        assertFalse(Senhas.confere("foice-e-martelO", h));
        assertFalse(h.contains("foice"), "nunca guarda a senha");
    }

    @Test
    void mesmoSenhaDaHashDiferente() {
        assertNotEquals(Senhas.hash("camarada1"), Senhas.hash("camarada1"), "cada uma com o seu sal");
    }

    @Test
    void recusaSenhaFracaOuONick() {
        assertTrue(Senhas.problema("12345", "Ana").isPresent());
        assertTrue(Senhas.problema("Pirata", "pirata").isPresent());
        assertTrue(Senhas.problema("x".repeat(65), "Ana").isPresent());
        assertTrue(Senhas.problema("vale-trabalho", "Ana").isEmpty());
        assertFalse(Senhas.confere("qualquer", "lixo"));
    }
}

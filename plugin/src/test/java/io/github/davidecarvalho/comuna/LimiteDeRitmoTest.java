package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LimiteDeRitmoTest {

    @Test
    void barraDepoisDoMaximoELiberaQuandoAJanelaPassa() {
        AtomicLong agora = new AtomicLong(0);
        LimiteDeRitmo limite = new LimiteDeRitmo(3, 60_000, agora::get);
        UUID p = UUID.randomUUID();
        assertTrue(limite.tentar(p));
        assertTrue(limite.tentar(p));
        assertTrue(limite.tentar(p));
        assertFalse(limite.tentar(p), "o quarto balde no mesmo minuto");
        agora.set(60_000);
        assertTrue(limite.tentar(p), "um minuto depois");
    }

    @Test
    void cadaJogadorTemOSeuLimite() {
        LimiteDeRitmo limite = new LimiteDeRitmo(1, 60_000, () -> 0);
        assertTrue(limite.tentar(UUID.randomUUID()));
        assertTrue(limite.tentar(UUID.randomUUID()));
    }
}

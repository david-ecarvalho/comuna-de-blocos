package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContribuicaoTest {

    private static final long H = Contribuicao.TICKS_POR_HORA;

    @Test
    void recemChegadoEAtendidoSemTerTrabalhado() {
        assertTrue(Contribuicao.atende(H, null, 2 * H, 2 * H));
    }

    @Test
    void depoisDaChegadaSoQuemContribuiuHaPouco() {
        assertFalse(Contribuicao.atende(3 * H, null, 2 * H, 2 * H), "nunca depositou");
        assertTrue(Contribuicao.atende(3 * H, 2 * H, 2 * H, 2 * H), "depositou há 1 hora de jogo");
        assertFalse(Contribuicao.atende(5 * H, 2 * H, 2 * H, 2 * H), "depositou há 3 horas de jogo");
    }

    @Test
    void quemNaoContribuiNaoEntraNaContaEFicaSabendo() {
        Estoque e = new Estoque();
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        e.depositar(ana, Recurso.TRIGO, 60);
        Necessidades.Resultado r = Necessidades.atender(List.of(
                new Necessidades.Pessoa(ana, 4, false, todasDePedra(), true),
                new Necessidades.Pessoa(beto, 4, false, todasDePedra(), false)), e, 6);
        assertEquals(1, r.entregas().size());
        assertEquals(ana, r.entregas().get(0).quem());
        assertEquals(Set.of(beto), r.semContribuir());
    }

    private static Map<Ferramenta, Ferramenta.Grau> todasDePedra() {
        Map<Ferramenta, Ferramenta.Grau> m = new java.util.EnumMap<>(Ferramenta.class);
        for (Ferramenta f : Ferramenta.values()) {
            m.put(f, Ferramenta.Grau.PEDRA);
        }
        return m;
    }
}

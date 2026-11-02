package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerritorioTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();
    private Territorio t;

    @BeforeEach
    void montar() {
        t = new Territorio();
        assertEquals(Territorio.Resultado.OK, t.registrarBase(ana, "mundo", 0, 0, 10));
        assertEquals(Territorio.Resultado.OK, t.criarColetiva("mina", "mundo", 100, 0, 20, false));
        assertEquals(Territorio.Resultado.OK, t.criarColetiva("fundicao", "mundo", 0, 100, 5, true));
    }

    @Test
    void soODonoConstroiNaBase() {
        assertTrue(t.podeConstruir(ana, "mundo", 5, 5));
        assertFalse(t.podeConstruir(beto, "mundo", 5, 5));
    }

    @Test
    void todosTrabalhamNaAreaColetivaEnaTerraLivre() {
        assertTrue(t.podeConstruir(beto, "mundo", 100, 0));
        assertTrue(t.podeConstruir(beto, "mundo", 50, 50));
    }

    @Test
    void umaBasePorPessoaEsemEncostar() {
        assertEquals(Territorio.Resultado.JA_TEM_BASE, t.registrarBase(ana, "mundo", 500, 500, 10));
        assertEquals(Territorio.Resultado.SOBREPOE, t.registrarBase(beto, "mundo", 15, 0, 10));
        assertEquals(Territorio.Resultado.OK, t.registrarBase(beto, "mundo", 25, 0, 10));
        assertEquals(Territorio.Resultado.OK, t.registrarBase(UUID.randomUUID(), "outro-mundo", 0, 0, 10));
    }

    @Test
    void lavaSoNaPropriaBaseOuNaFundicao() {
        assertTrue(t.podeDespejarLava(ana, "mundo", 0, 0));
        assertFalse(t.podeDespejarLava(beto, "mundo", 0, 0), "base de outra pessoa");
        assertTrue(t.podeDespejarLava(beto, "mundo", 0, 100), "fundição");
        assertFalse(t.podeDespejarLava(beto, "mundo", 100, 0), "mina coletiva sem lava");
        assertFalse(t.podeDespejarLava(beto, "mundo", 50, 50), "terra livre: é onde a lavacast acontece");
    }

    @Test
    void dispensadorSoDespejaLavaNaFundicao() {
        assertTrue(t.podeDispensarLava("mundo", 0, 100));
        assertFalse(t.podeDispensarLava("mundo", 0, 0));
        assertFalse(t.podeDispensarLava("mundo", 50, 50));
    }

    @Test
    void liquidoNaoAtravessaParaDentroDeUmaArea() {
        assertFalse(t.fluxoPermitido("mundo", 11, 0, 10, 0), "de fora para dentro da base");
        assertTrue(t.fluxoPermitido("mundo", 10, 0, 11, 0), "de dentro para fora");
        assertTrue(t.fluxoPermitido("mundo", 5, 0, 6, 0), "dentro da mesma área");
        assertTrue(t.fluxoPermitido("mundo", 50, 50, 51, 50), "terra livre");
    }

    @Test
    void removerBaseLiberaOLugar() {
        assertEquals(Territorio.Resultado.OK, t.removerBase(ana));
        assertTrue(t.podeConstruir(beto, "mundo", 5, 5));
        assertEquals(Territorio.Resultado.NAO_EXISTE, t.removerBase(ana));
    }

    @Test
    void nomeDeAreaColetivaNaoRepete() {
        assertEquals(Territorio.Resultado.NOME_EM_USO, t.criarColetiva("MINA", "mundo", 900, 900, 5, false));
    }
}

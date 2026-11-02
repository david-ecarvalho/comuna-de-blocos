package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NecessidadesTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();
    private final UUID caio = UUID.randomUUID();
    private Estoque e;

    @BeforeEach
    void montar() {
        e = new Estoque();
    }

    private static Necessidades.Pessoa pessoa(UUID id, int fome, boolean temComida, Set<Ferramenta> sem) {
        return new Necessidades.Pessoa(id, fome, temComida, sem);
    }

    private Necessidades.Resultado atender(Necessidades.Pessoa... pessoas) {
        return Necessidades.atender(List.of(pessoas), e, Necessidades.FOME_QUE_TRAVA_A_CORRIDA);
    }

    @Test
    void comidaSoParaQuemNaoConsegueMaisCorrer() {
        e.depositar(ana, Recurso.TRIGO, 30); // 10 pães
        Necessidades.Resultado r = atender(
                pessoa(ana, 7, false, Set.of()), // ainda corre
                pessoa(beto, 6, false, Set.of())); // travou
        assertEquals(1, r.entregas().size());
        assertEquals(beto, r.entregas().get(0).quem());
        assertEquals(List.of(new Estoque.Item("BREAD", 3)), r.entregas().get(0).itens(), "14 pontos até encher");
    }

    @Test
    void quemTemComidaConsigoComeAPropria() {
        e.depositar(ana, Recurso.TRIGO, 30);
        assertTrue(atender(pessoa(beto, 2, true, Set.of())).entregas().isEmpty());
    }

    @Test
    void quemTemMaisFomeComePrimeiro() {
        e.depositar(ana, Recurso.TRIGO, 12); // 4 pães = 20 pontos: dá para um só
        Necessidades.Resultado r = atender(pessoa(ana, 6, false, Set.of()), pessoa(beto, 1, false, Set.of()));
        assertEquals(beto, r.entregas().get(0).quem());
        assertEquals(1, r.comFome(), "Ana espera");
        List<String> frases = Necessidades.frases(r);
        assertEquals("1 camarada está com fome. Deposite comida pronta ou 9 trigos (3 pães) para alimentar.",
                frases.get(0), "Beto levou os 4 pães; Ana precisa de 14 pontos");
    }

    @Test
    void ferramentaQueFaltaVemDoEstoque() {
        e.depositar(ana, Recurso.PEDRA, 3);
        e.depositar(ana, Recurso.MADEIRA, 8);
        Necessidades.Resultado r = atender(
                pessoa(ana, 20, false, EnumSet.of(Ferramenta.PICARETA)),
                pessoa(beto, 20, false, EnumSet.of(Ferramenta.PICARETA, Ferramenta.PA)),
                pessoa(caio, 20, false, EnumSet.of(Ferramenta.PICARETA)));
        assertEquals(1, r.entregas().size(), "3 pedregulhos fazem uma picareta");
        assertEquals(new Estoque.Item("STONE_PICKAXE", 1), r.entregas().get(0).itens().get(0));
        assertEquals(Map.of(Ferramenta.PICARETA, 2, Ferramenta.PA, 1), r.semFerramenta());
        assertEquals(List.of(
                "2 camaradas estão sem picareta de pedra.",
                "1 camarada está sem pá de pedra.",
                "Deposite 7 pedregulhos para ajudar."), Necessidades.frases(r),
                "3 + 3 + 1 de pedra; os 6 gravetos que sobraram cobrem os cabos");
        assertEquals(Map.of(Recurso.PEDRA, 7, Recurso.MADEIRA, 6), r.demanda(), "a reserva que o kit não toca");
    }

    @Test
    void semNecessidadeOLetreiroAgradece() {
        assertEquals(List.of("Ninguém passando necessidade agora. Bom trabalho, camaradas!"),
                Necessidades.frases(atender(pessoa(ana, 20, false, Set.of()))));
    }
}

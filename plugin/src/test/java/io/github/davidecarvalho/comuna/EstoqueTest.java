package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EstoqueTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();
    private Estoque e;

    @BeforeEach
    void montar() {
        e = new Estoque(); // pesos padrão (pedra, madeira e trigo 1; ferro 4; comida 1 por ponto) e 20% para o fundo
    }

    @Test
    void recursoReconheceOsMateriais() {
        assertEquals(new Recurso.Valor(Recurso.MADEIRA, 8), Recurso.de("OAK_LOG").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.MADEIRA, 2), Recurso.de("BIRCH_PLANKS").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.PEDRA, 1), Recurso.de("COBBLESTONE").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.FERRO, 9), Recurso.de("IRON_BLOCK").orElseThrow());
        assertTrue(Recurso.de("BREAD").isEmpty(), "pão é comida pronta, guardada como está");
        assertTrue(Recurso.de("DIAMOND").isEmpty(), "o plano de agora não pede diamante");
        assertEquals("1 tronco", Recurso.MADEIRA.fala(3));
        assertEquals("2 troncos", Recurso.MADEIRA.fala(9));
    }

    @Test
    void depositarDaValesMenosAParteDoFundoSocial() {
        assertEquals(8, e.depositar(ana, Recurso.PEDRA, 10), "10 de trabalho, 2 para o fundo");
        assertEquals(8, e.vales(ana));
        assertEquals(2, e.fundoSocial());
        assertEquals(32, e.depositar(beto, Recurso.FERRO, 10), "ferro pesa 4: 40 de trabalho, 8 para o fundo");
    }

    @Test
    void comidaProntaValePelosPontosDeFome() {
        assertEquals(8, e.depositarComida(ana, "BREAD", 2), "2 pães = 10 pontos, 2 para o fundo");
        assertEquals(2, e.comida("BREAD"));
        assertEquals(0, e.depositarComida(ana, "ROTTEN_FLESH", 5), "carne podre não entra");
        e.depositar(ana, Recurso.TRIGO, 7); // 2 pães, sobra 1 trigo
        assertEquals(10 + 10, e.pontosDeComida());
    }

    @Test
    void alimentarEncheComAMenorSobraEPrimeiroAComidaPronta() {
        e.depositarComida(ana, "COOKED_BEEF", 1); // 8
        e.depositarComida(ana, "BREAD", 1); // 5
        e.depositar(ana, Recurso.TRIGO, 9); // 3 pães do trigo
        List<Estoque.Item> itens = e.alimentar(14).orElseThrow(); // 6 de fome até encher
        assertEquals(List.of(new Estoque.Item("COOKED_BEEF", 1), new Estoque.Item("BREAD", 2)), itens,
                "bife (8), depois o pão pronto e um do trigo (5 + 5)");
        assertEquals(0, e.comida("BREAD"));
        assertEquals(6, e.quantidade(Recurso.TRIGO), "um pão saiu do trigo");
    }

    @Test
    void semComidaBastanteNaoTiraNada() {
        e.depositarComida(ana, "BREAD", 1);
        assertTrue(e.alimentar(14).isEmpty());
        assertEquals(1, e.comida("BREAD"), "espera o estoque cobrir");
    }

    @Test
    void ferramentaPelaNecessidadeSoDeNovoQuandoQuebraOuMorre() {
        e.depositar(ana, Recurso.PEDRA, 20);
        e.depositar(ana, Recurso.MADEIRA, 20);
        assertTrue(e.entregarFerramenta(beto, Ferramenta.PICARETA));
        assertEquals(17, e.quantidade(Recurso.PEDRA));
        assertEquals(18, e.quantidade(Recurso.MADEIRA));
        assertTrue(!e.entregarFerramenta(beto, Ferramenta.PICARETA), "guardar no baú não dá outra");
        e.quebrou(beto, Ferramenta.PICARETA);
        assertTrue(e.entregarFerramenta(beto, Ferramenta.PICARETA));
        e.morreu(beto);
        assertTrue(e.podeReceber(beto, Ferramenta.PICARETA));
    }

    @Test
    void ferramentaDePedraParaCima() {
        assertTrue(Ferramenta.PICARETA.temBoa(List.of("IRON_PICKAXE")));
        assertTrue(!Ferramenta.PICARETA.temBoa(List.of("WOODEN_PICKAXE", "GOLDEN_PICKAXE")));
        assertTrue(!Ferramenta.MACHADO.temBoa(List.of("STONE_PICKAXE")), "picareta não é machado");
        assertEquals(Ferramenta.MACHADO, Ferramenta.deBoa("STONE_AXE").orElseThrow());
        assertTrue(Ferramenta.deBoa("WOODEN_AXE").isEmpty());
    }

    @Test
    void kitCustaOTrabalhoQueLeva() {
        assertEquals(9 + 8, e.custoEmVales(NivelDoKit.PEDRA));
        assertEquals(9 * 4 + 8, e.custoEmVales(NivelDoKit.FERRO));
    }

    @Test
    void trabalhaMaisGanhaMais() {
        e.depositar(ana, Recurso.PEDRA, 30);
        e.depositar(ana, Recurso.MADEIRA, 30); // 60 de trabalho, 48 de vales
        assertEquals(Estoque.Tipo.ENTREGUE, e.retirarKit(ana, Map.of()).tipo());
        assertEquals(48 - 17, e.vales(ana));
        assertEquals(Estoque.Tipo.ENTREGUE, e.retirarKit(ana, Map.of()).tipo(), "ainda tem vales para outro");
        Estoque.Entrega beto0 = e.retirarKit(beto, Map.of());
        assertEquals(Estoque.Tipo.SEM_VALES, beto0.tipo(), "o kit além do básico é trabalhando");
        assertEquals(17, beto0.valesQueFaltam());
    }

    @Test
    void oKitNaoTiraOQueANecessidadePede() {
        e.depositar(ana, Recurso.PEDRA, 9);
        e.depositar(ana, Recurso.MADEIRA, 8);
        e.depositar(ana, Recurso.TRIGO, 100); // vales de sobra
        assertEquals(Estoque.Tipo.FALTA_NO_ESTOQUE, e.retirarKit(ana, Map.of(Recurso.PEDRA, 3)).tipo(),
                "3 pedregulhos estão reservados para a picareta de quem não tem");
    }

    @Test
    void kitDeFerroQuandoOColetivoProduziuFerroBastante() {
        e.depositar(ana, Recurso.MADEIRA, 30);
        assertEquals(NivelDoKit.PEDRA, e.nivelDoColetivo());
        e.depositar(ana, Recurso.FERRO, 64);
        assertEquals(NivelDoKit.FERRO, e.nivelDoColetivo());
        Estoque.Entrega kit = e.retirarKit(ana, Map.of());
        assertEquals(NivelDoKit.FERRO, kit.nivel());
        assertEquals(64 - 9, e.quantidade(Recurso.FERRO));
    }

    @Test
    void semFerroNoEstoqueOcoletivoAindaDaOKitDePedra() {
        e.restaurar(Map.of(Recurso.PEDRA, 9, Recurso.MADEIRA, 8), Map.of(Recurso.FERRO, 64), Map.of(),
                Map.of(ana, 500), Map.of(), 0);
        assertEquals(NivelDoKit.PEDRA, e.retirarKit(ana, Map.of()).nivel());
    }
}

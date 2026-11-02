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
        Ferramenta.Grau pedra = Ferramenta.Grau.PEDRA;
        assertTrue(e.entregarFerramenta(beto, Ferramenta.PICARETA, pedra));
        assertEquals(17, e.quantidade(Recurso.PEDRA));
        assertEquals(18, e.quantidade(Recurso.MADEIRA));
        assertTrue(!e.entregarFerramenta(beto, Ferramenta.PICARETA, pedra), "guardar no baú não dá outra");
        assertTrue(e.podeReceber(beto, Ferramenta.PICARETA, Ferramenta.Grau.FERRO), "a meta de ferro vale para ele");
        e.quebrou(beto, Ferramenta.PICARETA);
        assertTrue(e.entregarFerramenta(beto, Ferramenta.PICARETA, pedra));
        e.morreu(beto);
        assertTrue(e.podeReceber(beto, Ferramenta.PICARETA, pedra));
    }

    @Test
    void ferramentaDePedraParaCima() {
        assertEquals(Ferramenta.Grau.FERRO,
                Ferramenta.PICARETA.melhor(List.of("STONE_PICKAXE", "IRON_PICKAXE")).orElseThrow());
        assertTrue(Ferramenta.PICARETA.melhor(List.of("WOODEN_PICKAXE", "GOLDEN_PICKAXE")).isEmpty());
        assertTrue(Ferramenta.MACHADO.melhor(List.of("STONE_PICKAXE")).isEmpty(), "picareta não é machado");
        assertEquals(new Ferramenta.Qual(Ferramenta.MACHADO, Ferramenta.Grau.PEDRA),
                Ferramenta.de("STONE_AXE").orElseThrow());
        assertTrue(Ferramenta.de("WOODEN_AXE").isEmpty());
        assertEquals(Map.of(Recurso.PEDRA, 2, Recurso.MADEIRA, 1), Ferramenta.ESPADA.custo(Ferramenta.Grau.PEDRA));
    }

    @Test
    void kitCustaOTrabalhoQueLeva() {
        assertTrue(NivelDoKit.PEDRA.ferramentas.contains("STONE_SWORD"), "o kit tem espada");
        assertEquals(11 + 9, e.custoEmVales(NivelDoKit.PEDRA));
        assertEquals(11 * 4 + 9, e.custoEmVales(NivelDoKit.FERRO));
    }

    @Test
    void trabalhaMaisGanhaMais() {
        e.depositar(ana, Recurso.PEDRA, 40);
        e.depositar(ana, Recurso.MADEIRA, 40); // 80 de trabalho, 64 de vales
        assertEquals(Estoque.Tipo.ENTREGUE, e.retirarKit(ana, Map.of()).tipo());
        assertEquals(64 - 20, e.vales(ana));
        assertEquals(Estoque.Tipo.ENTREGUE, e.retirarKit(ana, Map.of()).tipo(), "ainda tem vales para outro");
        Estoque.Entrega beto0 = e.retirarKit(beto, Map.of());
        assertEquals(Estoque.Tipo.SEM_VALES, beto0.tipo(), "o kit além do básico é trabalhando");
        assertEquals(20, beto0.valesQueFaltam());
    }

    @Test
    void oKitNaoTiraOQueANecessidadePede() {
        e.depositar(ana, Recurso.PEDRA, 11);
        e.depositar(ana, Recurso.MADEIRA, 9);
        e.depositar(ana, Recurso.TRIGO, 100); // vales de sobra
        assertEquals(Estoque.Tipo.FALTA_NO_ESTOQUE, e.retirarKit(ana, Map.of(Recurso.PEDRA, 3)).tipo(),
                "3 pedregulhos estão reservados para a picareta de quem não tem");
        assertEquals(Map.of(Recurso.PEDRA, 3), e.faltaParaKit(NivelDoKit.PEDRA, Map.of(Recurso.PEDRA, 3)));
    }

    @Test
    void semMadeiraNaoHaKitEAMensagemDizOQueFalta() {
        e.depositar(ana, Recurso.PEDRA, 47); // o teste do David em 02/11: muita pedra, nenhuma madeira
        Estoque.Entrega r = e.retirarKit(ana, Map.of());
        assertEquals(Estoque.Tipo.FALTA_NO_ESTOQUE, r.tipo());
        assertEquals(Map.of(Recurso.MADEIRA, 9), e.faltaParaKit(r.nivel(), Map.of()));
        assertEquals("2 troncos", Recurso.MADEIRA.fala(9));
    }

    @Test
    void kitDeFerroQuandoOColetivoProduziuFerroBastante() {
        e.depositar(ana, Recurso.MADEIRA, 30);
        assertEquals(NivelDoKit.PEDRA, e.nivelDoColetivo());
        e.depositar(ana, Recurso.FERRO, 64);
        assertEquals(NivelDoKit.FERRO, e.nivelDoColetivo());
        Estoque.Entrega kit = e.retirarKit(ana, Map.of());
        assertEquals(NivelDoKit.FERRO, kit.nivel());
        assertEquals(64 - 11, e.quantidade(Recurso.FERRO));
    }

    @Test
    void semFerroNoEstoqueOcoletivoAindaDaOKitDePedra() {
        e.restaurar(Map.of(Recurso.PEDRA, 11, Recurso.MADEIRA, 9), Map.of(Recurso.FERRO, 64), Map.of(),
                Map.of(ana, 500), Map.of(), 0);
        assertEquals(NivelDoKit.PEDRA, e.retirarKit(ana, Map.of()).nivel());
    }
}

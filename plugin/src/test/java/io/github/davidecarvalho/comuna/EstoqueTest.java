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
        e = new Estoque(); // pesos padrão (pedra, madeira e trigo 1; ferro 4) e 20% para o fundo social
    }

    /** Ana trabalha e deposita o bastante para um kit de pedra e sobra. */
    private void anaTrabalha() {
        e.depositar(ana, Recurso.PEDRA, 30);
        e.depositar(ana, Recurso.MADEIRA, 30);
        e.depositar(ana, Recurso.TRIGO, 60);
    }

    @Test
    void recursoReconheceOsMateriais() {
        assertEquals(new Recurso.Valor(Recurso.MADEIRA, 8), Recurso.de("OAK_LOG").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.MADEIRA, 2), Recurso.de("BIRCH_PLANKS").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.PEDRA, 1), Recurso.de("COBBLESTONE").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.TRIGO, 3), Recurso.de("BREAD").orElseThrow());
        assertEquals(new Recurso.Valor(Recurso.FERRO, 9), Recurso.de("IRON_BLOCK").orElseThrow());
        assertTrue(Recurso.de("DIAMOND").isEmpty(), "o plano de agora não pede diamante");
    }

    @Test
    void depositarDaValesMenosAParteDoFundoSocial() {
        assertEquals(8, e.depositar(ana, Recurso.PEDRA, 10), "10 de trabalho, 2 para o fundo");
        assertEquals(8, e.vales(ana));
        assertEquals(2, e.fundoSocial());
        assertEquals(32, e.depositar(beto, Recurso.FERRO, 10), "ferro pesa 4: 40 de trabalho, 8 para o fundo");
    }

    @Test
    void kitCustaOTrabalhoQueLeva() {
        assertEquals(9 + 8 + 24, e.custoEmVales(NivelDoKit.PEDRA));
        assertEquals(9 * 4 + 8 + 24, e.custoEmVales(NivelDoKit.FERRO));
    }

    @Test
    void trabalhaMaisGanhaMais() {
        anaTrabalha();
        int vales = e.vales(ana); // 120 de trabalho, 96 de vales
        assertEquals(96, vales);
        assertEquals(Estoque.Tipo.ENTREGUE_POR_VALES, e.retirarKit(ana).tipo());
        assertEquals(96 - 41, e.vales(ana));
        assertEquals(Estoque.Tipo.ENTREGUE_POR_VALES, e.retirarKit(ana).tipo(), "ainda tem vales para outro");
    }

    @Test
    void primeiroKitDeQuemChegaSaiDoFundoSocial() {
        anaTrabalha(); // fundo social: 24
        e.depositar(ana, Recurso.PEDRA, 100); // fundo: 44, cobre um kit de 41
        Estoque.Entrega primeira = e.retirarKit(beto);
        assertEquals(Estoque.Tipo.ENTREGUE_PELO_FUNDO, primeira.tipo());
        assertEquals(3, e.fundoSocial());
        Estoque.Entrega segunda = e.retirarKit(beto);
        assertEquals(Estoque.Tipo.SEM_VALES, segunda.tipo(), "do segundo em diante, é trabalhando");
        assertEquals(41, segunda.valesQueFaltam());
    }

    @Test
    void semEstoqueNaoHaKit() {
        e.depositar(ana, Recurso.TRIGO, 200); // muitos vales, mas nada de pedra nem madeira
        assertEquals(Estoque.Tipo.FALTA_NO_ESTOQUE, e.retirarKit(ana).tipo());
    }

    @Test
    void kitDeFerroQuandoOColetivoProduziuFerroBastante() {
        anaTrabalha();
        assertEquals(NivelDoKit.PEDRA, e.nivelDoColetivo());
        e.depositar(ana, Recurso.FERRO, 64);
        assertEquals(NivelDoKit.FERRO, e.nivelDoColetivo());
        Estoque.Entrega kit = e.retirarKit(ana);
        assertEquals(NivelDoKit.FERRO, kit.nivel());
        assertEquals(64 - 9, e.quantidade(Recurso.FERRO));
    }

    @Test
    void semFerroNoEstoqueOcoletivoAindaDaOKitDePedra() {
        anaTrabalha();
        e.depositar(ana, Recurso.FERRO, 64);
        e.restaurar(Map.of(Recurso.PEDRA, 9, Recurso.MADEIRA, 8, Recurso.TRIGO, 24),
                Map.of(Recurso.FERRO, 64), Map.of(ana, 500), java.util.Set.of(), 0);
        assertEquals(NivelDoKit.PEDRA, e.retirarKit(ana).nivel());
    }

    @Test
    void faltasENpc() {
        Map<Recurso, Integer> faltas = e.faltas(2);
        assertEquals(18, faltas.get(Recurso.PEDRA));
        assertEquals(16, faltas.get(Recurso.MADEIRA));
        List<String> frases = Estoque.frases(Map.of(Recurso.MADEIRA, 9), NivelDoKit.PEDRA);
        assertEquals(List.of("Falta madeira para os cabos das ferramentas dos trabalhadores: 2 troncos."), frases);
        assertTrue(Estoque.frases(Map.of(), NivelDoKit.PEDRA).get(0).contains("Bom trabalho"));
    }
}

package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * O estoque coletivo e o vale-trabalho, sem nada do servidor.
 *
 * <p>Primeiro a necessidade: quem está com fome recebe comida e quem não tem a ferramenta da meta do plano (pedra,
 * depois ferro) recebe uma, direto do estoque e sem pagar nada (Necessidades). Depois o trabalho, como na Crítica do Programa de Gotha (Marx, 1875): quem
 * deposita recebe vales pelo trabalho que deu, descontada a parte do fundo social (que é o que fica para a
 * necessidade), e com os vales retira um kit além do básico, que melhora quando o coletivo produziu o bastante
 * (NivelDoKit). O kit nunca tira do estoque o que a necessidade de agora está pedindo.
 */
public final class Estoque {

    private final Map<Recurso, Integer> pesos = new EnumMap<>(Recurso.class);
    private final int pesoComida;
    private final int deducaoPercentual;

    private final Map<Recurso, Integer> quantidades = new EnumMap<>(Recurso.class);
    private final Map<Recurso, Integer> produzido = new EnumMap<>(Recurso.class);
    private final Map<String, Integer> comidas = new TreeMap<>();
    private final Map<UUID, Integer> vales = new HashMap<>();
    private final Map<UUID, Map<Ferramenta, Ferramenta.Grau>> entregues = new HashMap<>();
    private final Map<UUID, Long> contribuicoes = new HashMap<>();
    private int fundoSocial;

    /** pesoComida: vales por ponto de fome da comida pronta (pão = 5 pontos). */
    public Estoque(Map<Recurso, Integer> pesos, int pesoComida, int deducaoPercentual) {
        for (Recurso r : Recurso.values()) {
            this.pesos.put(r, pesos.getOrDefault(r, r.pesoPadrao));
        }
        this.pesoComida = pesoComida;
        this.deducaoPercentual = deducaoPercentual;
    }

    public Estoque() {
        this(Map.of(), 1, 20);
    }

    // ---- leitura e gravação (o PlanoServico chama) ----
    public int quantidade(Recurso r) { return quantidades.getOrDefault(r, 0); }
    public int produzido(Recurso r) { return produzido.getOrDefault(r, 0); }
    public int comida(String material) { return comidas.getOrDefault(material, 0); }
    public Map<String, Integer> comidas() { return comidas; }
    public int vales(UUID quem) { return vales.getOrDefault(quem, 0); }
    public int fundoSocial() { return fundoSocial; }
    public Map<UUID, Integer> todosOsVales() { return vales; }
    public Map<UUID, Map<Ferramenta, Ferramenta.Grau>> entregues() { return entregues; }
    /** Ticks jogados por cada um quando depositou pela última vez (Contribuicao). */
    public Map<UUID, Long> contribuicoes() { return contribuicoes; }
    public Long ultimaContribuicao(UUID quem) { return contribuicoes.get(quem); }
    public void contribuiu(UUID quem, long jogado) { contribuicoes.put(quem, jogado); }

    public void restaurar(Map<Recurso, Integer> quantidades, Map<Recurso, Integer> produzido,
                          Map<String, Integer> comidas, Map<UUID, Integer> vales,
                          Map<UUID, Map<Ferramenta, Ferramenta.Grau>> entregues, int fundoSocial) {
        this.quantidades.clear();
        this.quantidades.putAll(quantidades);
        this.produzido.clear();
        this.produzido.putAll(produzido);
        this.comidas.clear();
        this.comidas.putAll(comidas);
        this.vales.clear();
        this.vales.putAll(vales);
        this.entregues.clear();
        entregues.forEach((u, f) -> {
            Map<Ferramenta, Ferramenta.Grau> m = new EnumMap<>(Ferramenta.class);
            m.putAll(f);
            this.entregues.put(u, m);
        });
        this.fundoSocial = fundoSocial;
    }

    // ---- trabalho ----

    /** Põe no estoque; quem trabalhou recebe vales, e a dedução vai para o fundo social. Devolve os vales ganhos. */
    public int depositar(UUID quem, Recurso r, int unidades) {
        if (unidades <= 0) {
            return 0;
        }
        quantidades.merge(r, unidades, Integer::sum);
        produzido.merge(r, unidades, Integer::sum);
        return creditar(quem, unidades * pesos.get(r));
    }

    /** Comida pronta: guardada como está, para ir inteira para quem tiver fome. Vale pelos pontos de fome. */
    public int depositarComida(UUID quem, String material, int quantidade) {
        var pontos = Comida.pontos(material);
        if (quantidade <= 0 || pontos.isEmpty()) {
            return 0;
        }
        comidas.merge(material, quantidade, Integer::sum);
        return creditar(quem, quantidade * pontos.getAsInt() * pesoComida);
    }

    private int creditar(UUID quem, int trabalho) {
        int deducao = trabalho * deducaoPercentual / 100;
        fundoSocial += deducao;
        vales.merge(quem, trabalho - deducao, Integer::sum);
        return trabalho - deducao;
    }

    // ---- necessidade: comida ----

    /** Pontos de fome que o estoque tem: a comida pronta e o pão que o trigo dá (3 trigos, 1 pão). */
    public int pontosDeComida() {
        int total = (quantidade(Recurso.TRIGO) / Comida.TRIGOS_POR_PAO) * Comida.PONTOS_DO_PAO;
        for (Map.Entry<String, Integer> c : comidas.entrySet()) {
            total += c.getValue() * Comida.pontos(c.getKey()).orElse(0);
        }
        return total;
    }

    public record Item(String material, int quantidade) { }

    /**
     * Separa comida que devolva pelo menos esses pontos de fome, com o mínimo de sobra, e tira do estoque. A comida
     * pronta sai antes do pão feito com o trigo. Se o estoque não cobre, não tira nada.
     */
    public Optional<List<Item>> alimentar(int pontos) {
        if (pontos <= 0 || pontosDeComida() < pontos) {
            return Optional.empty();
        }
        Map<String, Integer> pronta = new TreeMap<>(comidas);
        int paesDoTrigo = quantidade(Recurso.TRIGO) / Comida.TRIGOS_POR_PAO;
        int paesUsados = 0;
        Map<String, Integer> dar = new LinkedHashMap<>();
        int restante = pontos;
        while (restante > 0) {
            // a menor que já cobre o que falta; se nenhuma cobre, a maior que houver
            String escolha = null;
            int valor = 0;
            boolean doTrigo = false;
            for (Map.Entry<String, Integer> c : pronta.entrySet()) {
                if (c.getValue() <= 0) {
                    continue;
                }
                int p = Comida.pontos(c.getKey()).orElse(0);
                if (melhor(p, valor, restante, escolha == null)) {
                    escolha = c.getKey();
                    valor = p;
                }
            }
            if (paesUsados < paesDoTrigo && melhor(Comida.PONTOS_DO_PAO, valor, restante, escolha == null)) {
                escolha = "BREAD";
                valor = Comida.PONTOS_DO_PAO;
                doTrigo = true;
            }
            if (doTrigo) {
                paesUsados++;
            } else {
                pronta.merge(escolha, -1, Integer::sum);
            }
            dar.merge(escolha, 1, Integer::sum);
            restante -= valor;
        }
        pronta.forEach((m, q) -> {
            if (q <= 0) {
                comidas.remove(m);
            } else {
                comidas.put(m, q);
            }
        });
        quantidades.merge(Recurso.TRIGO, -paesUsados * Comida.TRIGOS_POR_PAO, Integer::sum);
        List<Item> itens = new ArrayList<>();
        dar.forEach((m, q) -> itens.add(new Item(m, q)));
        return Optional.of(itens);
    }

    /** Se p é melhor que o atual: cobrir o que falta com a menor sobra; sem nada que cubra, o maior. Empate fica. */
    private static boolean melhor(int p, int atual, int restante, boolean nenhum) {
        if (nenhum) {
            return true;
        }
        boolean cobre = p >= restante;
        boolean atualCobre = atual >= restante;
        if (cobre != atualCobre) {
            return cobre;
        }
        return cobre ? p < atual : p > atual;
    }

    // ---- necessidade: ferramentas ----

    /**
     * Quem já recebeu essa ferramenta pela necessidade só recebe outra quando ela quebrar, quando morrer ou quando a
     * meta subir para um grau melhor que o que recebeu.
     */
    public boolean podeReceber(UUID quem, Ferramenta f, Ferramenta.Grau g) {
        Ferramenta.Grau recebeu = entregues.getOrDefault(quem, Map.of()).get(f);
        return recebeu == null || recebeu.compareTo(g) < 0;
    }

    public boolean cobre(Map<Recurso, Integer> custo) {
        return custo.entrySet().stream().allMatch(c -> quantidade(c.getKey()) >= c.getValue());
    }

    /** Tira do estoque e marca como entregue. Devolve false se o estoque não cobre. */
    public boolean entregarFerramenta(UUID quem, Ferramenta f, Ferramenta.Grau g) {
        if (!podeReceber(quem, f, g) || !cobre(f.custo(g))) {
            return false;
        }
        f.custo(g).forEach((r, c) -> quantidades.merge(r, -c, Integer::sum));
        entregues.computeIfAbsent(quem, u -> new EnumMap<>(Ferramenta.class)).put(f, g);
        return true;
    }

    public void quebrou(UUID quem, Ferramenta f) {
        Map<Ferramenta, Ferramenta.Grau> s = entregues.get(quem);
        if (s != null) {
            s.remove(f);
        }
    }

    public void morreu(UUID quem) {
        entregues.remove(quem);
    }

    // ---- kit por vales ----

    /** Trabalho que o kit leva, em vales. */
    public int custoEmVales(NivelDoKit nivel) {
        int total = 0;
        for (Map.Entry<Recurso, Integer> c : nivel.custo.entrySet()) {
            total += c.getValue() * pesos.get(c.getKey());
        }
        return total;
    }

    /** O maior nível que o coletivo já liberou com o que produziu. */
    public NivelDoKit nivelDoColetivo() {
        NivelDoKit melhor = NivelDoKit.PEDRA;
        for (NivelDoKit n : NivelDoKit.values()) {
            if (n.producaoParaLiberar.entrySet().stream().allMatch(p -> produzido(p.getKey()) >= p.getValue())) {
                melhor = n;
            }
        }
        return melhor;
    }

    /** Se o estoque cobre o kit sem mexer na reserva (o que a necessidade de agora pede). */
    public boolean cobre(NivelDoKit nivel, Map<Recurso, Integer> reserva) {
        return nivel.custo.entrySet().stream().allMatch(c ->
                quantidade(c.getKey()) - reserva.getOrDefault(c.getKey(), 0) >= c.getValue());
    }

    /** O que falta no estoque, fora da reserva, para esse kit. */
    public Map<Recurso, Integer> faltaParaKit(NivelDoKit nivel, Map<Recurso, Integer> reserva) {
        Map<Recurso, Integer> faltas = new EnumMap<>(Recurso.class);
        nivel.custo.forEach((r, c) -> {
            int falta = c + reserva.getOrDefault(r, 0) - quantidade(r);
            if (falta > 0) {
                faltas.put(r, falta);
            }
        });
        return faltas;
    }

    public enum Tipo { ENTREGUE, SEM_VALES, FALTA_NO_ESTOQUE }

    public record Entrega(Tipo tipo, NivelDoKit nivel, int valesQueFaltam) { }

    /**
     * Entrega o melhor kit que o coletivo liberou, o estoque cobre fora da reserva e a pessoa pode pagar; se não der,
     * tenta o nível de baixo.
     */
    public Entrega retirarKit(UUID quem, Map<Recurso, Integer> reserva) {
        NivelDoKit[] niveis = NivelDoKit.values();
        Entrega melhorFalha = null;
        for (int i = nivelDoColetivo().ordinal(); i >= 0; i--) {
            NivelDoKit n = niveis[i];
            if (!cobre(n, reserva)) {
                if (melhorFalha == null) {
                    melhorFalha = new Entrega(Tipo.FALTA_NO_ESTOQUE, n, 0);
                }
                continue;
            }
            int custo = custoEmVales(n);
            if (vales(quem) >= custo) {
                vales.merge(quem, -custo, Integer::sum);
                n.custo.forEach((r, c) -> quantidades.merge(r, -c, Integer::sum));
                return new Entrega(Tipo.ENTREGUE, n, 0);
            }
            if (melhorFalha == null || melhorFalha.tipo() == Tipo.FALTA_NO_ESTOQUE) {
                melhorFalha = new Entrega(Tipo.SEM_VALES, n, custo - vales(quem));
            }
        }
        return melhorFalha;
    }
}

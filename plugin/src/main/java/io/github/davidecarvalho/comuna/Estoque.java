package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O estoque coletivo e o vale-trabalho, sem nada do servidor.
 *
 * <p>Como na Crítica do Programa de Gotha (Marx, 1875): quem trabalha recebe um certificado do trabalho que deu, depois
 * de descontada a parte do fundo social, e retira do estoque comum bens que custam a mesma quantidade de trabalho.
 * Aqui: depositar dá vales (unidades × peso do recurso), uma parte vai para o fundo social, e o kit custa em vales o
 * trabalho que leva. O primeiro kit de quem chega sai do fundo social. O kit melhora quando o coletivo produziu o
 * bastante (NivelDoKit).
 */
public final class Estoque {

    private final Map<Recurso, Integer> pesos = new EnumMap<>(Recurso.class);
    private final int deducaoPercentual;

    private final Map<Recurso, Integer> quantidades = new EnumMap<>(Recurso.class);
    private final Map<Recurso, Integer> produzido = new EnumMap<>(Recurso.class);
    private final Map<UUID, Integer> vales = new HashMap<>();
    private final Set<UUID> jaRecebeuKit = new HashSet<>();
    private int fundoSocial;

    public Estoque(Map<Recurso, Integer> pesos, int deducaoPercentual) {
        for (Recurso r : Recurso.values()) {
            this.pesos.put(r, pesos.getOrDefault(r, r.pesoPadrao));
        }
        this.deducaoPercentual = deducaoPercentual;
    }

    public Estoque() {
        this(Map.of(), 20);
    }

    // ---- leitura e gravação (o Armazém chama) ----
    public int quantidade(Recurso r) { return quantidades.getOrDefault(r, 0); }
    public int produzido(Recurso r) { return produzido.getOrDefault(r, 0); }
    public int vales(UUID quem) { return vales.getOrDefault(quem, 0); }
    public int fundoSocial() { return fundoSocial; }
    public Map<UUID, Integer> todosOsVales() { return vales; }
    public Set<UUID> jaRecebeuKit() { return jaRecebeuKit; }

    public void restaurar(Map<Recurso, Integer> quantidades, Map<Recurso, Integer> produzido,
                          Map<UUID, Integer> vales, Set<UUID> jaRecebeu, int fundoSocial) {
        this.quantidades.clear();
        this.quantidades.putAll(quantidades);
        this.produzido.clear();
        this.produzido.putAll(produzido);
        this.vales.clear();
        this.vales.putAll(vales);
        this.jaRecebeuKit.clear();
        this.jaRecebeuKit.addAll(jaRecebeu);
        this.fundoSocial = fundoSocial;
    }

    /** Trabalho que o kit leva, em vales. */
    public int custoEmVales(NivelDoKit nivel) {
        int total = 0; // o pão já entra no custo, como trigo
        for (Map.Entry<Recurso, Integer> c : nivel.custo.entrySet()) {
            total += c.getValue() * pesos.get(c.getKey());
        }
        return total;
    }

    /** Põe no estoque; quem trabalhou recebe vales, e a dedução vai para o fundo social. Devolve os vales ganhos. */
    public int depositar(UUID quem, Recurso r, int unidades) {
        if (unidades <= 0) {
            return 0;
        }
        quantidades.merge(r, unidades, Integer::sum);
        produzido.merge(r, unidades, Integer::sum);
        int trabalho = unidades * pesos.get(r);
        int deducao = trabalho * deducaoPercentual / 100;
        fundoSocial += deducao;
        vales.merge(quem, trabalho - deducao, Integer::sum);
        return trabalho - deducao;
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

    public boolean cobre(NivelDoKit nivel) {
        return nivel.custo.entrySet().stream().allMatch(c -> quantidade(c.getKey()) >= c.getValue());
    }

    public enum Tipo { ENTREGUE_POR_VALES, ENTREGUE_PELO_FUNDO, SEM_VALES, FALTA_NO_ESTOQUE }

    public record Entrega(Tipo tipo, NivelDoKit nivel, int valesQueFaltam) { }

    /**
     * Entrega o melhor kit que o coletivo liberou, o estoque cobre e a pessoa pode pagar; se não der, tenta o nível
     * de baixo. O primeiro kit de quem chega sai do fundo social.
     */
    public Entrega retirarKit(UUID quem) {
        NivelDoKit[] niveis = NivelDoKit.values();
        Entrega melhorFalha = null;
        for (int i = nivelDoColetivo().ordinal(); i >= 0; i--) {
            NivelDoKit n = niveis[i];
            if (!cobre(n)) {
                if (melhorFalha == null) {
                    melhorFalha = new Entrega(Tipo.FALTA_NO_ESTOQUE, n, 0);
                }
                continue;
            }
            int custo = custoEmVales(n);
            if (vales(quem) >= custo) {
                vales.merge(quem, -custo, Integer::sum);
                tirar(n, quem);
                return new Entrega(Tipo.ENTREGUE_POR_VALES, n, 0);
            }
            if (!jaRecebeuKit.contains(quem) && fundoSocial >= custo) {
                fundoSocial -= custo;
                tirar(n, quem);
                return new Entrega(Tipo.ENTREGUE_PELO_FUNDO, n, 0);
            }
            if (melhorFalha == null || melhorFalha.tipo() == Tipo.FALTA_NO_ESTOQUE) {
                melhorFalha = new Entrega(Tipo.SEM_VALES, n, custo - vales(quem));
            }
        }
        return melhorFalha;
    }

    private void tirar(NivelDoKit n, UUID quem) {
        n.custo.forEach((r, c) -> quantidades.merge(r, -c, Integer::sum));
        jaRecebeuKit.add(quem);
    }

    /** Quanto falta de cada recurso para dar o kit do nível do coletivo a quantos precisam dele agora. */
    public Map<Recurso, Integer> faltas(int precisando) {
        NivelDoKit n = nivelDoColetivo();
        Map<Recurso, Integer> faltas = new EnumMap<>(Recurso.class);
        n.custo.forEach((r, c) -> {
            int falta = precisando * c - quantidade(r);
            if (falta > 0) {
                faltas.put(r, falta);
            }
        });
        return faltas;
    }

    /** O que o NPC da Comissão do Plano fala. */
    public static List<String> frases(Map<Recurso, Integer> faltas, NivelDoKit nivel) {
        List<String> frases = new ArrayList<>();
        for (Recurso r : Recurso.values()) {
            Integer falta = faltas.get(r);
            if (falta == null) {
                continue;
            }
            String para = switch (r) {
                case MADEIRA -> "Falta madeira para os cabos das ferramentas dos trabalhadores";
                case PEDRA -> "Falta pedra para as picaretas dos trabalhadores";
                case FERRO -> "Falta ferro para as picaretas dos trabalhadores";
                case TRIGO -> "Falta trigo para o pão dos trabalhadores";
            };
            frases.add(para + ": " + r.paraFala(falta) + " " + r.unidadeDeFala + ".");
        }
        if (frases.isEmpty()) {
            frases.add("O estoque cobre o kit " + nivel.nome + " de quem precisa. Bom trabalho, camaradas!");
        }
        return frases;
    }
}

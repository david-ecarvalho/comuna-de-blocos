package io.github.davidecarvalho.comuna;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As ferramentas que todo mundo tem que ter, de pedra para cima. Quem não tem recebe uma de pedra pela necessidade,
 * sem pagar vale. Custo: a pedra da cabeça e 2 gravetos do cabo.
 */
public enum Ferramenta {
    PICARETA("PICKAXE", "picareta de pedra", 3),
    MACHADO("AXE", "machado de pedra", 3),
    PA("SHOVEL", "pá de pedra", 1),
    ENXADA("HOE", "enxada de pedra", 2);

    /** De pedra para cima. Ouro quebra minério como madeira, então não conta. */
    static final List<String> BOAS = List.of("STONE", "COPPER", "IRON", "DIAMOND", "NETHERITE");

    public final String sufixo;
    public final String nome;
    public final Map<Recurso, Integer> custo;

    Ferramenta(String sufixo, String nome, int pedra) {
        this.sufixo = sufixo;
        this.nome = nome;
        this.custo = Map.of(Recurso.PEDRA, pedra, Recurso.MADEIRA, 2);
    }

    /** O material da que a necessidade entrega. */
    public String material() {
        return "STONE_" + sufixo;
    }

    /** Se entre esses materiais (o inventário) há uma desse tipo, de pedra para cima. */
    public boolean temBoa(Collection<String> materiais) {
        return BOAS.stream().anyMatch(t -> materiais.contains(t + "_" + sufixo));
    }

    /** Qual ferramenta, de pedra para cima, é esse material ("STONE_AXE" é MACHADO; "WOODEN_AXE" não conta). */
    public static Optional<Ferramenta> deBoa(String material) {
        for (Ferramenta f : values()) {
            for (String t : BOAS) {
                if (material.equals(t + "_" + f.sufixo)) {
                    return Optional.of(f);
                }
            }
        }
        return Optional.empty();
    }
}

package io.github.davidecarvalho.comuna;

import java.util.List;
import java.util.Map;

/**
 * O kit que se compra com vales-trabalho, além do que a necessidade já dá. Cada nível tem as ferramentas (nomes de
 * Material), o custo em recursos e quanto a comunidade precisa já ter produzido, no total, para liberá-lo.
 */
public enum NivelDoKit {
    PEDRA("de pedra",
            List.of("STONE_PICKAXE", "STONE_AXE", "STONE_SHOVEL", "STONE_HOE"),
            Map.of(Recurso.PEDRA, 3 + 3 + 1 + 2, Recurso.MADEIRA, 4 * 2),
            Map.of()),
    FERRO("de ferro",
            List.of("IRON_PICKAXE", "IRON_AXE", "IRON_SHOVEL", "IRON_HOE"),
            Map.of(Recurso.FERRO, 3 + 3 + 1 + 2, Recurso.MADEIRA, 4 * 2),
            Map.of(Recurso.FERRO, 64));

    public final String nome;
    public final List<String> ferramentas;
    public final Map<Recurso, Integer> custo;
    public final Map<Recurso, Integer> producaoParaLiberar;

    NivelDoKit(String nome, List<String> ferramentas, Map<Recurso, Integer> custo,
               Map<Recurso, Integer> producaoParaLiberar) {
        this.nome = nome;
        this.ferramentas = ferramentas;
        this.custo = custo;
        this.producaoParaLiberar = producaoParaLiberar;
    }
}

package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * O kit que se compra com vales-trabalho, além do que a necessidade já dá: todas as ferramentas (picareta, espada,
 * machado, pá e enxada) de um grau. Cada nível tem quanto a comunidade precisa já ter produzido, no total, para
 * liberá-lo.
 */
public enum NivelDoKit {
    PEDRA(Ferramenta.Grau.PEDRA, Map.of()),
    FERRO(Ferramenta.Grau.FERRO, Map.of(Recurso.FERRO, 64));

    public final String nome;
    public final List<String> ferramentas;
    public final Map<Recurso, Integer> custo;
    public final Map<Recurso, Integer> producaoParaLiberar;

    NivelDoKit(Ferramenta.Grau grau, Map<Recurso, Integer> producaoParaLiberar) {
        this.nome = grau.nome;
        List<String> lista = new ArrayList<>();
        Map<Recurso, Integer> soma = new EnumMap<>(Recurso.class);
        for (Ferramenta f : Ferramenta.values()) {
            lista.add(f.material(grau));
            f.custo(grau).forEach((r, c) -> soma.merge(r, c, Integer::sum));
        }
        this.ferramentas = List.copyOf(lista);
        this.custo = Map.copyOf(soma);
        this.producaoParaLiberar = producaoParaLiberar;
    }
}

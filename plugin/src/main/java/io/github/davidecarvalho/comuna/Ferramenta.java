package io.github.davidecarvalho.comuna;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * As ferramentas que todo mundo tem que ter. A meta do plano sobe junto com o coletivo: primeiro todos com as de
 * pedra; quando ninguém mais está sem, a meta passa a ser as de ferro. Custo: a cabeça (pedra ou ferro) e os
 * gravetos do cabo.
 */
public enum Ferramenta {
    PICARETA("PICKAXE", "picareta", 3, 2),
    ESPADA("SWORD", "espada", 2, 1),
    MACHADO("AXE", "machado", 3, 2),
    PA("SHOVEL", "pá", 1, 2),
    ENXADA("HOE", "enxada", 2, 2);

    /** Os graus do jogo, do mais fraco para o mais forte. Ouro quebra minério como madeira, então não conta. */
    public enum Grau {
        PEDRA("STONE", "de pedra", Recurso.PEDRA),
        COBRE("COPPER", "de cobre", null),
        FERRO("IRON", "de ferro", Recurso.FERRO),
        DIAMANTE("DIAMOND", "de diamante", null),
        NETHERITE("NETHERITE", "de netherita", null);

        public final String prefixo;
        public final String nome;
        /** Do que a cabeça é feita, nos graus que o plano entrega (pedra e ferro). */
        public final Recurso cabeca;

        Grau(String prefixo, String nome, Recurso cabeca) {
            this.prefixo = prefixo;
            this.nome = nome;
            this.cabeca = cabeca;
        }
    }

    /** As metas do plano, na ordem. */
    public static final Grau[] METAS = {Grau.PEDRA, Grau.FERRO};

    public final String sufixo;
    public final String nome;
    public final int cabecas;
    public final int gravetos;

    Ferramenta(String sufixo, String nome, int cabecas, int gravetos) {
        this.sufixo = sufixo;
        this.nome = nome;
        this.cabecas = cabecas;
        this.gravetos = gravetos;
    }

    public String material(Grau g) {
        return g.prefixo + "_" + sufixo;
    }

    public String nome(Grau g) {
        return nome + " " + g.nome;
    }

    public Map<Recurso, Integer> custo(Grau g) {
        return Map.of(g.cabeca, cabecas, Recurso.MADEIRA, gravetos);
    }

    /** O melhor grau dessa ferramenta entre esses materiais (o inventário); vazio se não tem nenhuma de pedra para cima. */
    public Optional<Grau> melhor(Collection<String> materiais) {
        Grau melhor = null;
        for (Grau g : Grau.values()) {
            if (materiais.contains(material(g))) {
                melhor = g;
            }
        }
        return Optional.ofNullable(melhor);
    }

    /** Se tem uma desse grau ou melhor. */
    public boolean temAoMenos(Collection<String> materiais, Grau g) {
        return melhor(materiais).map(m -> m.compareTo(g) >= 0).orElse(false);
    }

    public record Qual(Ferramenta ferramenta, Grau grau) { }

    /** Qual ferramenta, de pedra para cima, é esse material ("STONE_AXE" é machado de pedra; "WOODEN_AXE" não conta). */
    public static Optional<Qual> de(String material) {
        for (Ferramenta f : values()) {
            for (Grau g : Grau.values()) {
                if (material.equals(f.material(g))) {
                    return Optional.of(new Qual(f, g));
                }
            }
        }
        return Optional.empty();
    }
}

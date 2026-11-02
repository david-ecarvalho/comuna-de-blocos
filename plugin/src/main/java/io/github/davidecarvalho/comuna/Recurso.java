package io.github.davidecarvalho.comuna;

import java.util.Optional;

/**
 * O que o plano precisa, contado numa unidade só por recurso: pedra em pedregulhos, madeira em gravetos, trigo em
 * trigos, ferro em lingotes. Recebe o nome do material (Material.name()) para ficar sem nada do servidor. Comida pronta
 * não é recurso: fica na Comida.
 *
 * <p>O peso é o trabalho que uma unidade leva, em vales-trabalho por unidade. Começa assim e a assembleia pode mudar
 * (config.yml).
 */
public enum Recurso {
    PEDRA("pedra", "pedregulho", "pedregulhos", 1),
    MADEIRA("madeira", "tronco", "troncos", 1),
    TRIGO("trigo", "trigo", "trigos", 1),
    FERRO("ferro", "lingote de ferro", "lingotes de ferro", 4);

    public final String nome;
    public final String unidadeDeFala;
    public final String unidadesDeFala;
    public final int pesoPadrao;

    Recurso(String nome, String unidadeDeFala, String unidadesDeFala, int pesoPadrao) {
        this.nome = nome;
        this.unidadeDeFala = unidadeDeFala;
        this.unidadesDeFala = unidadesDeFala;
        this.pesoPadrao = pesoPadrao;
    }

    /** Gravetos que saem de um tronco: tronco = 4 tábuas, e 2 tábuas fazem 4 gravetos (tábua = 2 gravetos). */
    public static final int GRAVETOS_POR_TRONCO = 8;

    /** Quanto um item desse material vale no estoque, e em qual recurso. */
    public record Valor(Recurso recurso, int unidades) { }

    public static Optional<Valor> de(String material) {
        if (material.equals("COBBLESTONE") || material.equals("COBBLED_DEEPSLATE") || material.equals("BLACKSTONE")) {
            return Optional.of(new Valor(PEDRA, 1));
        }
        if (material.endsWith("_LOG") || material.endsWith("_WOOD") || material.endsWith("_STEM")
                || material.endsWith("_HYPHAE")) {
            return Optional.of(new Valor(MADEIRA, GRAVETOS_POR_TRONCO));
        }
        if (material.endsWith("_PLANKS")) {
            return Optional.of(new Valor(MADEIRA, 2));
        }
        return switch (material) {
            case "STICK" -> Optional.of(new Valor(MADEIRA, 1));
            case "WHEAT" -> Optional.of(new Valor(TRIGO, 1));
            case "IRON_INGOT", "RAW_IRON" -> Optional.of(new Valor(FERRO, 1));
            case "IRON_BLOCK", "RAW_IRON_BLOCK" -> Optional.of(new Valor(FERRO, 9));
            default -> Optional.empty();
        };
    }

    /** Converte unidades internas na unidade que o NPC fala (madeira: troncos, arredondando para cima). */
    public int paraFala(int unidades) {
        return this == MADEIRA ? (unidades + GRAVETOS_POR_TRONCO - 1) / GRAVETOS_POR_TRONCO : unidades;
    }

    /** "1 tronco", "3 troncos". */
    public String fala(int unidades) {
        int n = paraFala(unidades);
        return n + " " + (n == 1 ? unidadeDeFala : unidadesDeFala);
    }
}

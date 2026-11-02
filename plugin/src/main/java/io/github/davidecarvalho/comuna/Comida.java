package io.github.davidecarvalho.comuna;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Comida pronta que o estoque aceita, com os pontos de fome que cada uma devolve (os do jogo). Fica de fora o que faz
 * mal ou tem efeito (carne podre, olho de aranha, frango cru, baiacu, fruta do coro) e a maçã dourada, que é mais
 * equipamento do que comida.
 */
public final class Comida {

    private Comida() { }

    public static final int PONTOS_DO_PAO = 5;
    public static final int TRIGOS_POR_PAO = 3;

    private static final Map<String, Integer> PONTOS = Map.ofEntries(
            Map.entry("BREAD", PONTOS_DO_PAO),
            Map.entry("BAKED_POTATO", 5),
            Map.entry("COOKED_BEEF", 8),
            Map.entry("COOKED_PORKCHOP", 8),
            Map.entry("COOKED_MUTTON", 6),
            Map.entry("COOKED_CHICKEN", 6),
            Map.entry("COOKED_RABBIT", 5),
            Map.entry("COOKED_COD", 5),
            Map.entry("COOKED_SALMON", 6),
            Map.entry("CARROT", 3),
            Map.entry("APPLE", 4),
            Map.entry("GOLDEN_CARROT", 6),
            Map.entry("MELON_SLICE", 2),
            Map.entry("SWEET_BERRIES", 2),
            Map.entry("GLOW_BERRIES", 2),
            Map.entry("PUMPKIN_PIE", 8),
            Map.entry("COOKIE", 2),
            Map.entry("BEETROOT", 1),
            Map.entry("BEETROOT_SOUP", 6),
            Map.entry("MUSHROOM_STEW", 6),
            Map.entry("RABBIT_STEW", 10),
            Map.entry("DRIED_KELP", 1));

    public static OptionalInt pontos(String material) {
        Integer p = PONTOS.get(material);
        return p == null ? OptionalInt.empty() : OptionalInt.of(p);
    }

    public static boolean eComida(String material) {
        return PONTOS.containsKey(material);
    }
}

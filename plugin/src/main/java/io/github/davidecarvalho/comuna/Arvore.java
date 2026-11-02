package io.github.davidecarvalho.comuna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * O tronco inteiro de uma árvore, sem nada do servidor: os blocos de tronco ligados ao que foi cortado, inclusive na
 * diagonal (galho de acácia e de carvalho grande), só do corte para cima e até um limite.
 */
public final class Arvore {

    private Arvore() { }

    public record Pos(int x, int y, int z) { }

    /** Os troncos ligados a "corte" (sem ele), em ordem de distância. */
    public static List<Pos> troncoAcima(Pos corte, Predicate<Pos> eTronco, int limite) {
        List<Pos> achados = new ArrayList<>();
        Set<Pos> vistos = new HashSet<>();
        Deque<Pos> fila = new ArrayDeque<>();
        vistos.add(corte);
        fila.add(corte);
        while (!fila.isEmpty()) {
            Pos p = fila.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Pos v = new Pos(p.x() + dx, p.y() + dy, p.z() + dz);
                        if (v.y() < corte.y() || !vistos.add(v) || !eTronco.test(v)) {
                            continue;
                        }
                        achados.add(v);
                        if (achados.size() >= limite) {
                            return achados;
                        }
                        fila.add(v);
                    }
                }
            }
        }
        return achados;
    }
}

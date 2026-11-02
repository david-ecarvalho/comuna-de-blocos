package io.github.davidecarvalho.comuna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArvoreTest {

    private static Arvore.Pos p(int x, int y, int z) {
        return new Arvore.Pos(x, y, z);
    }

    @Test
    void pegaOTroncoEOGalhoNaDiagonal() {
        Set<Arvore.Pos> troncos = Set.of(p(0, 0, 0), p(0, 1, 0), p(0, 2, 0), p(1, 3, 1), p(2, 4, 2));
        List<Arvore.Pos> achados = Arvore.troncoAcima(p(0, 0, 0), troncos::contains, 200);
        assertEquals(4, achados.size(), "tudo menos o bloco cortado");
        assertEquals(p(0, 1, 0), achados.get(0), "o mais perto primeiro");
    }

    @Test
    void naoDesceENaoPassaDoLimite() {
        Set<Arvore.Pos> troncos = Set.of(p(0, -1, 0), p(0, 0, 0), p(0, 1, 0), p(0, 2, 0), p(0, 3, 0));
        assertTrue(Arvore.troncoAcima(p(0, 0, 0), troncos::contains, 200).stream().noneMatch(q -> q.y() < 0),
                "cortar no meio não derruba o que está abaixo");
        assertEquals(2, Arvore.troncoAcima(p(0, 0, 0), troncos::contains, 2).size());
    }
}

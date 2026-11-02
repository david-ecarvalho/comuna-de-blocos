package io.github.davidecarvalho.comuna;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** No máximo {@code maximo} ações por jogador dentro de {@code janelaMs}. O relógio é injetado para o teste. */
public final class LimiteDeRitmo {

    private final int maximo;
    private final long janelaMs;
    private final LongSupplier relogio;
    private final Map<UUID, Deque<Long>> historico = new HashMap<>();

    public LimiteDeRitmo(int maximo, long janelaMs, LongSupplier relogio) {
        this.maximo = maximo;
        this.janelaMs = janelaMs;
        this.relogio = relogio;
    }

    /** true se a ação cabe no limite (e já conta); false se passou. */
    public boolean tentar(UUID jogador) {
        long agora = relogio.getAsLong();
        Deque<Long> fila = historico.computeIfAbsent(jogador, k -> new ArrayDeque<>());
        while (!fila.isEmpty() && agora - fila.peekFirst() >= janelaMs) {
            fila.pollFirst();
        }
        if (fila.size() >= maximo) {
            return false;
        }
        fila.addLast(agora);
        return true;
    }

    public void esquecer(UUID jogador) {
        historico.remove(jogador);
    }
}

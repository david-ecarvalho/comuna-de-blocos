package io.github.davidecarvalho.comuna;

/**
 * Quem a Comissão do Plano atende pela necessidade. Nas primeiras horas de jogo, todo mundo (o recém-chegado ainda
 * não pôde trabalhar: o fundo social cobre, como na Crítica do Programa de Gotha). Depois, só quem depositou alguma
 * coisa nas últimas horas de jogo. Conta tempo jogado, não tempo de relógio: quem fica dias fora não perde nada.
 */
public final class Contribuicao {

    private Contribuicao() { }

    public static final long TICKS_POR_HORA = 20L * 60 * 60;

    /**
     * jogado: ticks jogados no total; ultima: ticks jogados quando depositou pela última vez (nulo se nunca);
     * chegada e janela: em ticks.
     */
    public static boolean atende(long jogado, Long ultima, long chegada, long janela) {
        if (jogado < chegada) {
            return true;
        }
        return ultima != null && jogado - ultima <= janela;
    }
}

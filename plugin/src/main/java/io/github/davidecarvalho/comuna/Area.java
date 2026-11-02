package io.github.davidecarvalho.comuna;

import java.util.UUID;

/**
 * Um retângulo do mapa (altura inteira, do fundo ao céu).
 *
 * <p>BASE é a propriedade pessoal de um jogador: só o dono constrói e quebra. COLETIVA é meio de produção (mina,
 * fazenda, fábrica): todos trabalham nela, ninguém é dono. A lava só pode ser despejada na própria base ou numa área
 * coletiva liberada para isso (a fundição).
 */
public record Area(String id, Tipo tipo, UUID dono, String mundo, int minX, int minZ, int maxX, int maxZ,
                   boolean lavaPermitida) {

    public enum Tipo { BASE, COLETIVA }

    public Area {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("canto mínimo depois do máximo");
        }
        if (tipo == Tipo.BASE && dono == null) {
            throw new IllegalArgumentException("base sem dono");
        }
    }

    /** Área quadrada de lado 2*raio+1 em volta de (cx, cz). */
    public static Area emVolta(String id, Tipo tipo, UUID dono, String mundo, int cx, int cz, int raio,
                               boolean lavaPermitida) {
        return new Area(id, tipo, dono, mundo, cx - raio, cz - raio, cx + raio, cz + raio, lavaPermitida);
    }

    public boolean contem(String mundo, int x, int z) {
        return this.mundo.equals(mundo) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean sobrepoe(Area outra) {
        return mundo.equals(outra.mundo) && minX <= outra.maxX && outra.minX <= maxX
                && minZ <= outra.maxZ && outra.minZ <= maxZ;
    }
}

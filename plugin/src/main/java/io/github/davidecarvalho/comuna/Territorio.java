package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** As regras do território, sem nada do servidor: dá para testar com JUnit puro. */
public final class Territorio {

    public enum Resultado { OK, JA_TEM_BASE, SOBREPOE, NOME_EM_USO, NAO_EXISTE }

    private final List<Area> areas = new ArrayList<>();

    public List<Area> areas() {
        return Collections.unmodifiableList(areas);
    }

    public void carregar(List<Area> salvas) {
        areas.clear();
        areas.addAll(salvas);
    }

    public Optional<Area> areaEm(String mundo, int x, int z) {
        return areas.stream().filter(a -> a.contem(mundo, x, z)).findFirst();
    }

    public Optional<Area> baseDe(UUID jogador) {
        return areas.stream().filter(a -> a.tipo() == Area.Tipo.BASE && jogador.equals(a.dono())).findFirst();
    }

    public Resultado registrarBase(UUID dono, String mundo, int cx, int cz, int raio) {
        if (baseDe(dono).isPresent()) {
            return Resultado.JA_TEM_BASE;
        }
        return adicionar(Area.emVolta("base-" + dono, Area.Tipo.BASE, dono, mundo, cx, cz, raio, true));
    }

    public Resultado removerBase(UUID dono) {
        return areas.removeIf(a -> a.tipo() == Area.Tipo.BASE && dono.equals(a.dono()))
                ? Resultado.OK : Resultado.NAO_EXISTE;
    }

    public Resultado criarColetiva(String nome, String mundo, int cx, int cz, int raio, boolean lavaPermitida) {
        if (areas.stream().anyMatch(a -> a.id().equalsIgnoreCase(nome))) {
            return Resultado.NOME_EM_USO;
        }
        return adicionar(Area.emVolta(nome, Area.Tipo.COLETIVA, null, mundo, cx, cz, raio, lavaPermitida));
    }

    public Resultado removerColetiva(String nome) {
        return areas.removeIf(a -> a.tipo() == Area.Tipo.COLETIVA && a.id().equalsIgnoreCase(nome))
                ? Resultado.OK : Resultado.NAO_EXISTE;
    }

    private Resultado adicionar(Area nova) {
        if (areas.stream().anyMatch(nova::sobrepoe)) {
            return Resultado.SOBREPOE;
        }
        areas.add(nova);
        return Resultado.OK;
    }

    /** Construir e quebrar: livre fora das áreas e nas coletivas; na base, só o dono. */
    public boolean podeConstruir(UUID jogador, String mundo, int x, int z) {
        return areaEm(mundo, x, z)
                .map(a -> a.tipo() == Area.Tipo.COLETIVA || jogador.equals(a.dono()))
                .orElse(true);
    }

    /**
     * Lava (defesa contra lavacast): só na própria base ou numa área coletiva liberada (a fundição). Fora das áreas,
     * não: é ali que a lavacast cobre o mapa de pedregulho.
     */
    public boolean podeDespejarLava(UUID jogador, String mundo, int x, int z) {
        return areaEm(mundo, x, z)
                .map(a -> a.tipo() == Area.Tipo.BASE ? jogador.equals(a.dono()) : a.lavaPermitida())
                .orElse(false);
    }

    /** Lava sem jogador (dispensador): só numa área coletiva liberada. */
    public boolean podeDispensarLava(String mundo, int x, int z) {
        return areaEm(mundo, x, z).map(a -> a.tipo() == Area.Tipo.COLETIVA && a.lavaPermitida()).orElse(false);
    }

    /** Líquido que escorre não atravessa fronteira para dentro de uma base ou área coletiva. */
    public boolean fluxoPermitido(String mundo, int xDe, int zDe, int xPara, int zPara) {
        Optional<Area> destino = areaEm(mundo, xPara, zPara);
        return destino.isEmpty() || destino.equals(areaEm(mundo, xDe, zDe));
    }
}

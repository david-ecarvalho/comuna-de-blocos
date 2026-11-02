package io.github.davidecarvalho.comuna;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Guarda as áreas em territorio.yml, na pasta do plugin. */
final class Armazem {

    private final File arquivo;

    Armazem(File pasta) {
        this.arquivo = new File(pasta, "territorio.yml");
    }

    List<Area> ler() {
        List<Area> areas = new ArrayList<>();
        if (!arquivo.exists()) {
            return areas;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection raiz = yml.getConfigurationSection("areas");
        if (raiz == null) {
            return areas;
        }
        for (String id : raiz.getKeys(false)) {
            ConfigurationSection s = raiz.getConfigurationSection(id);
            String dono = s.getString("dono");
            areas.add(new Area(s.getString("id", id), Area.Tipo.valueOf(s.getString("tipo")),
                    dono == null ? null : UUID.fromString(dono), s.getString("mundo"),
                    s.getInt("minX"), s.getInt("minZ"), s.getInt("maxX"), s.getInt("maxZ"),
                    s.getBoolean("lava")));
        }
        return areas;
    }

    void salvar(List<Area> areas) throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (Area a : areas) {
            String p = "areas.a" + i++ + ".";
            yml.set(p + "id", a.id());
            yml.set(p + "tipo", a.tipo().name());
            yml.set(p + "dono", a.dono() == null ? null : a.dono().toString());
            yml.set(p + "mundo", a.mundo());
            yml.set(p + "minX", a.minX());
            yml.set(p + "minZ", a.minZ());
            yml.set(p + "maxX", a.maxX());
            yml.set(p + "maxZ", a.maxZ());
            yml.set(p + "lava", a.lavaPermitida());
        }
        yml.save(arquivo);
    }
}

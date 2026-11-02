package io.github.davidecarvalho.comuna;

import java.io.File;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Prazos de guarda do parecer jurídico de 02/11/2026 (LGPD, minimização): o registro de construção do CoreProtect
 * fica 90 dias, e os logs do servidor (entrada, saída e chat), 30. Roda na subida e uma vez por dia.
 */
final class Limpeza {

    private final JavaPlugin plugin;
    private final int diasCoreProtect;
    private final int diasLogs;

    Limpeza(JavaPlugin plugin, int diasCoreProtect, int diasLogs) {
        this.plugin = plugin;
        this.diasCoreProtect = diasCoreProtect;
        this.diasLogs = diasLogs;
    }

    void agendar() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::limpar, 20L * 60, 20L * 60 * 60 * 24);
    }

    void limpar() {
        if (Bukkit.getPluginManager().isPluginEnabled("CoreProtect")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co purge t:" + diasCoreProtect + "d");
        }
        File[] logs = new File(Bukkit.getWorldContainer(), "logs").listFiles((d, n) -> n.endsWith(".log.gz"));
        long limite = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(diasLogs);
        int apagados = 0;
        for (File f : logs == null ? new File[0] : logs) {
            if (f.lastModified() < limite && f.delete()) {
                apagados++;
            }
        }
        plugin.getLogger().info("Limpeza: CoreProtect com mais de " + diasCoreProtect + " dias e " + apagados
                + " logs com mais de " + diasLogs + " dias.");
    }
}

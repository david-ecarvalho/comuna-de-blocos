package io.github.davidecarvalho.comuna;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A Guarda Popular: golens de ferro que defendem a praça do spawn dos monstros. Ficam na
 * praça (quem sai volta), não atacam trabalhador, trabalhador não fere a guarda, e quem morre volta em até 10 segundos
 * sem soltar ferro (senão a guarda vira fazenda de ferro).
 */
final class GuardaPopular implements Listener {

    private static final String NOME = "Guarda Popular";

    private final NamespacedKey marca;
    private final int quantos;

    GuardaPopular(JavaPlugin plugin, int quantos) {
        this.marca = new NamespacedKey(plugin, "guarda-popular");
        this.quantos = quantos;
    }

    private boolean eDaGuarda(Entity e) {
        return e instanceof IronGolem && e.getPersistentDataContainer().has(marca);
    }

    private static boolean naPraca(Location l, Location spawn) {
        return Math.abs(l.getBlockX() - spawn.getBlockX()) <= PlanoServico.PERTO_DO_SPAWN
                && Math.abs(l.getBlockZ() - spawn.getBlockZ()) <= PlanoServico.PERTO_DO_SPAWN;
    }

    private static Location pontoDaPraca(World mundo) {
        Location s = mundo.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return PlanoServico.noChao(s.clone().add(r.nextInt(-8, 9), 0, r.nextInt(-8, 9)));
    }

    /** Só mexe quando a praça está carregada (tem gente por perto), para não contar errado nem duplicar a guarda. */
    void manter() {
        World mundo = Bukkit.getWorlds().get(0);
        Location spawn = mundo.getSpawnLocation();
        if (!mundo.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
            return;
        }
        List<IronGolem> guarda = mundo.getEntitiesByClass(IronGolem.class).stream()
                .filter(this::eDaGuarda).toList();
        for (int i = 0; i < guarda.size(); i++) {
            IronGolem g = guarda.get(i);
            if (i >= quantos) {
                g.remove();
            } else if (!naPraca(g.getLocation(), spawn)) {
                g.teleport(pontoDaPraca(mundo));
            }
        }
        for (int i = guarda.size(); i < quantos; i++) {
            mundo.spawn(pontoDaPraca(mundo), IronGolem.class, g -> {
                g.setPlayerCreated(true); // golem feito por gente não ataca gente
                g.setPersistent(true);
                g.setRemoveWhenFarAway(false);
                g.customName(Component.text(NOME, NamedTextColor.RED));
                g.setCustomNameVisible(true);
                g.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    void aoFerirAGuarda(EntityDamageByEntityEvent e) {
        if (!eDaGuarda(e.getEntity())) {
            return;
        }
        Entity quem = e.getDamager();
        if (quem instanceof Projectile pr && pr.getShooter() instanceof Entity atirador) {
            quem = atirador;
        }
        if (quem instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void aoMirar(EntityTargetLivingEntityEvent e) {
        if (eDaGuarda(e.getEntity()) && e.getTarget() instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    void aoMorrer(EntityDeathEvent e) {
        if (eDaGuarda(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}

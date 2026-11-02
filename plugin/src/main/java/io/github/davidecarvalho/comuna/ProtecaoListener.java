package io.github.davidecarvalho.comuna;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Liga as regras do Territorio aos eventos do jogo. */
final class ProtecaoListener implements Listener {

    static final String PERMISSAO_ADMIN = "comuna.admin";

    private final Territorio territorio;
    private final LimiteDeRitmo ritmoDaLava;

    ProtecaoListener(Territorio territorio, LimiteDeRitmo ritmoDaLava) {
        this.territorio = territorio;
        this.ritmoDaLava = ritmoDaLava;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void aoQuebrar(BlockBreakEvent e) {
        if (!pode(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            avisar(e.getPlayer(), "Essa base é de outra pessoa.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void aoColocar(BlockPlaceEvent e) {
        if (!pode(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            avisar(e.getPlayer(), "Essa base é de outra pessoa.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void aoEsvaziarBalde(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Block alvo = e.getBlock();
        if (p.hasPermission(PERMISSAO_ADMIN)) {
            return;
        }
        if (e.getBucket() == Material.LAVA_BUCKET) {
            if (!territorio.podeDespejarLava(p.getUniqueId(), alvo.getWorld().getName(), alvo.getX(), alvo.getZ())) {
                e.setCancelled(true);
                avisar(p, "Lava só na sua base ou na fundição coletiva.");
                return;
            }
            if (!ritmoDaLava.tentar(p.getUniqueId())) {
                e.setCancelled(true);
                avisar(p, "Calma: muitos baldes de lava em pouco tempo.");
                return;
            }
        }
        if (!pode(p, alvo)) {
            e.setCancelled(true);
            avisar(p, "Essa base é de outra pessoa.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void aoDispensar(BlockDispenseEvent e) {
        if (e.getItem().getType() != Material.LAVA_BUCKET
                || !(e.getBlock().getBlockData() instanceof Directional d)) {
            return;
        }
        Block alvo = e.getBlock().getRelative(d.getFacing());
        if (!territorio.podeDispensarLava(alvo.getWorld().getName(), alvo.getX(), alvo.getZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void aoEscorrer(BlockFromToEvent e) {
        Block de = e.getBlock();
        Block para = e.getToBlock();
        if (!territorio.fluxoPermitido(de.getWorld().getName(), de.getX(), de.getZ(), para.getX(), para.getZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    void aoSair(PlayerQuitEvent e) {
        ritmoDaLava.esquecer(e.getPlayer().getUniqueId());
    }

    private boolean pode(Player p, Block b) {
        return p.hasPermission(PERMISSAO_ADMIN)
                || territorio.podeConstruir(p.getUniqueId(), b.getWorld().getName(), b.getX(), b.getZ());
    }

    private static void avisar(Player p, String texto) {
        p.sendActionBar(Component.text(texto, NamedTextColor.RED));
    }
}

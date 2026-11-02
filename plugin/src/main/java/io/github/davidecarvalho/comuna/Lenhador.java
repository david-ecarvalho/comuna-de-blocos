package io.github.davidecarvalho.comuna;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Derruba a árvore inteira quando alguém corta um tronco com machado (agachado, corta um bloco só). Só árvore de
 * verdade, com folha natural em volta: casa de tronco não cai inteira. Respeita as bases (não corta o tronco que
 * fica no terreno de outra pessoa) e gasta o machado a cada tronco.
 */
final class Lenhador implements Listener {

    private static final int LIMITE = 200;
    private static final BlockFace[] EM_VOLTA = {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST};

    private final Territorio territorio;

    Lenhador(Territorio territorio) {
        this.territorio = territorio;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void aoCortar(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block corte = e.getBlock();
        if (p.isSneaking() || p.getGameMode() != GameMode.SURVIVAL || !Tag.LOGS.isTagged(corte.getType())
                || !machado(p.getInventory().getItemInMainHand())) {
            return;
        }
        Material tipo = corte.getType();
        World mundo = corte.getWorld();
        List<Arvore.Pos> tronco = Arvore.troncoAcima(new Arvore.Pos(corte.getX(), corte.getY(), corte.getZ()),
                q -> mundo.getBlockAt(q.x(), q.y(), q.z()).getType() == tipo, LIMITE);
        if (tronco.stream().noneMatch(q -> temFolhaNatural(mundo.getBlockAt(q.x(), q.y(), q.z())))) {
            return;
        }
        boolean admin = p.hasPermission(ProtecaoListener.PERMISSAO_ADMIN);
        for (Arvore.Pos q : tronco) {
            if (!admin && !territorio.podeConstruir(p.getUniqueId(), mundo.getName(), q.x(), q.z())) {
                continue;
            }
            ItemStack naMao = p.getInventory().getItemInMainHand();
            if (!machado(naMao)) {
                return; // o machado quebrou
            }
            mundo.getBlockAt(q.x(), q.y(), q.z()).breakNaturally(naMao);
            naMao.damage(1, p);
        }
    }

    private static boolean machado(ItemStack i) {
        return i.getType().name().endsWith("_AXE");
    }

    private static boolean temFolhaNatural(Block b) {
        for (BlockFace f : EM_VOLTA) {
            if (b.getRelative(f).getBlockData() instanceof Leaves folha && !folha.isPersistent()) {
                return true;
            }
        }
        return false;
    }
}

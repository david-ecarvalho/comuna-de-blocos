package io.github.davidecarvalho.comuna;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A Comissão do Plano: o NPC da área central que diz o que falta no estoque coletivo e entrega o kit do trabalhador
 * em troca de vales-trabalho. É um aldeão do próprio jogo, parado e invulnerável, com um letreiro (TextDisplay).
 */
final class PlanoServico implements Listener {

    private static final String NOME = "Comissão do Plano";

    private final JavaPlugin plugin;
    private final Estoque estoque;
    private final File arquivo;
    private final NamespacedKey marca;
    private UUID npc;
    private UUID letreiro;

    PlanoServico(JavaPlugin plugin, Estoque estoque) {
        this.plugin = plugin;
        this.estoque = estoque;
        this.arquivo = new File(plugin.getDataFolder(), "estoque.yml");
        this.marca = new NamespacedKey(plugin, "comissao-do-plano");
        ler();
    }

    /** Quem está online sem nenhuma picareta: são esses que precisam de kit agora. */
    private static int precisandoDeKit() {
        return (int) Bukkit.getOnlinePlayers().stream().filter(p -> {
            for (ItemStack i : p.getInventory().getContents()) {
                if (i != null && i.getType().name().endsWith("_PICKAXE")) {
                    return false;
                }
            }
            return true;
        }).count();
    }

    List<String> frasesAgora() {
        return Estoque.frases(estoque.faltas(precisandoDeKit()), estoque.nivelDoColetivo());
    }

    void colocarNpc(Location onde) {
        removerNpc();
        Villager v = onde.getWorld().spawn(onde, Villager.class, a -> {
            a.setAI(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setPersistent(true);
            a.setRemoveWhenFarAway(false);
            a.customName(Component.text(NOME, NamedTextColor.RED));
            a.setCustomNameVisible(true);
            a.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });
        TextDisplay t = onde.getWorld().spawn(onde.clone().add(0, 2.7, 0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });
        npc = v.getUniqueId();
        letreiro = t.getUniqueId();
        atualizarLetreiro();
        salvar();
    }

    void removerNpc() {
        for (UUID id : new UUID[] {npc, letreiro}) {
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        npc = null;
        letreiro = null;
        salvar();
    }

    void atualizarLetreiro() {
        if (letreiro != null && Bukkit.getEntity(letreiro) instanceof TextDisplay t) {
            t.text(Component.text("O QUE FALTA NO ESTOQUE (kit " + estoque.nivelDoColetivo().nome + ")\n",
                            NamedTextColor.GOLD)
                    .append(Component.text(String.join("\n", frasesAgora()), NamedTextColor.WHITE)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    void aoFalarComONpc(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(marca)) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Estoque.Entrega r = estoque.retirarKit(p.getUniqueId());
        switch (r.tipo()) {
            case ENTREGUE_POR_VALES, ENTREGUE_PELO_FUNDO -> {
                darKit(p, r.nivel());
                p.sendMessage(Component.text("Kit " + r.nivel().nome + " entregue"
                        + (r.tipo() == Estoque.Tipo.ENTREGUE_PELO_FUNDO
                                ? " pelo fundo social: é o seu primeiro, bem-vindo, camarada!"
                                : ". Vales que sobraram: " + estoque.vales(p.getUniqueId()) + "."),
                        NamedTextColor.GREEN));
                salvar();
                atualizarLetreiro();
            }
            case SEM_VALES -> p.sendMessage(Component.text("Faltam " + r.valesQueFaltam() + " vales-trabalho para o kit "
                    + r.nivel().nome + ". Trabalhe e deposite com /estoque depositar.", NamedTextColor.YELLOW));
            case FALTA_NO_ESTOQUE -> {
                p.sendMessage(Component.text("O estoque ainda não fecha um kit:", NamedTextColor.RED));
                frasesAgora().forEach(f -> p.sendMessage(Component.text(" - " + f, NamedTextColor.GRAY)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void aoAtacarONpc(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(marca)) {
            e.setCancelled(true);
        }
    }

    private static void darKit(Player p, NivelDoKit nivel) {
        for (String f : nivel.ferramentas) {
            dar(p, new ItemStack(Material.valueOf(f)));
        }
        dar(p, new ItemStack(Material.BREAD, NivelDoKit.PAES));
    }

    private static void dar(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(sobra -> p.getWorld().dropItem(p.getLocation(), sobra));
    }

    /** Deposita o item da mão (ou todos do mesmo tipo, com "tudo"). Devolve a mensagem para o jogador. */
    Component depositar(Player p, boolean tudo) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        var valor = Recurso.de(mao.getType().name());
        if (mao.getType().isAir() || valor.isEmpty()) {
            return Component.text("O plano de agora precisa de pedra, madeira, trigo e ferro. Segure um desses.",
                    NamedTextColor.RED);
        }
        Material tipo = mao.getType();
        int itens = 0;
        if (tudo) {
            for (ItemStack i : p.getInventory().getStorageContents()) {
                if (i != null && i.getType() == tipo) {
                    itens += i.getAmount();
                }
            }
            p.getInventory().remove(tipo);
        } else {
            itens = mao.getAmount();
            p.getInventory().setItemInMainHand(null);
        }
        int ganhos = estoque.depositar(p.getUniqueId(), valor.get().recurso(), itens * valor.get().unidades());
        salvar();
        atualizarLetreiro();
        return Component.text(itens + " de " + tipo.name().toLowerCase().replace('_', ' ') + " no estoque coletivo: +"
                + ganhos + " vales-trabalho (total " + estoque.vales(p.getUniqueId()) + ").", NamedTextColor.GREEN);
    }

    Component resumo(Player p) {
        NivelDoKit n = estoque.nivelDoColetivo();
        Component c = Component.text("ESTOQUE COLETIVO (kit " + n.nome + ", custa " + estoque.custoEmVales(n)
                + " vales)", NamedTextColor.GOLD);
        for (Recurso r : Recurso.values()) {
            c = c.append(Component.newline()).append(Component.text(" " + r.nome + ": "
                    + r.paraFala(estoque.quantidade(r)) + " " + r.unidadeDeFala, NamedTextColor.WHITE));
        }
        c = c.append(Component.newline()).append(Component.text(" fundo social: " + estoque.fundoSocial()
                + " vales", NamedTextColor.WHITE));
        c = c.append(Component.newline()).append(Component.text("Seus vales-trabalho: "
                + estoque.vales(p.getUniqueId()), NamedTextColor.AQUA));
        for (String f : frasesAgora()) {
            c = c.append(Component.newline()).append(Component.text(" " + f, NamedTextColor.GRAY));
        }
        return c;
    }

    private void ler() {
        if (!arquivo.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        Map<Recurso, Integer> qtd = new EnumMap<>(Recurso.class);
        Map<Recurso, Integer> prod = new EnumMap<>(Recurso.class);
        for (Recurso r : Recurso.values()) {
            qtd.put(r, y.getInt("estoque." + r.name(), 0));
            prod.put(r, y.getInt("produzido." + r.name(), 0));
        }
        Map<UUID, Integer> vales = new HashMap<>();
        ConfigurationSection v = y.getConfigurationSection("vales");
        if (v != null) {
            v.getKeys(false).forEach(k -> vales.put(UUID.fromString(k), v.getInt(k)));
        }
        Set<UUID> recebeu = new HashSet<>();
        y.getStringList("ja-recebeu-kit").forEach(s -> recebeu.add(UUID.fromString(s)));
        estoque.restaurar(qtd, prod, vales, recebeu, y.getInt("fundo-social", 0));
        String n = y.getString("npc");
        String l = y.getString("letreiro");
        npc = n == null ? null : UUID.fromString(n);
        letreiro = l == null ? null : UUID.fromString(l);
    }

    void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Recurso r : Recurso.values()) {
            y.set("estoque." + r.name(), estoque.quantidade(r));
            y.set("produzido." + r.name(), estoque.produzido(r));
        }
        estoque.todosOsVales().forEach((u, q) -> y.set("vales." + u, q));
        y.set("ja-recebeu-kit", estoque.jaRecebeuKit().stream().map(UUID::toString).toList());
        y.set("fundo-social", estoque.fundoSocial());
        y.set("npc", npc == null ? null : npc.toString());
        y.set("letreiro", letreiro == null ? null : letreiro.toString());
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().severe("Não salvei estoque.yml: " + e.getMessage());
        }
    }
}

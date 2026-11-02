package io.github.davidecarvalho.comuna;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A Comissão do Plano: o NPC do spawn. A cada 10 segundos olha a fome e o inventário de quem está online, entrega
 * direto no inventário o que a necessidade pede e o estoque cobre (Necessidades), e o letreiro diz o que falta.
 * Clicar nele troca vales-trabalho pelo kit além do básico. É um aldeão do próprio jogo, parado e invulnerável.
 */
final class PlanoServico implements Listener {

    private static final String NOME = "Comissão do Plano";
    /** Até quantos blocos do spawn a Comissão pode ficar. */
    static final int PERTO_DO_SPAWN = 16;

    private final JavaPlugin plugin;
    private final Estoque estoque;
    private final int fomeQuePedeComida;
    private final File arquivo;
    private final NamespacedKey marca;
    private UUID npc;
    private UUID letreiro;
    private Location ondeEsta;
    private Necessidades.Resultado ultimo = new Necessidades.Resultado(List.of(), 0, 0, Map.of(), Map.of(), Map.of());

    PlanoServico(JavaPlugin plugin, Estoque estoque, int fomeQuePedeComida) {
        this.plugin = plugin;
        this.estoque = estoque;
        this.fomeQuePedeComida = fomeQuePedeComida;
        this.arquivo = new File(plugin.getDataFolder(), "estoque.yml");
        this.marca = new NamespacedKey(plugin, "comissao-do-plano");
        ler();
    }

    // ---- a necessidade ----

    private Necessidades.Pessoa pessoa(Player p) {
        List<String> materiais = new ArrayList<>();
        boolean temComida = false;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null) {
                materiais.add(i.getType().name());
                temComida |= Comida.eComida(i.getType().name());
            }
        }
        Set<Ferramenta> sem = EnumSet.noneOf(Ferramenta.class);
        for (Ferramenta f : Ferramenta.values()) {
            if (!f.temBoa(materiais)) {
                sem.add(f);
            }
        }
        return new Necessidades.Pessoa(p.getUniqueId(), p.getFoodLevel(), temComida, sem);
    }

    /** Olha quem está online no modo sobrevivência, entrega o que o estoque cobre e atualiza o letreiro. */
    void atenderNecessidades() {
        List<Necessidades.Pessoa> pessoas = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .map(this::pessoa)
                .toList();
        ultimo = Necessidades.atender(pessoas, estoque, fomeQuePedeComida);
        for (Necessidades.Entrega en : ultimo.entregas()) {
            Player p = Bukkit.getPlayer(en.quem());
            if (p == null) {
                continue;
            }
            Component msg = Component.text("A Comissão do Plano mandou ");
            boolean primeiro = true;
            for (Estoque.Item it : en.itens()) {
                Material m = Material.valueOf(it.material());
                dar(p, new ItemStack(m, it.quantidade()));
                msg = msg.append(Component.text((primeiro ? "" : ", ") + it.quantidade() + " "))
                        .append(Component.translatable(m.translationKey()));
                primeiro = false;
            }
            p.sendMessage(msg.append(Component.text(en.motivo() == Necessidades.Motivo.FOME
                    ? ": você estava com fome." : ": você não tinha.")).append(Component.text(
                    " Veio do trabalho de todos.")).color(NamedTextColor.GREEN));
        }
        if (!ultimo.entregas().isEmpty()) {
            salvar();
        }
        atualizarLetreiro();
    }

    List<String> frasesAgora() {
        return Necessidades.frases(ultimo);
    }

    @EventHandler
    void aoQuebrar(PlayerItemBreakEvent e) {
        Ferramenta.deBoa(e.getBrokenItem().getType().name()).ifPresent(f -> {
            estoque.quebrou(e.getPlayer().getUniqueId(), f);
            salvar();
        });
    }

    @EventHandler
    void aoMorrer(PlayerDeathEvent e) {
        if (!e.getKeepInventory()) {
            estoque.morreu(e.getEntity().getUniqueId());
            salvar();
        }
    }

    // ---- o NPC ----

    private static Location noChao(Location l) {
        return l.getWorld().getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }

    private static boolean pertoDoSpawn(Location l) {
        if (l == null || l.getWorld() == null) {
            return false;
        }
        Location s = l.getWorld().getSpawnLocation();
        return l.getWorld().equals(Bukkit.getWorlds().get(0))
                && Math.abs(l.getBlockX() - s.getBlockX()) <= PERTO_DO_SPAWN
                && Math.abs(l.getBlockZ() - s.getBlockZ()) <= PERTO_DO_SPAWN;
    }

    /** Na subida do servidor: se a Comissão não está no spawn, vai para lá. */
    void garantirNoSpawn() {
        if (npc != null && pertoDoSpawn(ondeEsta)) {
            return;
        }
        World mundo = Bukkit.getWorlds().get(0);
        colocarNpc(noChao(mundo.getSpawnLocation()));
        plugin.getLogger().info("Comissão do Plano colocada no spawn.");
    }

    /** Devolve false se o lugar está longe do spawn. */
    boolean colocarNpc(Location onde) {
        if (!pertoDoSpawn(onde)) {
            return false;
        }
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
        ondeEsta = onde.clone();
        atualizarLetreiro();
        salvar();
        return true;
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
        ondeEsta = null;
        salvar();
    }

    /** Comissão antiga que estava num pedaço do mundo descarregado: sai quando o pedaço carrega. */
    @EventHandler
    void aoCarregarEntidades(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(marca)
                    && !en.getUniqueId().equals(npc) && !en.getUniqueId().equals(letreiro)) {
                en.remove();
            }
        }
    }

    void atualizarLetreiro() {
        if (letreiro != null && Bukkit.getEntity(letreiro) instanceof TextDisplay t) {
            t.text(Component.text("COMISSÃO DO PLANO: O QUE FALTA\n", NamedTextColor.GOLD)
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
        atenderNecessidades(); // quem clicou com fome ou sem ferramenta já recebe aqui
        Estoque.Entrega r = estoque.retirarKit(p.getUniqueId(), ultimo.demanda());
        switch (r.tipo()) {
            case ENTREGUE -> {
                for (String f : r.nivel().ferramentas) {
                    dar(p, new ItemStack(Material.valueOf(f)));
                }
                p.sendMessage(Component.text("Kit " + r.nivel().nome + " entregue pelos seus vales-trabalho. Sobraram "
                        + estoque.vales(p.getUniqueId()) + ".", NamedTextColor.GREEN));
                salvar();
                atualizarLetreiro();
            }
            case SEM_VALES -> p.sendMessage(Component.text("O kit " + r.nivel().nome + " custa "
                    + estoque.custoEmVales(r.nivel()) + " vales-trabalho e faltam " + r.valesQueFaltam()
                    + ". Deposite com /estoque depositar.", NamedTextColor.YELLOW));
            case FALTA_NO_ESTOQUE -> {
                p.sendMessage(Component.text(ultimo.demanda().isEmpty()
                        ? "O estoque ainda não fecha o kit " + r.nivel().nome + "."
                        : "A necessidade vem antes: o estoque está guardado para quem não tem ferramenta.",
                        NamedTextColor.RED));
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

    private static void dar(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(sobra -> p.getWorld().dropItem(p.getLocation(), sobra));
    }

    // ---- depósito e resumo ----

    /** Deposita o item da mão (ou todos do mesmo tipo, com "tudo"). Devolve a mensagem para o jogador. */
    Component depositar(Player p, boolean tudo) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        String nome = mao.getType().name();
        var valor = Recurso.de(nome);
        boolean comida = Comida.eComida(nome);
        if (mao.getType().isAir() || (valor.isEmpty() && !comida)) {
            return Component.text("O plano de agora precisa de pedra, madeira, trigo, ferro e comida pronta. "
                    + "Segure um desses.", NamedTextColor.RED);
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
        int ganhos = comida ? estoque.depositarComida(p.getUniqueId(), nome, itens)
                : estoque.depositar(p.getUniqueId(), valor.get().recurso(), itens * valor.get().unidades());
        salvar();
        atenderNecessidades(); // se alguém esperava por isso, recebe agora
        return Component.text(itens + " de ").append(Component.translatable(tipo.translationKey()))
                .append(Component.text(" no estoque coletivo: +" + ganhos + " vales-trabalho (total "
                        + estoque.vales(p.getUniqueId()) + ")."))
                .color(NamedTextColor.GREEN);
    }

    Component resumo(Player p) {
        NivelDoKit n = estoque.nivelDoColetivo();
        Component c = Component.text("ESTOQUE COLETIVO", NamedTextColor.GOLD);
        for (Recurso r : Recurso.values()) {
            c = c.append(Component.newline()).append(Component.text(" " + r.nome + ": "
                    + r.fala(estoque.quantidade(r)), NamedTextColor.WHITE));
        }
        c = c.append(Component.newline()).append(Component.text(" comida: " + estoque.pontosDeComida()
                + " pontos de fome (com o pão que o trigo dá)", NamedTextColor.WHITE));
        c = c.append(Component.newline()).append(Component.text(" fundo social (fica para a necessidade): "
                + estoque.fundoSocial() + " vales", NamedTextColor.WHITE));
        c = c.append(Component.newline()).append(Component.text("Seus vales-trabalho: "
                + estoque.vales(p.getUniqueId()) + " (kit " + n.nome + ": " + estoque.custoEmVales(n) + ")",
                NamedTextColor.AQUA));
        for (String f : frasesAgora()) {
            c = c.append(Component.newline()).append(Component.text(" " + f, NamedTextColor.GRAY));
        }
        return c;
    }

    // ---- estoque.yml ----

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
        Map<String, Integer> comidas = new HashMap<>();
        ConfigurationSection cs = y.getConfigurationSection("comida");
        if (cs != null) {
            cs.getKeys(false).forEach(k -> comidas.put(k, cs.getInt(k)));
        }
        Map<UUID, Integer> vales = new HashMap<>();
        ConfigurationSection v = y.getConfigurationSection("vales");
        if (v != null) {
            v.getKeys(false).forEach(k -> vales.put(UUID.fromString(k), v.getInt(k)));
        }
        Map<UUID, Set<Ferramenta>> entregues = new HashMap<>();
        ConfigurationSection en = y.getConfigurationSection("entregues");
        if (en != null) {
            for (String k : en.getKeys(false)) {
                Set<Ferramenta> s = new HashSet<>();
                en.getStringList(k).forEach(f -> s.add(Ferramenta.valueOf(f)));
                entregues.put(UUID.fromString(k), s);
            }
        }
        estoque.restaurar(qtd, prod, comidas, vales, entregues, y.getInt("fundo-social", 0));
        String n = y.getString("npc");
        String l = y.getString("letreiro");
        npc = n == null ? null : UUID.fromString(n);
        letreiro = l == null ? null : UUID.fromString(l);
        ondeEsta = y.getLocation("npc-local");
    }

    void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Recurso r : Recurso.values()) {
            y.set("estoque." + r.name(), estoque.quantidade(r));
            y.set("produzido." + r.name(), estoque.produzido(r));
        }
        estoque.comidas().forEach((m, q) -> y.set("comida." + m, q));
        estoque.todosOsVales().forEach((u, q) -> y.set("vales." + u, q));
        estoque.entregues().forEach((u, s) -> y.set("entregues." + u, s.stream().map(Enum::name).toList()));
        y.set("fundo-social", estoque.fundoSocial());
        y.set("npc", npc == null ? null : npc.toString());
        y.set("letreiro", letreiro == null ? null : letreiro.toString());
        y.set("npc-local", ondeEsta);
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().severe("Não salvei estoque.yml: " + e.getMessage());
        }
    }
}

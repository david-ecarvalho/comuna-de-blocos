package io.github.davidecarvalho.comuna;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * O login próprio do servidor (sem a verificação de conta da Mojang, qualquer um poderia entrar com o nick de outra
 * pessoa). Na primeira vez, /registrar senha senha; depois, /entrar senha. Até entrar, a pessoa não anda, não fala,
 * não mexe em nada, não toma dano e não é atendida pela Comissão. 60 segundos sem entrar ou 5 senhas erradas: sai do
 * servidor, e depois de 5 erradas só tenta de novo em 1 minuto.
 *
 * <p>Os comandos de senha são tratados aqui e cancelados, para não chegarem ao registro de comandos de nenhum plugin;
 * o registro de comandos do servidor fica desligado no spigot.yml. Nenhuma senha vai para log.
 */
final class Portaria implements Listener {

    private static final int SEGUNDOS_PARA_ENTRAR = 60;
    private static final int TENTATIVAS = 5;
    private static final long ESPERA_DEPOIS_DE_ERRAR = 60_000;

    private final JavaPlugin plugin;
    private final File arquivo;
    private final Map<UUID, String> senhas = new ConcurrentHashMap<>();
    private final Set<UUID> dentro = ConcurrentHashMap.newKeySet();
    private final Set<UUID> conferindo = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> erros = new HashMap<>();
    private final Map<String, Long> bloqueadoAte = new HashMap<>();

    Portaria(JavaPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "senhas.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        y.getKeys(false).forEach(k -> senhas.put(UUID.fromString(k), y.getString(k)));
    }

    boolean entrou(UUID quem) {
        return dentro.contains(quem);
    }

    private synchronized void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        senhas.forEach((u, h) -> y.set(u.toString(), h));
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().severe("Não salvei senhas.yml: " + e.getMessage());
        }
    }

    private static void dizer(Player p, String texto, NamedTextColor cor) {
        p.sendMessage(Component.text(texto, cor));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoChegar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        dentro.remove(p.getUniqueId());
        erros.remove(p.getUniqueId());
        Long ate = bloqueadoAte.get(p.getName().toLowerCase(Locale.ROOT));
        if (ate != null && ate > System.currentTimeMillis()) {
            Bukkit.getScheduler().runTask(plugin, () -> p.kick(Component.text(
                    "Muitas senhas erradas. Tente de novo daqui a 1 minuto.")));
            return;
        }
        boolean temSenha = senhas.containsKey(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> dizer(p, temSenha
                ? "Bem-vindo de volta, camarada. Digite /entrar e a sua senha."
                : "Primeira vez aqui: crie uma senha com /registrar <senha> <senha>. Não use a senha de nenhum "
                        + "outro lugar.", NamedTextColor.GOLD), 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !dentro.contains(p.getUniqueId())) {
                p.kick(Component.text("Você não entrou com a senha em " + SEGUNDOS_PARA_ENTRAR + " segundos."));
            }
        }, 20L * SEGUNDOS_PARA_ENTRAR);
    }

    @EventHandler
    void aoSair(PlayerQuitEvent e) {
        dentro.remove(e.getPlayer().getUniqueId());
        conferindo.remove(e.getPlayer().getUniqueId());
    }

    /** Os comandos de senha. Cancelados sempre: a senha não segue para nenhum outro plugin nem para o log. */
    @EventHandler(priority = EventPriority.LOWEST)
    void aoComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String[] partes = e.getMessage().trim().split("\\s+");
        String cmd = partes[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "/entrar", "/login", "/l" -> {
                e.setCancelled(true);
                entrar(p, partes);
            }
            case "/registrar", "/register", "/reg" -> {
                e.setCancelled(true);
                registrar(p, partes);
            }
            case "/trocarsenha" -> {
                e.setCancelled(true);
                trocar(p, partes);
            }
            default -> {
                if (!dentro.contains(p.getUniqueId())) {
                    e.setCancelled(true);
                    dizer(p, "Entre primeiro com /entrar <senha> (ou /registrar, se é a primeira vez).",
                            NamedTextColor.RED);
                }
            }
        }
    }

    private void entrar(Player p, String[] partes) {
        UUID id = p.getUniqueId();
        if (dentro.contains(id)) {
            dizer(p, "Você já entrou.", NamedTextColor.GRAY);
            return;
        }
        String guardada = senhas.get(id);
        if (guardada == null) {
            dizer(p, "Você ainda não tem senha. Crie com /registrar <senha> <senha>.", NamedTextColor.RED);
            return;
        }
        if (partes.length != 2) {
            dizer(p, "Use: /entrar <senha>", NamedTextColor.RED);
            return;
        }
        if (!conferindo.add(id)) {
            return;
        }
        String senha = partes[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = Senhas.confere(senha, guardada);
            Bukkit.getScheduler().runTask(plugin, () -> {
                conferindo.remove(id);
                if (!p.isOnline()) {
                    return;
                }
                if (ok) {
                    liberar(p, "Entrou. Bom trabalho, camarada!");
                    return;
                }
                int n = erros.merge(id, 1, Integer::sum);
                if (n >= TENTATIVAS) {
                    bloqueadoAte.put(p.getName().toLowerCase(Locale.ROOT),
                            System.currentTimeMillis() + ESPERA_DEPOIS_DE_ERRAR);
                    plugin.getLogger().warning(p.getName() + ": " + TENTATIVAS + " senhas erradas, fora por 1 minuto.");
                    p.kick(Component.text("Muitas senhas erradas. Tente de novo daqui a 1 minuto."));
                } else {
                    dizer(p, "Senha errada (" + n + " de " + TENTATIVAS + "). Esqueceu? Peça ao David no Discord.",
                            NamedTextColor.RED);
                }
            });
        });
    }

    private void registrar(Player p, String[] partes) {
        UUID id = p.getUniqueId();
        if (senhas.containsKey(id)) {
            dizer(p, "Você já tem senha. Use /entrar <senha>.", NamedTextColor.RED);
            return;
        }
        if (partes.length != 3 || !partes[1].equals(partes[2])) {
            dizer(p, "Use: /registrar <senha> <senha> (a mesma duas vezes).", NamedTextColor.RED);
            return;
        }
        var problema = Senhas.problema(partes[1], p.getName());
        if (problema.isPresent()) {
            dizer(p, problema.get(), NamedTextColor.RED);
            return;
        }
        if (!conferindo.add(id)) {
            return;
        }
        String senha = partes[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String h = Senhas.hash(senha);
            Bukkit.getScheduler().runTask(plugin, () -> {
                conferindo.remove(id);
                senhas.put(id, h);
                salvar();
                plugin.getLogger().info(p.getName() + " criou a senha.");
                if (p.isOnline()) {
                    liberar(p, "Senha criada. Nas próximas vezes, entre com /entrar <senha>.");
                }
            });
        });
    }

    private void trocar(Player p, String[] partes) {
        UUID id = p.getUniqueId();
        if (!dentro.contains(id) || partes.length != 3) {
            dizer(p, "Use, depois de entrar: /trocarsenha <senha atual> <senha nova>", NamedTextColor.RED);
            return;
        }
        var problema = Senhas.problema(partes[2], p.getName());
        if (problema.isPresent()) {
            dizer(p, problema.get(), NamedTextColor.RED);
            return;
        }
        String atual = partes[1];
        String nova = partes[2];
        String guardada = senhas.get(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = guardada != null && Senhas.confere(atual, guardada);
            String h = ok ? Senhas.hash(nova) : null;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ok) {
                    dizer(p, "A senha atual não confere.", NamedTextColor.RED);
                    return;
                }
                senhas.put(id, h);
                salvar();
                dizer(p, "Senha trocada.", NamedTextColor.GREEN);
            });
        });
    }

    private void liberar(Player p, String texto) {
        dentro.add(p.getUniqueId());
        erros.remove(p.getUniqueId());
        dizer(p, texto, NamedTextColor.GREEN);
    }

    /** Só pelo console (coordenação): apaga a senha de quem esqueceu, para criar outra no próximo login. */
    boolean removerSenha(String nick) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean tinha = senhas.remove(id) != null;
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            dentro.remove(id);
            p.kick(Component.text("Sua senha foi apagada. Entre de novo e crie outra com /registrar."));
        }
        if (tinha) {
            salvar();
        }
        return tinha;
    }

    // ---- quem não entrou não faz nada ----

    private boolean fora(Entity e) {
        return e instanceof Player p && !dentro.contains(p.getUniqueId());
    }

    private void barrar(Cancellable c, Entity e) {
        if (fora(e)) {
            c.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoAndar(PlayerMoveEvent e) {
        if (fora(e.getPlayer()) && e.hasChangedPosition()) {
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    void aoFalar(AsyncPlayerChatEvent e) {
        if (!dentro.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoMexer(PlayerInteractEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoMexerEmAlguem(PlayerInteractEntityEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoQuebrar(BlockBreakEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoColocar(BlockPlaceEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoLargar(PlayerDropItemEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoPegar(EntityPickupItemEvent e) { barrar(e, e.getEntity()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoTrocarDeMao(PlayerSwapHandItemsEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoAbrir(InventoryOpenEvent e) { barrar(e, e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoClicar(InventoryClickEvent e) { barrar(e, e.getWhoClicked()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoApanhar(EntityDamageEvent e) { barrar(e, e.getEntity()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoBater(EntityDamageByEntityEvent e) { barrar(e, e.getDamager()); }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoSerMirado(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null) {
            barrar(e, e.getTarget());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void aoTerFome(FoodLevelChangeEvent e) { barrar(e, e.getEntity()); }
}

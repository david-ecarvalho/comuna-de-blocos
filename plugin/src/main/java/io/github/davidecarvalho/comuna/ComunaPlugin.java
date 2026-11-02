package io.github.davidecarvalho.comuna;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Comuna de Blocos: bases pessoais, áreas coletivas, defesa contra lavacast e a Comissão do Plano. */
public final class ComunaPlugin extends JavaPlugin {

    /** Cria áreas coletivas e coloca a Comissão do Plano, sem passar por cima das proteções. */
    static final String PERMISSAO_COORDENACAO = "comuna.coordenacao";
    static final String PRACA = "praca";

    private final Territorio territorio = new Territorio();
    private Armazem armazem;
    private PlanoServico plano;
    private int raioDaBase;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        raioDaBase = getConfig().getInt("base.raio", 10);
        LimiteDeRitmo ritmo = new LimiteDeRitmo(getConfig().getInt("lava.baldes-por-minuto", 6), 60_000,
                System::currentTimeMillis);
        armazem = new Armazem(getDataFolder());
        territorio.carregar(armazem.ler());
        getServer().getPluginManager().registerEvents(new ProtecaoListener(territorio, ritmo), this);

        Map<Recurso, Integer> pesos = new EnumMap<>(Recurso.class);
        for (Recurso r : Recurso.values()) {
            pesos.put(r, getConfig().getInt("vale-trabalho.pesos." + r.name().toLowerCase(Locale.ROOT), r.pesoPadrao));
        }
        Estoque estoque = new Estoque(pesos, getConfig().getInt("vale-trabalho.pesos.comida", 1),
                getConfig().getInt("vale-trabalho.fundo-social-percentual", 20));
        plano = new PlanoServico(this, estoque, getConfig().getInt("necessidade.fome-que-pede-comida",
                Necessidades.FOME_QUE_TRAVA_A_CORRIDA), getConfig().getInt("necessidade.horas-de-chegada", 2),
                getConfig().getInt("necessidade.horas-sem-contribuir", 2));
        getServer().getPluginManager().registerEvents(plano, this);
        // depois que o mundo carregou: a praça e a Comissão do Plano no spawn
        getServer().getScheduler().runTask(this, () -> {
            garantirPraca();
            plano.garantirNoSpawn();
        });
        GuardaPopular guarda = new GuardaPopular(this, getConfig().getInt("guarda.golens", 3));
        getServer().getPluginManager().registerEvents(guarda, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            plano.atenderNecessidades();
            guarda.manter();
        }, 100L, 200L);
        new Limpeza(this, getConfig().getInt("guarda-de-dados.coreprotect-dias", 90),
                getConfig().getInt("guarda-de-dados.logs-dias", 30)).agendar();
        getLogger().info(territorio.areas().size() + " áreas carregadas.");
    }

    /** A praça do spawn é área coletiva: ninguém registra base em volta da Comissão do Plano. */
    private void garantirPraca() {
        if (territorio.areas().stream().anyMatch(a -> a.id().equals(PRACA))) {
            return;
        }
        World mundo = getServer().getWorlds().get(0);
        Location s = mundo.getSpawnLocation();
        Territorio.Resultado r = territorio.criarColetiva(PRACA, mundo.getName(), s.getBlockX(), s.getBlockZ(),
                PlanoServico.PERTO_DO_SPAWN, false);
        if (r == Territorio.Resultado.OK) {
            salvarTerritorio();
            getLogger().info("Praça do spawn criada (área coletiva).");
        } else {
            getLogger().warning("Não criei a praça do spawn: " + r + " (uma base encosta no spawn).");
        }
    }

    @Override
    public void onDisable() {
        if (plano != null) {
            plano.salvar();
        }
    }

    @Override
    public boolean onCommand(CommandSender quem, Command comando, String rotulo, String[] args) {
        if (!(quem instanceof Player p)) {
            quem.sendMessage("Só dentro do jogo.");
            return true;
        }
        String acao = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        return switch (comando.getName()) {
            case "base" -> base(p, acao);
            case "coletiva" -> coletiva(p, acao, args);
            case "estoque" -> estoque(p, acao, args);
            case "plano" -> plano(p, acao);
            default -> false;
        };
    }

    private boolean estoque(Player p, String acao, String[] args) {
        switch (acao) {
            case "depositar" -> p.sendMessage(plano.depositar(p, args.length > 1 && args[1].equalsIgnoreCase("tudo")));
            case "ver", "" -> p.sendMessage(plano.resumo(p));
            default -> p.sendMessage(Component.text("/estoque depositar [tudo] | ver", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean plano(Player p, String acao) {
        if (!p.hasPermission(PERMISSAO_COORDENACAO)) {
            p.sendMessage(Component.text("Só a coordenação coloca a Comissão do Plano.", NamedTextColor.RED));
            return true;
        }
        switch (acao) {
            case "npc" -> p.sendMessage(plano.colocarNpc(p.getLocation())
                    ? Component.text("Comissão do Plano colocada aqui.", NamedTextColor.GREEN)
                    : Component.text("A Comissão do Plano fica a até " + PlanoServico.PERTO_DO_SPAWN
                            + " blocos do spawn.", NamedTextColor.RED));
            case "remover" -> {
                plano.removerNpc();
                p.sendMessage(Component.text("Comissão do Plano removida.", NamedTextColor.GREEN));
            }
            default -> p.sendMessage(Component.text("/plano npc | remover", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean base(Player p, String acao) {
        Location l = p.getLocation();
        switch (acao) {
            case "registrar" -> responder(p, territorio.registrarBase(p.getUniqueId(), l.getWorld().getName(),
                    l.getBlockX(), l.getBlockZ(), raioDaBase), "Base registrada em volta de você ("
                    + (2 * raioDaBase + 1) + " x " + (2 * raioDaBase + 1) + " blocos). Ela é sua.");
            case "remover" -> responder(p, territorio.removerBase(p.getUniqueId()), "Base removida.");
            case "info" -> {
                String aqui = territorio.areaEm(l.getWorld().getName(), l.getBlockX(), l.getBlockZ())
                        .map(a -> a.tipo() == Area.Tipo.BASE
                                ? (p.getUniqueId().equals(a.dono()) ? "sua base" : "base de outra pessoa")
                                : "área coletiva " + a.id() + (a.lavaPermitida() ? " (fundição)" : ""))
                        .orElse("terra livre");
                p.sendMessage(Component.text("Aqui: " + aqui + ".", NamedTextColor.GOLD));
            }
            default -> p.sendMessage(Component.text("/base registrar | info | remover", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean coletiva(Player p, String acao, String[] args) {
        if (!p.hasPermission(PERMISSAO_COORDENACAO)) {
            p.sendMessage(Component.text("Só a coordenação cria área coletiva.", NamedTextColor.RED));
            return true;
        }
        Location l = p.getLocation();
        switch (acao) {
            case "criar" -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("/coletiva criar <nome> <raio> [fundicao]", NamedTextColor.GRAY));
                    return true;
                }
                int raio;
                try {
                    raio = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    p.sendMessage(Component.text("O raio é um número.", NamedTextColor.RED));
                    return true;
                }
                boolean fundicao = args.length > 3 && args[3].equalsIgnoreCase("fundicao");
                responder(p, territorio.criarColetiva(args[1], l.getWorld().getName(), l.getBlockX(), l.getBlockZ(),
                        raio, fundicao), "Área coletiva " + args[1] + " criada" + (fundicao ? " (fundição)." : "."));
            }
            case "remover" -> responder(p, args.length > 1 ? territorio.removerColetiva(args[1])
                    : Territorio.Resultado.NAO_EXISTE, "Área coletiva removida.");
            case "lista" -> {
                List<String> nomes = territorio.areas().stream().filter(a -> a.tipo() == Area.Tipo.COLETIVA)
                        .map(Area::id).collect(Collectors.toList());
                p.sendMessage(Component.text("Coletivas: " + (nomes.isEmpty() ? "nenhuma" : String.join(", ", nomes)),
                        NamedTextColor.GOLD));
            }
            default -> p.sendMessage(Component.text("/coletiva criar | remover | lista", NamedTextColor.GRAY));
        }
        return true;
    }

    private void responder(Player p, Territorio.Resultado r, String ok) {
        String texto = switch (r) {
            case OK -> ok;
            case JA_TEM_BASE -> "Você já tem uma base. Use /base remover antes.";
            case SOBREPOE -> "Aqui encosta em outra área. Ande um pouco e tente de novo.";
            case NOME_EM_USO -> "Já existe uma área com esse nome.";
            case NAO_EXISTE -> "Não achei essa área.";
        };
        p.sendMessage(Component.text(texto, r == Territorio.Resultado.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (r == Territorio.Resultado.OK) {
            salvarTerritorio();
        }
    }

    private void salvarTerritorio() {
        try {
            armazem.salvar(territorio.areas());
        } catch (IOException e) {
            getLogger().severe("Não salvei territorio.yml: " + e.getMessage());
        }
    }
}

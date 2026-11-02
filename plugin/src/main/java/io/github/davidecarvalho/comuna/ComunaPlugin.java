package io.github.davidecarvalho.comuna;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Comuna de Blocos: bases pessoais, áreas coletivas e defesa contra lavacast. */
public final class ComunaPlugin extends JavaPlugin {

    private final Territorio territorio = new Territorio();
    private Armazem armazem;
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
        getLogger().info(territorio.areas().size() + " áreas carregadas.");
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
            default -> false;
        };
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
        if (!p.hasPermission(ProtecaoListener.PERMISSAO_ADMIN)) {
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
            try {
                armazem.salvar(territorio.areas());
            } catch (IOException e) {
                getLogger().severe("Não salvei territorio.yml: " + e.getMessage());
            }
        }
    }
}

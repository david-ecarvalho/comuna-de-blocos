package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * A necessidade social, sem nada do servidor: de cada um segundo sua capacidade, a cada um segundo suas necessidades.
 * A Comissão do Plano olha quem está online e entrega direto no inventário:
 * <ul>
 *   <li>comida a quem chegou na fome que trava a corrida (3 coxinhas) e não tem comida consigo, até encher;</li>
 *   <li>a ferramenta da meta do plano que a pessoa não tem (picareta, espada, machado, pá, enxada): primeiro de
 *   pedra; quando todo mundo tem as de pedra, a meta sobe para as de ferro.</li>
 * </ul>
 * O que o estoque não cobre vira o pedido do letreiro: "3 camaradas estão sem picareta de pedra. Deposite...".
 */
public final class Necessidades {

    private Necessidades() { }

    /** Com 6 pontos de fome (3 coxinhas) o jogo não deixa mais correr. */
    public static final int FOME_QUE_TRAVA_A_CORRIDA = 6;
    public static final int FOME_CHEIA = 20;

    /**
     * O que a Comissão sabe de cada pessoa: a fome, se tem comida consigo, o melhor grau de cada ferramenta e se
     * contribui (recém-chegada ou depositou há pouco: Contribuicao).
     */
    public record Pessoa(UUID id, int fome, boolean temComida, Map<Ferramenta, Ferramenta.Grau> ferramentas,
                         boolean contribui) {

        public Pessoa(UUID id, int fome, boolean temComida, Map<Ferramenta, Ferramenta.Grau> ferramentas) {
            this(id, fome, temComida, ferramentas, true);
        }

        boolean temAoMenos(Ferramenta f, Ferramenta.Grau g) {
            Ferramenta.Grau tem = ferramentas.get(f);
            return tem != null && tem.compareTo(g) >= 0;
        }
    }

    public enum Motivo { FOME, FERRAMENTA }

    public record Entrega(UUID quem, Motivo motivo, List<Estoque.Item> itens) { }

    /**
     * O que foi entregue e o que ficou faltando. meta: o grau que o plano está pedindo agora (nulo quando todo mundo
     * já tem tudo); demanda: o que as ferramentas que faltaram custam (a reserva que o kit por vales não pode tocar);
     * faltas: o que falta no estoque para cobri-las; semContribuir: quem precisava mas não contribuiu (não entra na
     * conta e recebe o aviso).
     */
    public record Resultado(List<Entrega> entregas, int comFome, int pontosDeFomeQueFaltam, Ferramenta.Grau meta,
                            Map<Ferramenta, Integer> semFerramenta, Map<Recurso, Integer> demanda,
                            Map<Recurso, Integer> faltas, Set<UUID> semContribuir) {

        public static final Resultado VAZIO = new Resultado(List.of(), 0, 0, null, Map.of(), Map.of(), Map.of(),
                Set.of());
    }

    public static Resultado atender(List<Pessoa> todas, Estoque e, int fomeQuePedeComida) {
        List<Entrega> entregas = new ArrayList<>();

        // quem pode trabalhar e não trabalha não entra (Lênin, O Estado e a Revolução, cap. 5); o recém-chegado entra
        Set<UUID> semContribuir = new HashSet<>();
        for (Pessoa p : todas) {
            boolean precisa = (p.fome() <= fomeQuePedeComida && !p.temComida())
                    || Arrays.stream(Ferramenta.values()).anyMatch(f -> !p.temAoMenos(f, Ferramenta.METAS[0]));
            if (!p.contribui() && precisa) {
                semContribuir.add(p.id());
            }
        }
        List<Pessoa> pessoas = todas.stream().filter(Pessoa::contribui).toList();

        // comida: quem está com mais fome primeiro
        int comFome = 0;
        int pontosQueFaltam = 0;
        List<Pessoa> famintos = pessoas.stream()
                .filter(p -> p.fome() <= fomeQuePedeComida && !p.temComida())
                .sorted(Comparator.comparingInt(Pessoa::fome))
                .toList();
        for (Pessoa p : famintos) {
            int precisa = FOME_CHEIA - p.fome();
            var itens = e.alimentar(precisa);
            if (itens.isPresent()) {
                entregas.add(new Entrega(p.id(), Motivo.FOME, itens.get()));
            } else {
                comFome++;
                pontosQueFaltam += precisa;
            }
        }
        pontosQueFaltam = Math.max(0, pontosQueFaltam - e.pontosDeComida());

        // ferramentas: todo mundo com as de pedra antes de alguém ganhar as de ferro; dentro de cada meta, a picareta
        // de todo mundo antes da espada de alguém
        Map<Ferramenta, Integer> sem = new EnumMap<>(Ferramenta.class);
        Map<Recurso, Integer> demanda = new EnumMap<>(Recurso.class);
        Ferramenta.Grau meta = null;
        Ferramenta.Grau anterior = null;
        for (Ferramenta.Grau g : Ferramenta.METAS) {
            for (Ferramenta f : Ferramenta.values()) {
                for (Pessoa p : pessoas) {
                    if (p.temAoMenos(f, g) || !e.podeReceber(p.id(), f, g)
                            || (anterior != null && !p.temAoMenos(f, anterior))) {
                        continue;
                    }
                    if (e.entregarFerramenta(p.id(), f, g)) {
                        entregas.add(new Entrega(p.id(), Motivo.FERRAMENTA,
                                List.of(new Estoque.Item(f.material(g), 1))));
                    } else {
                        sem.merge(f, 1, Integer::sum);
                        f.custo(g).forEach((r, c) -> demanda.merge(r, c, Integer::sum));
                    }
                }
            }
            if (!sem.isEmpty()) {
                meta = g;
                break;
            }
            anterior = g;
        }
        Map<Recurso, Integer> faltas = new EnumMap<>(Recurso.class);
        demanda.forEach((r, d) -> {
            int falta = d - e.quantidade(r);
            if (falta > 0) {
                faltas.put(r, falta);
            }
        });
        return new Resultado(entregas, comFome, pontosQueFaltam, meta, sem, demanda, faltas, semContribuir);
    }

    /** O que o letreiro da Comissão do Plano fala. */
    public static List<String> frases(Resultado r) {
        List<String> frases = new ArrayList<>();
        if (r.comFome() > 0) {
            int paes = (r.pontosDeFomeQueFaltam() + Comida.PONTOS_DO_PAO - 1) / Comida.PONTOS_DO_PAO;
            frases.add(camaradas(r.comFome()) + " com fome. Deposite comida pronta ou " + paes * Comida.TRIGOS_POR_PAO
                    + " trigos (" + paes + (paes == 1 ? " pão" : " pães") + ") para alimentar.");
        }
        if (r.meta() != null && r.meta() != Ferramenta.METAS[0]) {
            frases.add("Meta do plano: ferramentas " + r.meta().nome + " para todos.");
        }
        r.semFerramenta().forEach((f, n) -> frases.add(camaradas(n) + " sem " + f.nome(r.meta()) + "."));
        if (!r.faltas().isEmpty()) {
            StringJoiner lista = new StringJoiner(", ");
            List<String> partes = new ArrayList<>();
            r.faltas().forEach((rec, q) -> partes.add(rec.fala(q)));
            for (int i = 0; i < partes.size() - 1; i++) {
                lista.add(partes.get(i));
            }
            String texto = partes.size() == 1 ? partes.get(0)
                    : lista + " e " + partes.get(partes.size() - 1);
            frases.add("Deposite " + texto + " para ajudar.");
        }
        if (frases.isEmpty()) {
            frases.add("Ninguém passando necessidade agora. Bom trabalho, camaradas!");
        }
        return frases;
    }

    private static String camaradas(int n) {
        return n == 1 ? "1 camarada está" : n + " camaradas estão";
    }
}

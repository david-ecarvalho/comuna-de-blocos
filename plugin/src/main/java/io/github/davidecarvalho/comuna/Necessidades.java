package io.github.davidecarvalho.comuna;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
 *   <li>a ferramenta de pedra que a pessoa não tem (picareta, machado, pá, enxada).</li>
 * </ul>
 * O que o estoque não cobre vira o pedido do letreiro: "3 camaradas estão sem picareta de pedra. Deposite...".
 */
public final class Necessidades {

    private Necessidades() { }

    /** Com 6 pontos de fome (3 coxinhas) o jogo não deixa mais correr. */
    public static final int FOME_QUE_TRAVA_A_CORRIDA = 6;
    public static final int FOME_CHEIA = 20;

    /** O que a Comissão sabe de cada pessoa: a fome, se tem comida consigo e as ferramentas que faltam. */
    public record Pessoa(UUID id, int fome, boolean temComida, Set<Ferramenta> semFerramenta) { }

    public enum Motivo { FOME, FERRAMENTA }

    public record Entrega(UUID quem, Motivo motivo, List<Estoque.Item> itens) { }

    /**
     * O que foi entregue e o que ficou faltando. demanda: o que as ferramentas que faltaram custam (a reserva que o
     * kit por vales não pode tocar); faltas: o que falta no estoque para cobri-las.
     */
    public record Resultado(List<Entrega> entregas, int comFome, int pontosDeFomeQueFaltam,
                            Map<Ferramenta, Integer> semFerramenta, Map<Recurso, Integer> demanda,
                            Map<Recurso, Integer> faltas) { }

    public static Resultado atender(List<Pessoa> pessoas, Estoque e, int fomeQuePedeComida) {
        List<Entrega> entregas = new ArrayList<>();

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

        // ferramentas: a picareta de todo mundo antes do machado de alguém
        Map<Ferramenta, Integer> sem = new EnumMap<>(Ferramenta.class);
        Map<Recurso, Integer> demanda = new EnumMap<>(Recurso.class);
        for (Ferramenta f : Ferramenta.values()) {
            for (Pessoa p : pessoas) {
                if (!p.semFerramenta().contains(f) || !e.podeReceber(p.id(), f)) {
                    continue;
                }
                if (e.entregarFerramenta(p.id(), f)) {
                    entregas.add(new Entrega(p.id(), Motivo.FERRAMENTA, List.of(new Estoque.Item(f.material(), 1))));
                } else {
                    sem.merge(f, 1, Integer::sum);
                    f.custo.forEach((r, c) -> demanda.merge(r, c, Integer::sum));
                }
            }
        }
        Map<Recurso, Integer> faltas = new EnumMap<>(Recurso.class);
        demanda.forEach((r, d) -> {
            int falta = d - e.quantidade(r);
            if (falta > 0) {
                faltas.put(r, falta);
            }
        });
        return new Resultado(entregas, comFome, pontosQueFaltam, sem, demanda, faltas);
    }

    /** O que o letreiro da Comissão do Plano fala. */
    public static List<String> frases(Resultado r) {
        List<String> frases = new ArrayList<>();
        if (r.comFome() > 0) {
            int paes = (r.pontosDeFomeQueFaltam() + Comida.PONTOS_DO_PAO - 1) / Comida.PONTOS_DO_PAO;
            frases.add(camaradas(r.comFome()) + " com fome. Deposite comida pronta ou " + paes * Comida.TRIGOS_POR_PAO
                    + " trigos (" + paes + (paes == 1 ? " pão" : " pães") + ") para alimentar.");
        }
        r.semFerramenta().forEach((f, n) -> frases.add(camaradas(n) + " sem " + f.nome + "."));
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

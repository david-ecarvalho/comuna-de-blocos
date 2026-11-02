# Estoque coletivo e vale-trabalho (02/11/2026)

## A ideia

Como na *Crítica do Programa de Gotha* (Marx, 1875): quem trabalha recebe um certificado do trabalho que deu, depois de
descontada a parte do fundo social, e retira do estoque comum bens que custam a mesma quantidade de trabalho.
Trabalha mais, pode retirar mais.

## Como funciona no jogo

- `/estoque depositar` põe no estoque coletivo o que está na mão (`/estoque depositar tudo`: todos os itens daquele
  tipo). O estoque é virtual. Aceita pedra (pedregulho), madeira (tronco, tábua, graveto), trigo (trigo, pão) e ferro
  (lingote, ferro bruto, bloco).
- Cada unidade vale vales-trabalho pelo seu peso (pedra, madeira e trigo: 1; ferro: 4). **20%** vão para o fundo
  social.
- `/estoque ver` mostra o estoque, o fundo social, os seus vales e o que falta.
- **Comissão do Plano**: o NPC da área central (`/plano npc`, coordenação). O letreiro em cima dele diz o que falta para
  dar o kit a quem está online sem picareta, por exemplo "Falta madeira para os cabos das ferramentas dos
  trabalhadores: 2 troncos".
- **Kit do trabalhador**: clique no NPC. Ele entrega o melhor kit que o coletivo já liberou, que o estoque cobre e que
  a pessoa pode pagar em vales. Se não der, entrega o nível de baixo.
  - **De pedra**: picareta, machado, pá e enxada de pedra e 8 pães (41 vales).
  - **De ferro**: o mesmo, de ferro (68 vales). Liberado quando a comunidade já produziu 64 ferros no total.
- **O primeiro kit de quem chega** sai do fundo social.

Os pesos, o percentual do fundo e os níveis ficam no `config.yml` (pesos e fundo) e em `NivelDoKit` (níveis), para a
assembleia votar e mudar.

## Permissões

- `comuna.coordenacao`: cria áreas coletivas e coloca a Comissão do Plano.
- `comuna.admin`: passa por cima das proteções (só manutenção).

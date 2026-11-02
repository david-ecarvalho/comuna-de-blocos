# Estoque coletivo, necessidade e vale-trabalho (02/11/2026)

## A ideia

Primeiro a necessidade social: ninguém fica com fome nem sem ferramenta enquanto houver no estoque. Depois o
trabalho, como na *Crítica do Programa de Gotha* (Marx, 1875): quem trabalha recebe um certificado do trabalho que
deu, descontada a parte do fundo social, e com ele retira do estoque bens que custam a mesma quantidade de trabalho.
O fundo social é a parte do trabalho que não vira vale e fica para a necessidade.

## A Comissão do Plano

O NPC fica no spawn, numa praça que é área coletiva (raio de 16 blocos, ninguém registra base ali). O plugin põe os
dois sozinho quando o servidor sobe. A coordenação pode mudar o NPC de lugar com `/plano npc`, sempre a até 16 blocos
do spawn.

A cada 10 segundos a Comissão olha quem está online no modo sobrevivência e entrega direto no inventário:

- **Comida**: para quem chegou a 6 pontos de fome (3 coxinhas, quando o jogo não deixa mais correr) e não tem
  comida consigo. Vai comida até encher a barra, com a menor sobra. A comida pronta sai antes do pão feito com o
  trigo do estoque (3 trigos, 1 pão). Quem está com mais fome come primeiro.
- **Ferramentas da meta do plano**: picareta, espada, machado, pá e enxada. A meta começa nas de pedra (madeira e
  ouro não contam). Quando ninguém online está mais sem as de pedra, a meta sobe para as de ferro, para quem já tem
  as de pedra. Primeiro a picareta de todo mundo, depois a espada e assim por diante. Quem já recebeu só recebe outra
  quando a dela quebrar, quando morrer ou quando a meta subir. Guardar no baú não dá outra.
- Toda ferramenta leva cabo: 2 gravetos (a espada, 1). Sem madeira no estoque, nenhuma sai.

O que o estoque não cobre vira o letreiro em cima do NPC:

> 2 camaradas estão sem picareta de pedra.
> Deposite 6 pedregulhos para ajudar.

Na meta de ferro, a primeira linha é "Meta do plano: ferramentas de ferro para todos."

## Trabalho e vale-trabalho

- `/estoque depositar` põe no estoque o que está na mão (`/estoque depositar tudo`: todos os itens daquele tipo).
  Aceita pedra (pedregulho), madeira (tronco, tábua, graveto), trigo, ferro (lingote, ferro bruto, bloco) e comida
  pronta (pão, carnes e peixes assados, batata assada, cenoura, maçã, tortas, sopas e outras). A comida pronta fica
  guardada como está e vai inteira para quem tiver fome.
- Cada unidade vale vales-trabalho pelo seu peso: pedra, madeira e trigo, 1; ferro, 4; comida pronta, 1 por ponto
  de fome (pão = 5). **20%** vão para o fundo social.
- `/estoque ver` mostra o estoque, a comida em pontos de fome, o fundo social, os seus vales e o que falta.
- **Kit por vales**: clique no NPC. Ele entrega o melhor kit que o coletivo já liberou, que o estoque cobre e que a
  pessoa pode pagar. Se não der, entrega o de baixo.
  - **De pedra**: picareta, espada, machado, pá e enxada de pedra (20 vales). Serve para ter ferramenta a mais.
  - **De ferro**: as mesmas, de ferro (53 vales). Liberado quando a comunidade já produziu 64 ferros no total.
  - O kit nunca tira do estoque o que a necessidade de agora está pedindo. Se faltar algo, o NPC diz o quê (por
    exemplo "falta no estoque: 2 troncos").

## Guarda Popular

Três golens de ferro (`guarda.golens` no `config.yml`) defendem a praça do spawn dos monstros. Quem sai da praça
volta; não atacam trabalhador e trabalhador não fere a guarda; quem morre volta em até 10 segundos e não solta
ferro, para a guarda não virar fazenda de ferro. Só aparecem quando tem alguém perto do spawn.

Os pesos, o percentual do fundo e a fome que pede comida ficam no `config.yml`; os níveis do kit, em `NivelDoKit`.
A assembleia pode votar e mudar.

## Permissões

- `comuna.coordenacao`: cria áreas coletivas e muda a Comissão do Plano de lugar.
- `comuna.admin`: passa por cima das proteções (só manutenção).

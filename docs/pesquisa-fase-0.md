# Pesquisa da fase 0 (02/11/2026)

Resumo técnico de um relatório de pesquisa (Deep Research), conferido em parte no GitHub. O que descreve a máquina e a
rede de quem hospeda fica fora deste repositório público.

## Decisões

1. **Base: Paper 1.21.x.** Purpur e Leaves trazem pouco ganho e mais risco; Folia quebra a maioria dos plugins.
2. **Ninguém conecta direto na máquina que hospeda.** Na fase aberta, um proxy **Velocity** numa VPS no Brasil recebe
   os jogadores, com *modern forwarding* (segredo compartilhado). O Paper fica com `online-mode=false` só atrás do
   proxy, e o firewall aceita apenas o IP da VPS. Na fase de teste fechado, nada é exposto: teste local ou rede
   privada (Tailscale) para poucas pessoas.
3. **Isolamento: Docker** (imagem `itzg/minecraft-server`) sobre WSL2, com limite de memória e de CPU, para o servidor
   não disputar recurso com o resto da máquina.
4. **Configuração segura:** whitelist na fase de teste, `enforce-secure-profile=true`, RCON e query desligados,
   permissões pelo **LuckPerms** (nunca `/op` para uso diário).
5. **Anti-bot e anti-cheat:** **Sonar** no Velocity (GPL-3.0, ativo) e **GrimAC** no Paper (GPL-3.0, ativo), começando
   só com alertas, sem punição automática.
6. **Plugins só do Hangar ou do Modrinth**, nunca de fórum, Discord ou arquivo enviado por alguém (caso Fracturiser,
   2023).
7. **Backup** diário local e cópia externa, com teste de restauração todo mês.
8. **Rastro e desfazer estrago: CoreProtect** (GPL-3.0). É a "prova" do julgamento em assembleia e o que desfaz o
   dano.
9. **Guarda popular: Citizens + Sentinel** (NPCs), acionados pelo nosso plugin.
10. **Plugin próprio em Java 21 + Gradle + Paper API**, testes com **MockBukkit**, CI no GitHub Actions.

## Reaproveitar

| Projeto | Para quê | Licença | Situação (conferida em 02/11/2026) |
|---|---|---|---|
| [CivMC/Civ](https://github.com/CivMC/Civ) | Referência e possível base: proteção de blocos (Citadel), grupos (NameLayer), registro de invasão (JukeAlert), prisão (ExilePearl) | MIT | Ativo, num repositório único (os repositórios antigos de cada plugin foram arquivados). Conferir a versão do jogo. |
| CoreProtect | Registro de tudo e reversão | GPL-3.0 | Ativo |
| [GrimAC](https://github.com/GrimAnticheat/Grim) | Anti-cheat | GPL-3.0 | Ativo |
| [Sonar](https://github.com/jonesdevelopment/sonar) | Anti-bot no proxy | GPL-3.0 | Ativo |
| Velocity | Proxy | GPL-3.0 | Ativo |
| Citizens + Sentinel | NPCs da guarda | a conferir | a conferir |
| Lands | Territórios | Pago, fechado | Alternativa: proteção própria ou base no Civ, para o projeto continuar todo aberto |
| [Mineflayer](https://github.com/PrismarineJS/mineflayer) | Bots para a simulação mercado x plano | MIT | Ativo |

Lição dos servidores "Civ": prisão por tempo indefinido (ExilePearl) virou extorsão entre grupos. Aqui a pena é
decidida em assembleia, com prazo, e o foco é desfazer o dano.

## Regras da Mojang (resumo)

- Sem "Minecraft" no nome do servidor; aviso de que não é oficial nem associado à Mojang ou à Microsoft.
- Proibido vender vantagem de jogo; doação sem contrapartida e cosmético podem.

## A confirmar com o advogado do projeto

- Guarda de registros de acesso pelo Marco Civil (art. 15): o relatório trata como obrigação; a lei fala em provedor
  "com fins econômicos", e o servidor é gratuito.
- Jogadores menores: LGPD art. 14 e ECA Digital (Lei 15.211/2025). Coletar o mínimo (nick e o que o jogo já envia).

## Ordem de trabalho

1. Servidor local de teste (Docker), fechado, só para quem desenvolve.
2. Esqueleto do plugin (Gradle, Paper API, MockBukkit, CI).
3. Primeira mecânica: base pessoal registrada + CoreProtect para desfazer.
4. Proxy na VPS e abertura para teste com o Divã.

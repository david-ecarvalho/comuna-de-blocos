# Defesa contra lavacast (02/11/2026)

**Lavacast** é despejar lava do alto e depois água por cima. Em minutos, isso cobre uma área enorme com uma casca de
pedregulho. É um grief barato e muito usado, porque não quebra nenhum bloco de ninguém: só cria blocos novos por cima.

## Camada 1: registro e desfazer (já ligado no servidor de teste)

O CoreProtect está com `buckets`, `lava-flow`, `water-flow` e `liquid-tracking` ligados. Com o `liquid-tracking`, o
pedregulho que se forma fica no nome de quem pôs a lava ou a água. Então dá para desfazer tudo o que uma pessoa fez:

```
/co rollback u:<nick> t:2h r:#global
```

O registro também é a prova para o julgamento em assembleia.

## Camada 2: impedir no nosso plugin (a escrever)

1. **Balde de lava só onde a pessoa pode.** No `PlayerBucketEmptyEvent`, a lava só é aceita dentro da base da própria
   pessoa ou numa área coletiva liberada para isso, como a fundição. Fora disso, o balde não esvazia e aparece uma
   mensagem explicando o motivo.
2. **Líquido não atravessa fronteira.** No `BlockFromToEvent`, a lava e a água que vêm de fora não entram numa base nem
   numa área coletiva protegida.
3. **Altura.** Fora da própria base, não se despeja lava acima de um limite de altura. É de cima que a lavacast cobre
   uma área grande.
4. **Ritmo.** Um limite de baldes de lava por minuto para cada jogador, para frear quem tenta despejar muito em pouco
   tempo.
5. **Guarda popular.** Lava encostada na base de outra pessoa conta como ataque: a guarda prende, a assembleia julga e o
   CoreProtect desfaz.

## Camada 3: regras do mundo

- Fogo que se espalha desligado fora da base de cada um, porque a lava também incendeia construções.
- Testar, no servidor de teste, uma lavacast de verdade contra cada camada antes de abrir para o público.

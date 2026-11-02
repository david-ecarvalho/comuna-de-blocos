#!/usr/bin/env bash
# Troca o plugin Comuna do servidor de teste pela versão recém-compilada e religa o túnel.
# Uso (de dentro de servidor-teste/):  ./atualizar-plugin.sh
# Para o servidor, porque o jar antigo em dados/plugins/ só sai com ele desligado. O túnel do playit divide a rede
# com o servidor e fica sem internet quando o servidor volta, então é recriado no fim.
set -euo pipefail
cd "$(dirname "$0")"
novo=$(ls -t ../plugin/target/Comuna-*.jar | head -1)
echo "plugin novo: $novo"
if docker exec -u 1000 comuna-teste mc-send-to-console "list" 2>/dev/null; then
  sleep 1
  docker logs --tail 1 comuna-teste
fi
rm -f plugins-extra/Comuna-*.jar
cp "$novo" plugins-extra/
docker compose stop comuna
rm -f dados/plugins/Comuna-*.jar
docker compose up -d
docker compose up -d --force-recreate playit
for _ in $(seq 1 60); do
  docker logs --since 2m comuna-teste 2>&1 | grep -q "Done (" && break
  sleep 5
done
docker logs --since 3m comuna-teste 2>&1 | grep -E "\[Comuna\]|Exception" | tail -5
for _ in $(seq 1 12); do
  docker logs --since 2m comuna-playit 2>&1 | grep -q "tunnels loaded" && { echo "túnel conectado"; exit 0; }
  sleep 5
done
echo "ATENÇÃO: o túnel não conectou; rode: docker compose up -d --force-recreate playit" >&2
exit 1

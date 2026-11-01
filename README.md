SimpleClans (fork ZetraMC)
==========================

Fork do [SimpleClans](https://github.com/RoinujNosde/SimpleClans) usado no servidor ZetraMC. Tudo do plugin
original continua funcionando; este fork adiciona guerra dinâmica, pedidos de entrada, menus para Bedrock e um
ranking de clãs composto.

Documentação do plugin original: <https://simpleclans.gitbook.io/simpleclans/>

## O que este fork adiciona

### Guerra dinâmica

Clãs que ativaram `/clan toggle warstart` entram em guerra quando trocam golpes fora dos mundos bloqueados.

- A guerra só começa depois de `hits-to-start` golpes em `hits-window-seconds` segundos (com 1, começa no primeiro golpe).
- O anúncio global e o webhook do Discord respeitam um cooldown por par de clãs (`announce-cooldown-minutes`).
- A guerra acaba depois de `idle-seconds` sem combate, mostrando uma bossbar para os membros.
- `/guerra` teleporta para perto da guerra do seu clã. Tem cooldown, não funciona logo depois de levar dano e é
  cancelado se você levar dano durante a espera. No Nether nunca teleporta para o teto.
- Guerras dinâmicas terminam quando o servidor desliga; se o servidor cair, elas são encerradas no próximo boot.

Configuração em `war-and-protection.dynamic-war`.

### Pedidos de entrada

Jogadores sem clã pedem para entrar com `/clan apply <tag>` em clãs com `/clan toggle joinrequests` ativo.
Qualquer líder online decide com `/clan joinaccept <jogador>` ou `/clan joindeny <jogador>` (ou clicando na
mensagem). Vários jogadores podem pedir ao mesmo tempo e os pedidos não bloqueiam alianças, rivalidades nem guerras.

### Menus Bedrock (Floodgate)

Jogadores Bedrock recebem formulários próprios em vez dos inventários do Java: Início, Meu clã, Gerenciar,
Banco, Membros, Relações, Convites, Idioma e Ranking. As cores das tags são mantidas e os botões de alternar
mostram o estado atual. Os ícones podem ser trocados em `plugins/SimpleClans/bedrock-icons.yml`
(depois rode `/clan admin reload`).

### Ranking de clãs

Uma pontuação de 0 a 1000, recalculada a cada `clan-ranking.update-minutes` minutos:

| Parte     | Peso | Como é calculada |
|-----------|------|------------------|
| Economia  | 30%  | Banco do clã + dinheiro dos membros, com limite por membro e escala logarítmica |
| Combate   | 30%  | KDR médio dos membros nos últimos 30 dias, com proteção contra farm |
| Guerras   | 25%  | Guerras recentes com luta real, valendo mais contra clãs bem colocados |
| Atividade | 10%  | Parte dos membros que entrou nos últimos 7 dias |
| Idade     | 5%   | Tempo de clã, até 90 dias |

Proteções: kills repetidas na mesma vítima perdem valor, kills entre o mesmo par de clãs no mesmo dia rendem menos,
o KDR é suavizado para quem tem poucas lutas, um único jogador forte não carrega o clã, membros novos só contam
dinheiro depois de alguns dias e guerras sem kills dos dois lados não pontuam. Só entram clãs verificados, com
membros suficientes e alguns dias de existência. Pesos e limites ficam em `clan-ranking` no `config.yml`.

Ver o ranking: `/clan top`, botão no menu (Java e Bedrock) ou placeholders.

## Comandos e permissões novos

| Comando | Permissão | Descrição |
|---------|-----------|-----------|
| `/clan top` | `simpleclans.anyone.top` | Ranking de clãs |
| `/clan joinaccept <jogador>` | líder | Aceita um pedido de entrada |
| `/clan joindeny <jogador>` | líder | Nega um pedido de entrada |
| `/clan toggle joinrequests` | `simpleclans.leader.joinrequests-toggle` | Liga ou desliga pedidos de entrada |
| `/clan toggle warstart` | `simpleclans.leader.dynamicwar-toggle` | Liga ou desliga a guerra dinâmica do clã |
| `/guerra` | `simpleclans.member.dynamicwar` | Vai para a guerra do seu clã |

## Placeholders do ranking

O `%simpleclans_topclans_...%` original continua igual (ordenado por KDR). Os novos usam o ranking composto:

| Placeholder | Valor |
|-------------|-------|
| `%simpleclans_clanrank_<N>_clan_<campo>%` | Qualquer placeholder de clã do clã na posição N, ex.: `%simpleclans_clanrank_1_clan_name%`, `%simpleclans_clanrank_1_clan_color_tag%` |
| `%simpleclans_clanrank_<N>_score%` | Pontos do clã na posição N |
| `%simpleclans_clanrank_<N>_score_economy%` | Também `_combat`, `_wars`, `_activity` e `_age` |
| `%simpleclans_clanrank_position%` | Posição do clã do jogador (vazio se não estiver no ranking) |
| `%simpleclans_clanrank_score%` | Pontos do clã do jogador; também `score_economy`, `score_combat` etc. |

## Compilar

```
./mvnw -B package
```

O jar fica em `target/SimpleClans.jar`. O GitHub Actions compila e roda os testes a cada push e pull request na `main`.

## Créditos

SimpleClans foi criado por phaed e é mantido por RoinujNosde e colaboradores. Licença GPL-3.0, veja [LICENSE](LICENSE).

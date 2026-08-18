| Mode   | Envoie | Reçoit | Usage                              |
| ------ | -----: | -----: | ---------------------------------- |
| `PAIR` |      ✅ |      ✅ | abstractChannel bidirectionnelle 1↔1 |
| `PUB`  |      ✅ |      ❌ | diffuse à N abonnés                |
| `SUB`  |      ❌ |      ✅ | reçoit les publications            |
| `PUSH` |      ✅ |      ❌ | distribue du travail               |
| `PULL` |      ❌ |      ✅ | consomme du travail                |
| `REQ`  |      ✅ |      ✅ | requête → attend réponse           |
| `REP`  |      ✅ |      ✅ | reçoit requête → répond            |

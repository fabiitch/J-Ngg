# NNG Pattern Summary

| Pattern | Sends | Receives | Use |
| --- | ---: | ---: | --- |
| `PAIR` | yes | yes | Direct bidirectional 1-to-1 channel. |
| `PUB` | yes | no | Broadcasts publications to subscribers. |
| `SUB` | no | yes | Receives publications. |
| `PUSH` | yes | no | Distributes work. |
| `PULL` | no | yes | Consumes distributed work. |
| `REQ` | yes | yes | Sends one request and waits for one reply. |
| `REP` | yes | yes | Receives one request and sends one reply. |

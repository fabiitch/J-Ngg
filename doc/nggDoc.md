# J-NNG — documentation technique

## Objectif

J-NNG expose les patterns NNG dans l'API applicative sans exposer leur
implémentation native. L'application choisit explicitement `PAIR`, `PUB/SUB`,
`PUSH/PULL` ou `REQ/REP`; J-NNG gère les sockets, Panama, les AIO, le framing,
le dispatch et le lifecycle.

```text
Application
    │ messages Java / Protobuf
    ▼
PairChannel, PubChannel, SubChannel, PushChannel,
PullChannel, ReqChannel ou RepChannel
    │ registry + dispatcher
    ▼
Socket NNG + AIO (interne)
    │
    ▼
Panama + nng.dll
```

## Création et propriété

`Jnng` fabrique et possède tous ses channels. Sa configuration par défaut
utilise un seul thread daemon pour tous les callbacks Java du processus.

```java
try (Jnng jnng = new Jnng()) {
    PairChannel overlay = jnng.pair(configuration);
}
```

Un executor applicatif peut être partagé explicitement :

```java
try (Jnng jnng = new Jnng(applicationExecutor)) {
    // Jnng ne ferme pas un executor fourni par l'application.
}
```

Fermer `Jnng` ferme tous les channels en ordre inverse de leur création. Fermer
un channel manuellement est également autorisé. Toutes les fermetures sont
idempotentes.

## Configuration obligatoire

Chaque channel reçoit une `ChannelConfiguration` indiquant l'adresse, le rôle
de connexion et les options natives.

```java
ChannelConfiguration server = ChannelConfiguration
        .listen("ipc://agent-overlay")
        .socketConfig(NngSocketConfig.defaults()
                .withSendTimeout(Duration.ofSeconds(5))
                .withRequestTimeout(Duration.ofSeconds(4))
                .withReconnect(Duration.ofMillis(100), Duration.ofSeconds(1)))
        .build();

ChannelConfiguration client = ChannelConfiguration
        .dial("ipc://agent-overlay")
        .build();
```

`LISTEN` possède l'endpoint. `DIAL` initie une connexion non bloquante et NNG
réessaie automatiquement selon la configuration de reconnexion.

## Registry multi-message

Un channel transporte plusieurs types de messages. Chaque exécutable doit
partager les mêmes identifiants réseau et codecs.

```java
public interface ChannelMessageCodec<T> {
    byte[] encode(T message);
    T decode(byte[] payload);
}
```

Enregistrement d'un type uniquement pour l'envoi ou pour une future réponse :

```java
channel.registerMessage(100, Data.class, dataCodec);
```

Enregistrement d'un type reçu avec son unique listener :

```java
Subscription data = channel.registerMessage(
        100,
        Data.class,
        dataCodec,
        this::handleData
);
```

Un channel refuse :

- deux classes associées au même identifiant;
- deux identifiants associés à la même classe;
- un deuxième listener pour un type;
- un enregistrement effectué après `open()`.

Fermer la `Subscription` désactive le listener. Le codec reste disponible
jusqu'à la fermeture du channel, notamment pour permettre l'envoi du même type.
Un message reçu pour un type connu dont le listener est désactivé est ignoré.

Le projet Protobuf applicatif peut fournir directement les codecs :

```java
ChannelMessageCodec<Data> codec = new ChannelMessageCodec<>() {
    public byte[] encode(Data value) {
        return value.toByteArray();
    }

    public Data decode(byte[] payload) {
        return Data.parseFrom(payload);
    }
};
```

## Lifecycle

Les messages et listeners sont enregistrés avant l'ouverture :

```java
PairChannel channel = jnng.pair(configuration);

channel.registerMessage(DATA_ID, Data.class, dataCodec, this::handleData);
channel.onConnectionChanged(this::handleConnection);
channel.onError(this::handleCommunicationError);

channel.open();
channel.send(new Data(...));
```

Règles :

- un channel commence dans l'état interne `NEW`;
- `open()` est autorisé exactement une fois;
- les opérations de communication exigent un channel ouvert;
- un channel fermé ne peut pas être rouvert;
- `close()` est idempotent.

## Connexions et reconnexion

J-NNG adapte `nng_pipe_notify` en événements applicatifs :

```java
channel.onConnectionChanged(event -> {
    switch (event.state()) {
        case CONNECTING -> ...;
        case CONNECTED -> ...;
        case DISCONNECTED -> ...;
        case CLOSED -> ...;
    }
});
```

`activeConnections` indique le nombre de pipes NNG actifs. Il vaut normalement
zéro ou un pour `PAIR`, mais peut être supérieur pour les patterns acceptant
plusieurs pairs.

La notification native ne fait aucun traitement métier sous le verrou NNG :
elle remet seulement l'événement à l'executor partagé.

Une pipe connectée confirme le transport, pas le traitement d'un message par le
processus distant. Pour une confirmation applicative, utiliser `REQ/REP` ou un
message d'acquittement. Un processus bloqué mais encore connecté nécessite un
heartbeat applicatif.

## Patterns exposés

### PAIR

```java
PairChannel pair = jnng.pair(configuration);
pair.registerMessage(DATA_ID, Data.class, dataCodec, this::handleData);
pair.open();

pair.send(data);
pair.trySend(data);
pair.sendAsync(data);
```

`PAIR` est bidirectionnel et prévu pour une relation directe 1↔1.

### PUB/SUB

```java
PubChannel pub = jnng.pub(pubConfiguration);
pub.registerMessage(STATUS_ID, Status.class, statusCodec);
pub.open();
pub.publish(status);

SubChannel sub = jnng.sub(subConfiguration);
sub.registerMessage(STATUS_ID, Status.class, statusCodec, this::handleStatus);
sub.open();
```

`SUB` s'abonne au préfixe vide et transmet tous les types connus au dispatcher.
Les premières publications peuvent être perdues pendant la propagation initiale
de la subscription (« slow joiner »), comportement normal de NNG.

### PUSH/PULL

```java
PushChannel push = jnng.push(pushConfiguration);
push.registerMessage(JOB_ID, Job.class, jobCodec);
push.open();
push.push(job);

PullChannel pull = jnng.pull(pullConfiguration);
pull.registerMessage(JOB_ID, Job.class, jobCodec, this::handleJob);
pull.open();
```

Avec plusieurs `PULL`, `PUSH` distribue le travail; il ne diffuse pas une copie à
chaque consommateur.

### REQ/REP

```java
ReqChannel req = jnng.req(reqConfiguration);
req.registerMessage(COMMAND_ID, Command.class, commandCodec);
req.registerMessage(RESPONSE_ID, Response.class, responseCodec);
req.open();

Response response = req.request(command, Response.class);
CompletableFuture<Response> async =
        req.requestAsync(command, Response.class, Duration.ofSeconds(4));
```

NNG impose une transaction active à la fois sur une socket `REQ`. Une deuxième
requête concurrente échoue avec `TooManyPendingRequestsException`. Un timeout
produit `NggRequestTimeoutException`.

Côté `REP`, chaque type de requête possède un handler spécialisé qui retourne un
message de réponse déjà enregistré :

```java
RepChannel rep = jnng.rep(repConfiguration);
rep.registerMessage(RESPONSE_ID, Response.class, responseCodec);
rep.registerRequest(
        COMMAND_ID,
        Command.class,
        commandCodec,
        command -> handle(command)
);
rep.open();
```

Le channel respecte automatiquement l'alternance NNG `receive → reply`. Une
exception du handler ou une réponse non enregistrée ferme le channel, car une
socket `REP` ne peut pas passer à la requête suivante sans produire sa réponse.

## Framing réseau

J-NNG ajoute une enveloppe stable devant le payload produit par le codec. Tous
les nombres sont big-endian.

| Offset | Taille | Champ |
| ---: | ---: | --- |
| 0 | 4 | version du wire format |
| 4 | 4 | identifiant du type de message |
| 8 | 8 | identifiant du message |
| 16 | 8 | identifiant de corrélation |
| 24 | 4 | longueur du payload |
| 28 | N | payload applicatif |

Les exécutables natifs doivent reproduire ce contrat. Pour `REP`, la corrélation
de la réponse contient l'identifiant de la requête.

## Threads et AIO

- aucune boucle de réception ne bloque un thread Java;
- chaque channel réarme une réception via `nng_recv_aio`;
- les callbacks Panama copient la trame puis déposent le décodage et le listener
  sur l'executor de `Jnng`;
- l'instance par défaut utilise un seul dispatcher Java partagé par tous les
  channels;
- fournir un executor concurrent autorise des handlers simultanés et abandonne
  la garantie implicite d'ordre d'un executor mono-thread.

Un traitement métier long doit être déplacé sur un executor applicatif adapté,
notamment pour ne pas bloquer les autres channels utilisant le dispatcher unique.

## Couche socket interne

Les classes de `com.nz.jnng.socket` ne constituent plus l'API applicative. Elles
gèrent :

- l'ouverture des sockets NNG;
- `listen` et `dial`;
- les options de timeout, reconnexion et taille maximale;
- l'allocation et la propriété des `nng_msg`;
- les opérations AIO et leur annulation;
- les notifications de pipe.

L'application utilise uniquement `Jnng`, la configuration et les channels
concrets.

## Validation

`JnngApplicationTest` simule le code réel des applications et couvre :

- plusieurs types et listeners sur un même `PAIR`;
- les événements de connexion;
- `PUB/SUB`;
- `PUSH/PULL`;
- `REQ/REP` bloquant;
- les timeouts;
- les règles de lifecycle.

```powershell
.\gradlew.bat test
```

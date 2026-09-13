# J-NNG Technical Overview

## Goal

J-NNG exposes NNG patterns without exposing native socket handling.
Applications choose `PAIR`, `PUB/SUB`, `PUSH/PULL` or `REQ/REP`; J-NNG handles
FFM/Panama, AIO, framing, dispatch and lifecycle.

```text
Application
    | Java / Protobuf messages
    v
PairChannel, PubChannel, SubChannel, PushChannel,
PullChannel, ReqChannel or RepChannel
    | registry + dispatcher
    v
NNG socket + AIO
    |
    v
FFM/Panama + nng.dll
```

## Ownership

`Jnng` creates and owns channels. The default instance uses one shared daemon
dispatcher for Java callbacks.

```java
try (Jnng jnng = new Jnng()) {
    PairChannel overlay = jnng.pair(configuration);
}
```

The default native NNG pools are small: 2 task threads, 1 expire thread,
1 poller thread and 1 resolver thread.

```java
NngRuntimeConfig nativeThreads = NngRuntimeConfig.builder()
        .taskThreads(2)
        .expireThreads(1)
        .pollerThreads(1)
        .resolverThreads(1)
        .build();

try (Jnng jnng = new Jnng(nativeThreads)) {
    // channel creation
}
```

NNG pools are process-wide. The first `Jnng` instance fixes them; later
instances must use the same values.

An application-owned executor can be supplied:

```java
try (Jnng jnng = new Jnng(applicationExecutor)) {
    // Jnng does not close application-owned executors.
}
```

Both options can be combined with `new Jnng(nativeThreads, applicationExecutor)`.

Closing `Jnng` closes channels in reverse creation order. Closing a channel
directly is also supported. Closes are idempotent.

## Channel Configuration

Each channel receives an address, a connection role and socket options.

```java
ChannelConfiguration server = ChannelConfiguration
        .listen("ipc://agent-overlay")
        .socketConfig(NngSocketConfig.defaults()
                .withSendTimeout(Duration.ofSeconds(5))
                .withRequestTimeout(Duration.ofSeconds(4))
                .withReconnect(Duration.ofMillis(100), Duration.ofSeconds(1)))
        .queuePolicy(ChannelQueuePolicy.dropNewest(256))
        .build();

ChannelConfiguration client = ChannelConfiguration
        .dial("ipc://agent-overlay")
        .build();
```

`LISTEN` owns the endpoint. `DIAL` starts a non-blocking connection and NNG
reconnects according to the socket configuration.

Receiving channels use a bounded dispatch queue of 256 messages by default.
`dropNewest(n)` keeps accepted messages and drops new native messages when the
queue is full. Use `channel.droppedMessages()` for monitoring.

## Message Registry

A channel can carry multiple message types. Processes must share the same wire
ids and codecs.

```java
public interface ChannelMessageCodec<T> {
    byte[] encode(T message);
    T decode(byte[] payload);
}
```

Register a type for sending or decoding future responses:

```java
channel.registerMessage(100, Data.class, dataCodec);
```

Register a received type with its listener:

```java
Subscription data = channel.registerMessage(
        100,
        Data.class,
        dataCodec,
        this::handleData
);
```

A channel rejects:

- two classes for the same id;
- two ids for the same class;
- a second listener for the same type;
- registration after `open()`.

Closing a `Subscription` disables only the listener. The codec remains
available until the channel closes.

Protobuf codecs can be passed directly:

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

Register messages and listeners before opening:

```java
PairChannel channel = jnng.pair(configuration);

channel.registerMessage(DATA_ID, Data.class, dataCodec, this::handleData);
channel.onConnectionChanged(this::handleConnection);
channel.onError(this::handleCommunicationError);

channel.open();
channel.send(new Data(...));
```

Rules:

- `open()` is allowed once;
- communication requires an open channel;
- a closed channel cannot be reopened;
- `close()` is idempotent.

## Connections

J-NNG turns `nng_pipe_notify` into application events:

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

`activeConnections` is the number of active NNG pipes. Native callbacks only
handoff work to the dispatcher; they do not run business logic under NNG locks.

A connected pipe confirms transport, not remote message handling. Use `REQ/REP`,
an ack message or an application heartbeat when needed.

## Patterns

### PAIR

```java
PairChannel pair = jnng.pair(configuration);
pair.registerMessage(DATA_ID, Data.class, dataCodec, this::handleData);
pair.open();

pair.send(data);
pair.trySend(data);
pair.sendAsync(data);
```

Bidirectional direct 1-to-1 communication.

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

`SUB` subscribes to the empty prefix and dispatches known message types. Early
publications may be lost while the subscription propagates; this is normal NNG
slow-joiner behavior.

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

With multiple `PULL` peers, `PUSH` distributes work. It does not broadcast.

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

NNG allows one active transaction per `REQ` socket. Concurrent requests fail
with `TooManyPendingRequestsException`; timeouts fail with
`NggRequestTimeoutException`.

On `REP`, each request type has a handler returning a registered response type:

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

The channel enforces NNG's `receive -> reply` alternation. Handler failure or an
unregistered response closes the channel.

## Wire Format

J-NNG prefixes codec payloads with a stable big-endian envelope.

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 4 | wire format version |
| 4 | 4 | message type id |
| 8 | 8 | message id |
| 16 | 8 | correlation id |
| 24 | 4 | payload length |
| 28 | N | application payload |

Native peers must reproduce this contract. `REQ` checks that a response
correlation id matches the original request id before decoding.

## Threads and AIO

- Receive loops do not block Java threads.
- Each socket reuses one receive AIO.
- FFM callbacks hand off quickly to Java.
- Only the payload is copied before dispatch.
- Blocking `REQ` calls can run from the single dispatcher without deadlock.
- A custom concurrent executor allows parallel handlers and removes implicit
  single-thread ordering.

Move long business work to an application executor.

## Internal Socket Layer

Classes under `com.fabiitch.jnng.socket` are internal. They handle:

- native socket open/close;
- `listen` and `dial`;
- timeout, reconnect and max-size options;
- `nng_msg` ownership;
- AIO receive/cancel;
- pipe notifications.

Applications should use `Jnng`, `ChannelConfiguration` and concrete channels.

## Tests

`JnngApplicationTest` covers:

- multiple types on one `PAIR`;
- connection events;
- `PUB/SUB`;
- `PUSH/PULL`;
- blocking and async `REQ/REP`;
- blocking `REQ` from the single dispatcher;
- bounded queue drops;
- timeouts;
- lifecycle rules.

```powershell
.\gradlew.bat test
```

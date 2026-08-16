### init 
git clone https://github.com/nanomsg/nng.git
cd nng
git fetch --tags
git reset --hard v1.12.0

## compile
rm -rf build
mkdir build
cmake -B build -DBUILD_SHARED_LIBS=ON
cmake --build build --config Release
find build -iname "*.dll"


### copy dll and ngg.h in jextract bin folder

New-Item -ItemType Directory -Force -Path C:\temp\nng-headers
Copy-Item `
"C:\Users\fabocc\Documents\dossier_perso\clone-project\nng\include\nng\*" `
"C:\Users\fabocc\Documents\dossier_perso\sdk\jextract-25\bin\nng" `
-Recurse -Force


panama gen

./jextract \
--target-package com.nz.jnng \
nng.h

./jextract -I "C:/temp/nng-headers" --target-package com.nz.jnng --output "C:/Users/fabocc/Documents/dossier_perso/workspace/J-ngg/src/generated" "C:/temp/nng-headers/nng.h" "C:/temp/nng-headers/protocol/pair0/pair.h" "C:/temp/nng-headers/protocol/pair1/pair.h" "C:/temp/nng-headers/protocol/pubsub0/pub.h" "C:/temp/nng-headers/protocol/pubsub0/sub.h" "C:/temp/nng-headers/protocol/pipeline0/push.h" "C:/temp/nng-headers/protocol/pipeline0/pull.h" "C:/temp/nng-headers/protocol/reqrep0/req.h" "C:/temp/nng-headers/protocol/reqrep0/rep.h"

## Application API

Application payload serialization is supplied by the application (for example,
by a shared Protobuf project). J-NNG owns only the stable wire envelope.

```java
MessageType<Data> DATA = new MessageType<>(100, Data.class, dataCodec);
MessageRegistry messages = MessageRegistry.builder().register(DATA).build();

NngTopology<Exe> topology = NngTopology.<Exe>builder()
        .link(Exe.AGENT, Exe.OVERLAY, "ipc://agent-overlay")
        .build();

try (NngService<Exe> service = NngService.open(Exe.AGENT, topology, messages)) {
    Subscription subscription = service.on(
            Exe.OVERLAY,
            Data.class,
            this::handleData
    );

    service.sendTo(Exe.OVERLAY, new Data(...));
    service.sendToAsync(Exe.OVERLAY, new Data(...));
}
```

The first endpoint passed to `link` listens; the second endpoint dials and
reconnects asynchronously. Each process uses one Java dispatcher by default,
not one Java thread per socket.

For a zero-copy receive hot path, register a native handler. Its payload segment
is valid only during the callback:

```java
service.onNativeMessage(Exe.OVERLAY, 100, message -> {
    MemorySegment payload = message.payload();
    // Read directly while the callback is active.
});
```

Socket behavior is configured once and propagated through every layer:

```java
NngSocketConfig socketConfig = NngSocketConfig.defaults()
        .withSendTimeout(Duration.ofSeconds(5))
        .withRequestTimeout(Duration.ofSeconds(10))
        .withReconnect(Duration.ofMillis(100), Duration.ofSeconds(1))
        .withMaxReceiveSize(32L * 1024 * 1024);

NngService<Exe> service = NngService.builder(Exe.AGENT, topology, messages)
        .socketConfig(socketConfig)
        .build();
```

The default send and request timeout is 30 seconds. Streaming receives are
infinite by default, reconnect backoff ranges from 100 ms to 1 second, and the
maximum accepted message size is 16 MiB. Blocking receive methods can also take
a per-call `Duration`.

## Low-level Server / Client API

All NNG patterns remain available below `NngService` through `NngServer` and
`NngClient`: `pair`, `pub/sub`, `push/pull`, and `req/rep`. Channels provide
blocking, non-blocking and AIO-backed asynchronous methods according to the
operations supported by their NNG protocol.

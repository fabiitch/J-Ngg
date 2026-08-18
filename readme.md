# J-NNG

[Documentation technique couche par couche](doc/nggDoc.md)

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

The NNG pattern remains explicit while sockets, Panama, AIO and dispatch stay
internal. A channel can carry several application message types, each identified
by a stable wire id and encoded by the application (for example with Protobuf).

```java
try (Jnng jnng = new Jnng()) {
    PairChannel overlay = jnng.pair(
            ChannelConfiguration.dial("ipc://agent-overlay").build()
    );

    overlay.registerMessage(
            100,
            Data.class,
            dataCodec,
            this::handleData
    );
    overlay.registerMessage(101, Status.class, statusCodec, this::handleStatus);
    overlay.onConnectionChanged(event -> log.info("{}", event));
    overlay.onError(this::handleCommunicationError);

    overlay.open();
    overlay.send(new Data(...));
    overlay.sendAsync(new Status(...));
}
```

`Jnng` exposes all patterns directly:

```java
jnng.pair(configuration);
jnng.pub(configuration);
jnng.sub(configuration);
jnng.push(configuration);
jnng.pull(configuration);
jnng.req(configuration);
jnng.rep(configuration);
```

The default instance owns one shared daemon dispatcher, not one Java thread per
channel. Socket behavior is configured through `NngSocketConfig`; receives use
NNG AIO and dialers reconnect automatically.

See [the layer-by-layer technical documentation](doc/nggDoc.md) for lifecycle,
multi-message dispatch, connection events, request timeouts and the wire format.

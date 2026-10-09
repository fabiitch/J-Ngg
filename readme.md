# J-NNG

Java 25 bindings and a small channel API for [NNG](https://nng.nanomsg.org/).

J-NNG keeps the NNG pattern explicit while hiding sockets, FFM/Panama, AIO,
framing, dispatch and native message ownership.

## Status

- Runtime: Windows x64.
- Native library: bundled `nng.dll`.
- Java: JDK 25 with native access enabled.
- Supported patterns: `PAIR`, `PUB/SUB`, `PUSH/PULL`, `REQ/REP`.

## Maven dependency

Publish the library to Maven local from this repository:

```powershell
.\gradlew.bat publishToMavenLocal
```

Consumers use the published artifact instead of importing this Gradle project:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation 'com.fabiitch:J-Nng:1.0-SNAPSHOT'
}
```

Republish after changing the Java sources, generated bindings or bundled DLL.
Publication packages the existing native runtime; it does not rebuild NNG itself.
JVM consumers must enable `--enable-native-access=ALL-UNNAMED`.

## Quick Start

```java
try (Jnng jnng = new Jnng()) {
    PairChannel overlay = jnng.pair(
            ChannelConfiguration.dial("ipc://your_ipc").build()
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

`Jnng` exposes one factory per pattern:

```java
jnng.pair(configuration);
jnng.pub(configuration);
jnng.sub(configuration);
jnng.push(configuration);
jnng.pull(configuration);
jnng.req(configuration);
jnng.rep(configuration);
```

The default instance uses one shared daemon dispatcher, not one Java thread per
channel. Socket behavior is configured with `NngSocketConfig`; receives use NNG
AIO and dialers reconnect automatically.

## Build

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The Gradle build already adds:

```text
--enable-native-access=ALL-UNNAMED
```

Applications using J-NNG on the classpath must add the same JVM option.

## Native Image

Reachability metadata is bundled for the native calls used by J-NNG. See
[GraalVM Native Image](docs/graalvm-native.md) before changing FFM calls,
callbacks, resources or native loading.

```powershell
.\gradlew.bat refreshGraalVmMetadata
```

## Development Docs

- [Technical overview](docs/nggDoc.md)
- [jextract generation](docs/jextract.md)
- [GraalVM Native Image](docs/graalvm-native.md)
- [NNG pattern summary](docs/nng_scalability_protocol.md)

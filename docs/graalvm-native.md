# GraalVM Native Image

J-NNG uses Java FFM downcalls and upcalls. Native Image needs reachability
metadata for these calls and for the bundled `nng.dll` resource.

Bundled metadata lives here:

```text
src/main/resources/META-INF/native-image/com.fabiitch.jnng/J-NNG/reachability-metadata.json
```

## When to Regenerate

Regenerate metadata after changing:

- generated jextract bindings used by production code;
- FFM downcalls or upcalls;
- AIO callbacks or connection callbacks;
- native resource loading.

## Requirements

- Windows x64.
- GraalVM JDK 25 for Windows x64.
- `native-image-agent` available in that GraalVM distribution.

A regular JDK 25 is not enough for metadata generation.

## Generate Metadata

From the repository root:

```powershell
.\gradlew.bat refreshGraalVmMetadata
```

The Gradle task runs the scenario with a Java 25 launcher and uses
`GRAALVM_HOME` to locate `native-image-agent`. It does not require changing
global `JAVA_HOME`.

```powershell
$env:GRAALVM_HOME = "C:\path\to\graalvm-jdk-25"
.\gradlew.bat refreshGraalVmMetadata
```

Generated output:

```text
build/native/agent-output/reachability-metadata.json
```

Bundled output:

```text
src/main/resources/META-INF/native-image/com.fabiitch.jnng/J-NNG/reachability-metadata.json
```

The compatibility script calls the same Gradle task:

```bat
.\generate-graalvm-metadata.bat "C:\path\to\graalvm-jdk-25"
```

It sets `JAVA_HOME`, `GRAALVM_HOME` and `PATH` only for its own process so the
Gradle Wrapper can start with the requested JDK. This is useful when you want
the Java launcher and `native-image-agent` to come from the same GraalVM
installation.

## Scenario Coverage

`com.fabiitch.jnng.graalvm.GraalVmNativeMetadataApp` is a test-only app. It exercises:

- `PAIR`, `PUB`, `SUB`, `PUSH`, `PULL`, `REQ`, `REP`;
- sync, non-blocking and async paths;
- native message allocation, read and free;
- connection upcalls and AIO completion upcalls;
- SUB subscribe/unsubscribe;
- receive timeout and cancellation;
- `nng_strerror`.

Success output:

```text
GraalVM metadata scenario completed successfully.
```

## Validate Before Commit

The tracing agent records executed paths; it does not prove full coverage.

Before replacing bundled metadata:

1. review `foreign.downcalls`, `foreign.upcalls` and `foreign.directUpcalls`;
2. remove entries that only belong to the test harness;
3. keep the resource entry for `dll/windows-x86_64/nng.dll`;
4. test a real native image with `--exact-reachability-metadata`.

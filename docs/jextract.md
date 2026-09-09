# jextract Generation

J-NNG keeps the generated FFM bindings in `src/generated`.

Use this only when upgrading NNG headers or changing the native surface.

## Inputs

- NNG source checkout, for example `nanomsg/nng` tag `v1.12.0`.
- jextract compatible with the Java version used by the project.
- Output directory: `src/generated`.
- Target package: `com.nz.jnng`.

## Build NNG

```sh
git clone https://github.com/nanomsg/nng.git
cd nng
git fetch --tags
git checkout v1.12.0
cmake -S . -B build -DBUILD_SHARED_LIBS=ON
cmake --build build --config Release
```

Copy the produced `nng.dll` into:

```text
src/main/resources/dll/windows-x86_64/nng.dll
```

## Generate Bindings

Set the header directory and output directory, then run:

```sh
JEXTRACT_BIN=/path/to/jextract \
NNG_INCLUDE_DIR=/path/to/nng/include/nng \
OUT_DIR=src/generated \
./script/jextract_generate.sh
```

On Windows with Git Bash:

```sh
JEXTRACT_BIN="/c/tools/jextract-25/bin/jextract.exe" \
NNG_INCLUDE_DIR="/c/dev/nng/include/nng" \
OUT_DIR="src/generated" \
./script/jextract_generate.sh
```

## After Generation

```powershell
.\gradlew.bat test
```

Review generated changes before committing. Do not hand-edit `src/generated`.

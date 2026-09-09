# Instructions for Agents

## Scope

J-NNG is a public Java library. Keep docs and examples portable.

## Rules

- Write documentation in concise English.
- Do not add machine-specific paths.
- Do not hand-edit `src/generated`.
- Keep `--enable-native-access=ALL-UNNAMED` visible for JVM users.
- Update GraalVM metadata docs when FFM calls, callbacks or native resources change.
- Use `.\gradlew.bat refreshGraalVmMetadata` after adding native calls.
- Run the smallest relevant Gradle test after code or metadata changes.

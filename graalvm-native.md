# Préparer les métadonnées GraalVM Native Image

Le projet contient une application de vérification dédiée :
`com.nz.jnng.graalvm.GraalVmNativeMetadataApp`. Elle est placée dans les
sources de test afin de ne pas être publiée avec la bibliothèque.

Cette application ouvre les sept patterns NNG pris en charge (`PAIR`, `PUB`,
`SUB`, `PUSH`, `PULL`, `REQ` et `REP`) et exerce les chemins synchrones,
non bloquants et AIO. Elle déclenche également :

- le chargement de `nng.dll` depuis les ressources ;
- la configuration des sockets et des pools NNG ;
- les notifications de connexion par upcall ;
- l'allocation, la lecture, le transfert et la libération des messages natifs ;
- l'abonnement et le désabonnement SUB ;
- un timeout et l'annulation d'une réception AIO ;
- la traduction d'un code d'erreur avec `nng_strerror`.

## Prérequis

- Windows x64 ;
- GraalVM JDK 25 ;
- `JAVA_HOME` positionné sur cette distribution GraalVM ;
- `native-image` présent dans `%JAVA_HOME%\bin`.

Vérification PowerShell :

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\native-image.cmd" --version
```

## Exécuter le scénario sans agent

Depuis la racine du dépôt :

```powershell
.\gradlew.bat graalvmMetadataApp
```

La commande doit terminer avec :

```text
GraalVM metadata scenario completed successfully.
```

## Collecter les métadonnées avec l'agent

La ligne de commande PowerShell est :

```powershell
.\gradlew.bat --no-daemon clean graalvmMetadataApp -Pagent
```

Avec `-Pagent`, la tâche ajoute elle-même l'option suivante au processus Java
qui exécute l'application :

```text
-agentlib:native-image-agent=config-output-dir=build/native/agent-output
```

Le résultat est écrit dans :

```text
build/native/agent-output/reachability-metadata.json
```

La tâche efface ce répertoire avant chaque collecte. Pour fusionner plus tard
plusieurs scénarios dans des métadonnées existantes, remplacer temporairement
`config-output-dir` par `config-merge-dir` dans la tâche Gradle.

## Exploiter le résultat

Le fichier produit est une observation des chemins réellement exécutés, pas
une preuve automatique de couverture. Avant de publier les métadonnées avec la
bibliothèque :

1. contrôler les sections `foreign.downcalls`, `foreign.upcalls` et
   `foreign.directUpcalls` ;
2. retirer les entrées propres à l'application de test ou au framework de test ;
3. conserver l'entrée de ressource pour
   `dll/windows-x86_64/nng.dll` si elle a été générée ;
4. placer le fichier validé sous
   `src/main/resources/META-INF/native-image/com.nz.jnng/J-NNG/reachability-metadata.json` ;
5. reconstruire puis tester une véritable image native avec
   `--exact-reachability-metadata`.

L'agent ne voit que les appels effectués pendant cette exécution. Le scénario
doit donc échouer dès qu'un échange, un callback, un timeout ou une annulation
n'aboutit pas, afin d'éviter de produire silencieusement un fichier incomplet.

# Générer les métadonnées GraalVM Native Image

Le projet contient une application de vérification dédiée :
`com.nz.jnng.graalvm.GraalVmNativeMetadataApp`. Elle est placée dans les
sources de test afin de ne pas être publiée avec la bibliothèque.

Cette application ouvre les sept patterns NNG pris en charge (`PAIR`, `PUB`,
`SUB`, `PUSH`, `PULL`, `REQ` et `REP`) et exerce les chemins synchrones,
non bloquants et AIO.

Le scénario a été comparé aux appels FFM du code de production. Il couvre tous
les downcalls utilisés par J-Nng ainsi que les deux types d'upcall : événements
de connexion et complétion AIO. Il déclenche notamment :

- le chargement de `nng.dll` depuis les ressources ;
- la configuration des sockets et des pools NNG ;
- les notifications de connexion par upcall ;
- l'allocation, la lecture, le transfert et la libération des messages natifs ;
- l'abonnement et le désabonnement SUB ;
- un timeout et l'annulation d'une réception AIO ;
- la traduction d'un code d'erreur avec `nng_strerror`.

## Prérequis

- Windows x64 ;
- un GraalVM JDK 25 pour Windows x64, avec `native-image-agent`.

Un JDK Oracle, OpenJDK ou Temurin standard ne suffit pas, même s'il s'agit d'un
JDK 25.

## Générer les métadonnées

Depuis `cmd.exe` ou PowerShell, à la racine du dépôt :

```bat
.\generate-graalvm-metadata.bat "C:\outils\graalvm-community-openjdk-25"
```

Le chemin peut contenir des espaces. Le script :

1. vérifie que le JDK contient `java.exe` et `native-image-agent.dll` ;
2. positionne localement `JAVA_HOME`, `GRAALVM_HOME` et `PATH` ;
3. compile les sources de test avec ce JDK via le Gradle Wrapper ;
4. exécute `GraalVmNativeMetadataApp` avec le tracing agent ;
5. vérifie que le fichier de métadonnées a été créé.

Le script ne modifie pas les variables d'environnement de la machine. Il est
donc indépendant du Java configuré globalement.

Il est aussi possible de définir `GRAALVM_HOME` et d'omettre l'argument :

```powershell
$env:GRAALVM_HOME = "C:\outils\graalvm-community-openjdk-25"
.\generate-graalvm-metadata.bat
```

En cas de succès, le scénario affiche :

```text
GraalVM metadata scenario completed successfully.
```

Le JSON généré se trouve dans :

```text
build/native/agent-output/reachability-metadata.json
```

Ce répertoire est recréé à chaque exécution afin de ne jamais mélanger une
ancienne collecte à la nouvelle.

## Exécuter uniquement le scénario

Pour diagnostiquer le scénario sans tracing agent, utiliser le même GraalVM :

```powershell
$env:JAVA_HOME = "C:\outils\graalvm-community-openjdk-25"
.\gradlew.bat --no-daemon graalvmMetadataApp
```

## Exploiter le résultat

Le fichier produit est une observation des chemins réellement exécutés, pas
une preuve automatique de couverture. Avant de publier les métadonnées avec la
bibliothèque :

1. contrôler les sections `foreign.downcalls`, `foreign.upcalls` et, si elle
   existe, `foreign.directUpcalls` ;
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

@echo off
setlocal

set "GRAALVM_JDK=%~1"
if not defined GRAALVM_JDK set "GRAALVM_JDK=%GRAALVM_HOME%"

if not defined GRAALVM_JDK (
    echo Usage: %~nx0 "C:\path\to\graalvm-jdk-25"
    echo Alternatively, define GRAALVM_HOME before running this script.
    exit /b 2
)

if not exist "%GRAALVM_JDK%\bin\java.exe" (
    echo ERROR: Java was not found at "%GRAALVM_JDK%\bin\java.exe".
    exit /b 2
)

if not exist "%GRAALVM_JDK%\bin\native-image-agent.dll" (
    echo ERROR: "%GRAALVM_JDK%" is not a GraalVM JDK with native-image-agent.
    exit /b 2
)

set "JAVA_HOME=%GRAALVM_JDK%"
set "GRAALVM_HOME=%GRAALVM_JDK%"
set "PATH=%GRAALVM_JDK%\bin;%PATH%"

pushd "%~dp0"
call "%~dp0gradlew.bat" --no-daemon refreshGraalVmMetadata
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
    popd
    echo ERROR: GraalVM metadata generation failed.
    exit /b %EXIT_CODE%
)

set "METADATA_FILE=%CD%\src\main\resources\META-INF\native-image\com.nz.jnng\J-NNG\reachability-metadata.json"
if not exist "%METADATA_FILE%" (
    popd
    echo ERROR: The tracing agent did not create reachability-metadata.json.
    exit /b 1
)

echo.
echo GraalVM metadata generated and installed at:
echo %METADATA_FILE%
popd
exit /b 0

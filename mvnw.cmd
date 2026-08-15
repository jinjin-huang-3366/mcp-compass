@ECHO OFF
SETLOCAL EnableDelayedExpansion
SET MAVEN_VERSION=3.9.16
IF "%USERPROFILE%"=="" SET USERPROFILE=%HOMEDRIVE%%HOMEPATH%
SET MAVEN_BASE=%USERPROFILE%\.m2\wrapper\dists\mcp-compass\apache-maven-%MAVEN_VERSION%
SET MAVEN_BIN=%MAVEN_BASE%\bin\mvn.cmd
SET ARCHIVE=%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip
SET URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

IF NOT EXIST "%MAVEN_BIN%" (
  ECHO Downloading Apache Maven %MAVEN_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '!URL!' -OutFile '!ARCHIVE!'; New-Item -ItemType Directory -Force -Path (Split-Path '!MAVEN_BASE!') | Out-Null; if (Test-Path '!MAVEN_BASE!') { Remove-Item -Recurse -Force '!MAVEN_BASE!' }; Expand-Archive -Path '!ARCHIVE!' -DestinationPath (Split-Path '!MAVEN_BASE!') -Force; Remove-Item '!ARCHIVE!'"
  IF ERRORLEVEL 1 EXIT /B 1
)

CALL "%MAVEN_BIN%" %*
ENDLOCAL

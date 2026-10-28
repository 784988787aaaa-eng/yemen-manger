@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.9
set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin-v5\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Please run gradlew from a Unix shell in this project, or install Gradle 8.9.
  exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" -p "%APP_HOME%" %*

@echo off
setlocal
set GRADLE_VERSION=9.5.0
set DIST_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat
set DIST_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  if not exist "%DIST_ZIP%" powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_ZIP%'"
  powershell -NoProfile -Command "Expand-Archive -Force '%DIST_ZIP%' '%DIST_DIR%'"
)
call "%GRADLE_BIN%" %*
endlocal

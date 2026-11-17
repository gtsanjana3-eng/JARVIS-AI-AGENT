@echo off
setlocal EnableExtensions
title J.A.R.V.I.S. - Java Backend

REM ==========================================================================
REM  J.A.R.V.I.S. launcher
REM  - works when the project path contains spaces
REM  - compiles every .java file under src\ into out\
REM  - starts the Java backend and opens http://localhost:8080
REM  - never touches the data\ folder (your database)
REM ==========================================================================

REM Always run from the folder this script lives in (quoted: path may have spaces)
cd /d "%~dp0"

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

set "SRC_DIR=%CD%\src"
set "OUT_DIR=%CD%\out"
set "ARGFILE=%OUT_DIR%\sources.txt"

echo.
echo  ==============================================
echo    J.A.R.V.I.S.  -  build and launch
echo  ==============================================
echo    Project : %CD%
echo.

REM ---------------------------------------------------------- check the JDK
where javac >nul 2>&1
if errorlevel 1 (
  echo  [ERROR] "javac" was not found.
  echo          Install a Java JDK 17 or newer and make sure its "bin" folder
  echo          is on your PATH, then run this file again.
  goto :fail
)
where java >nul 2>&1
if errorlevel 1 (
  echo  [ERROR] "java" was not found on your PATH.
  goto :fail
)

if not exist "%SRC_DIR%\com\jarvis\Main.java" (
  echo  [ERROR] Could not find "%SRC_DIR%\com\jarvis\Main.java".
  echo          Keep run.bat in the project folder next to the "src" folder.
  goto :fail
)

REM SQLite JDBC driver (the only library): every .jar in lib\ is on the classpath
set "LIB_CP=%CD%\lib\*"
if not exist "%CD%\lib\sqlite-jdbc-*.jar" (
  echo  [ERROR] The SQLite driver is missing from the "lib" folder.
  echo          Download sqlite-jdbc-3.47.1.0.jar from
  echo          https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.47.1.0/
  echo          and save it in "%CD%\lib".
  goto :fail
)

REM Use the port from config\jarvis.properties unless one was given on the command line
if "%~1"=="" if exist "%CD%\config\jarvis.properties" (
  for /f "tokens=2 delims== " %%A in ('findstr /b /c:"server.port=" "%CD%\config\jarvis.properties"') do set "PORT=%%A"
)

REM ---------------------------------------------------------- already running in the background?
netstat -ano | findstr /r /c:":%PORT% .*LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo  JARVIS is already running in the background - opening it.
  echo  ^(To restart with new changes: right-click the JARVIS icon next to the clock, Exit, then run this again.^)
  start "" http://localhost:%PORT%
  timeout /t 4 >nul
  goto :eof
)

REM ---------------------------------------------------------- clean build output only
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"
if errorlevel 1 (
  echo  [ERROR] Could not create the build folder "%OUT_DIR%".
  goto :fail
)

REM ---------------------------------------------------------- build the javac argument file
REM Each path is written in double quotes with forward slashes, so spaces are
REM kept inside one argument and backslashes are not treated as escapes.
set /a COUNT=0
type nul > "%ARGFILE%"
setlocal EnableDelayedExpansion
for /r "%SRC_DIR%" %%F in (*.java) do (
  set "P=%%~fF"
  set "P=!P:\=/!"
  >> "%ARGFILE%" echo "!P!"
  set /a COUNT+=1
)
endlocal & set "COUNT=%COUNT%"

if "%COUNT%"=="0" (
  echo  [ERROR] No .java files were found in "%SRC_DIR%".
  goto :fail
)

REM ---------------------------------------------------------- compile
echo  [1/2] Compiling %COUNT% Java source files...
javac -encoding UTF-8 -cp "%LIB_CP%" -d "%OUT_DIR%" "@%ARGFILE%"
if errorlevel 1 (
  echo.
  echo  [ERROR] Compilation failed. Read the messages above: each one shows
  echo          the file name, the line number and what is wrong.
  goto :fail
)
echo        Compilation successful.

REM ---------------------------------------------------------- launch
echo  [2/2] Starting J.A.R.V.I.S. on http://localhost:%PORT%
echo        Keep this window open. Press Ctrl+C here to shut down.
echo.

REM Open the browser a moment after the server starts
start "" /b cmd /c "timeout /t 2 /nobreak >nul & start "" http://localhost:%PORT%"

java --enable-native-access=ALL-UNNAMED -cp "%OUT_DIR%;%LIB_CP%" com.jarvis.Main %PORT% --no-browser
if errorlevel 1 (
  echo.
  echo  [ERROR] The server stopped with an error. If the message above says
  echo          "Address already in use", another JARVIS window is still
  echo          running: close it, or start on another port:  run.bat 9090
  goto :fail
)
goto :eof

:fail
echo.
pause
exit /b 1

@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install a JDK 17 or newer and make sure java is on PATH.
  pause
  exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
  echo The Java compiler javac was not found. Install a JDK 17 or newer, not only a JRE.
  pause
  exit /b 1
)
if not exist build\classes mkdir build\classes
javac --release 17 -encoding UTF-8 -d build\classes src\main\java\com\flightbooking\Main.java src\main\java\com\flightbooking\DataStore.java src\main\java\com\flightbooking\Json.java
if errorlevel 1 (
  echo Compilation failed. Review the Java messages above.
  pause
  exit /b 1
)
echo.
echo Starting FlightDesk. Keep this window open while using the app.
echo URL: http://localhost:8080
echo.
java --add-modules jdk.httpserver -cp build\classes com.flightbooking.Main
pause

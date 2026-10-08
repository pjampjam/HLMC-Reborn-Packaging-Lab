@echo off
rem Double-click to test the next Holy Lois version on this PC: builds the add-ons, opens the local test server in its
rem own window, then starts a test Minecraft with the next mod set. Join "localhost" from Multiplayer (Direct Connection).
rem Close the game normally; type "stop" in the server window to shut the server down. Nothing here touches the real
rem server, your CurseForge profile or the launcher.
setlocal
cd /d "%~dp0"
if exist "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
python fetch-deps.py || goto failed
python fetch-client-files.py || goto failed
call gradlew.bat --console=plain -q :extras:jar :onboarding:jar :auth-ui:jar || goto failed
start "Holy Lois TEST server - type stop here to close it" cmd /k gradlew.bat --console=plain :dev:runServer
echo Waiting for the test server to start...
timeout /t 45 /nobreak >nul
call gradlew.bat --console=plain :dev:runClient
exit /b 0
:failed
echo.
echo Something failed above. Send a screenshot of this window to Claude.
pause

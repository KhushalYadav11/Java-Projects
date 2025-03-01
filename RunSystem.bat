@echo off
echo Secure File Transfer System

:: Create directories if they don't exist
mkdir server_storage 2>nul
mkdir client_storage 2>nul

:: Check if keystores exist, if not, generate them
if not exist server_keystore.jks (
    echo Keystores not found. Generating now...
    call SetupKeystores.bat
)

:: Compile the Java files
echo Compiling Java files...
javac Server.java
javac Client.java

:: Check if compilation was successful
if %ERRORLEVEL% EQU 0 (
    echo Compilation successful.

    :menu
    echo.
    echo Which application would you like to run?
    echo 1. Server
    echo 2. Client
    echo 3. Both (Server in new window)
    echo 4. Exit
    set /p choice=Enter your choice (1-4):

    if "%choice%"=="1" (
        echo Starting server...
        start "Secure File Server" java Server
        goto end
    ) else if "%choice%"=="2" (
        echo Starting client...
        java Client
        goto end
    ) else if "%choice%"=="3" (
        echo Starting server in new window...
        start "Secure File Server" java Server
        echo Starting client...
        java Client
        goto end
    ) else if "%choice%"=="4" (
        echo Exiting.
        goto end
    ) else (
        echo Invalid choice.
        goto menu
    )
) else (
    echo Compilation failed.
    pause
)

:end
pause
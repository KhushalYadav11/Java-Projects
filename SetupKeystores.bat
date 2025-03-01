@echo off
echo Generating keystores for secure file transfer system...

:: Create directories if they don't exist
mkdir server_storage 2>nul
mkdir client_storage 2>nul

:: Generate a self-signed certificate and keystore for the server
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 ^
  -validity 365 -keystore server_keystore.jks -storepass password ^
  -dname "CN=localhost, OU=Development, O=MyCompany, L=City, S=State, C=US" ^
  -noprompt

:: Export the server certificate
keytool -exportcert -alias server -keystore server_keystore.jks ^
  -storepass password -file server.cer

:: Create the client truststore and import the server certificate
keytool -importcert -alias server -file server.cer ^
  -keystore client_truststore.jks -storepass password -noprompt

:: Clean up the exported certificate file
del server.cer

echo Keystores generated successfully.
pause
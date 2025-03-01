# Secure File Transfer System for Windows

This is a Java-based secure file transfer system that implements a client-server architecture with focus on security and encryption.

## Features

- Client-server architecture for file transfer
- SSL/TLS encryption for secure communication
- AES encryption for file contents
- User authentication
- Upload and download functionality
- File listing functionality

## Prerequisites

- Java 8 or higher (with JDK)
- Make sure Java is in your PATH environment variable

## Setup on Windows

1. Make sure all files are in the same directory:
   - Server.java
   - Client.java
   - SetupKeystores.bat
   - RunSystem.bat

2. Double-click on `SetupKeystores.bat` to generate the necessary SSL certificates and keystores.

3. Double-click on `RunSystem.bat` to compile and run the application.

## Using the System

1. When running `RunSystem.bat`, you'll be presented with options:
   - Option 1: Run only the server
   - Option 2: Run only the client
   - Option 3: Run both server and client (server in a new window)
   - Option 4: Exit

2. For a typical use case, select option 3 to run both.

3. When the client starts, you'll be prompted to log in with a username and password.
   - Default users: `Wild` (password: `password123`) or `Yadav` (password: `12345678`)

4. After logging in, you can use the following commands:
   - `UPLOAD <filename>` - Upload a file from client_storage directory
   - `DOWNLOAD <filename>` - Download a file to the client_storage directory
   - `LIST` - List all files available on the server
   - `EXIT` - Disconnect from the server and exit

## File Management

- Files to be uploaded should be placed in the `client_storage` directory
- Downloaded files will be saved to the `client_storage` directory
- On the server side, files are stored in `server_storage\<username>` directories

## Troubleshooting

1. If you get "Command not found" for keytool:
   - Make sure JDK is installed, not just JRE
   - Make sure the JDK bin directory is in your PATH environment variable

2. If you get connection errors:
   - Make sure the server is running before connecting with the client
   - Check that no firewall is blocking port 8443

3. If files don't appear in LIST command:
   - Make sure you've uploaded files while logged in as the current user
   - Check that the server has permission to access the storage directories

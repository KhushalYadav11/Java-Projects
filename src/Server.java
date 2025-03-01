import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 8444;
    private static final String SERVER_STORAGE_DIR = "server_storage";
    private static final Map<String, String> userCredentials = new HashMap<>();
    private static final Map<String, SecretKey> userEncryptionKeys = new HashMap<>();

    static {
        // Initialize some users (in real-world, this would be a database)
        try {
            addUser("Wild", "password123");
            addUser("Yadav", "12345678");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Create server storage directory if it doesn't exist
        createStorageDirectory();

        System.setProperty("javax.net.ssl.keyStore", "server_keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "password");

        try {
            SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(PORT);

            System.out.println("Server started on port " + PORT);

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Create a new thread for each client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createStorageDirectory() {
        File dir = new File(SERVER_STORAGE_DIR);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                System.out.println("Created server storage directory");
            } else {
                System.err.println("Failed to create server storage directory");
            }
        }
    }

    private static void addUser(String username, String password) throws NoSuchAlgorithmException {
        // Hash the password before storing it
        String hashedPassword = hashPassword(password);
        userCredentials.put(username, hashedPassword);

        // Generate an encryption key for this user derived from their password
        SecretKey key = deriveKeyFromPassword(password);
        userEncryptionKeys.put(username, key);

        // Create user directory
        File userDir = new File(SERVER_STORAGE_DIR + File.separator + username);
        if (!userDir.exists()) {
            userDir.mkdir();
        }
    }

    private static String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashedBytes = md.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashedBytes);
    }

    private static SecretKey deriveKeyFromPassword(String password) throws NoSuchAlgorithmException {
        // In a real system, you would use a proper key derivation function like PBKDF2
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest(password.getBytes());
        return new SecretKeySpec(key, "AES");
    }

    private static class ClientHandler extends Thread {
        private final SSLSocket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private DataInputStream dataIn;
        private DataOutputStream dataOut;
        private String authenticatedUser = null;

        public ClientHandler(SSLSocket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                // Set up the input and output streams
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                dataIn = new DataInputStream(clientSocket.getInputStream());
                dataOut = new DataOutputStream(clientSocket.getOutputStream());

                // Handle client authentication
                if (authenticate()) {
                    // Main command loop
                    processCommands();
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (dataIn != null) dataIn.close();
                    if (dataOut != null) dataOut.close();
                    clientSocket.close();
                    System.out.println("Client disconnected");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean authenticate() throws IOException {
            out.println("Please login with username:password");
            String credentials = in.readLine();
            String[] parts = credentials.split(":");

            if (parts.length != 2) {
                out.println("FAILED: Invalid credentials format");
                return false;
            }

            String username = parts[0];
            String password = parts[1];

            try {
                String hashedPassword = hashPassword(password);
                if (userCredentials.containsKey(username) &&
                        userCredentials.get(username).equals(hashedPassword)) {
                    authenticatedUser = username;
                    out.println("SUCCESS: Authenticated as " + username);
                    return true;
                } else {
                    out.println("FAILED: Invalid username or password");
                    return false;
                }
            } catch (NoSuchAlgorithmException e) {
                out.println("FAILED: Server authentication error");
                return false;
            }
        }

        private void processCommands() throws IOException {
            String command;
            while ((command = in.readLine()) != null) {
                String[] parts = command.split(" ", 2);
                String action = parts[0].toUpperCase();

                switch (action) {
                    case "UPLOAD":
                        if (parts.length < 2) {
                            out.println("FAILED: Missing filename");
                            continue;
                        }
                        handleUpload(parts[1]);
                        break;
                    case "DOWNLOAD":
                        if (parts.length < 2) {
                            out.println("FAILED: Missing filename");
                            continue;
                        }
                        handleDownload(parts[1]);
                        break;
                    case "LIST":
                        handleListFiles();
                        break;
                    case "EXIT":
                        out.println("Goodbye!");
                        return;
                    default:
                        out.println("FAILED: Unknown command");
                }
            }
        }

        private void handleUpload(String filename) throws IOException {
            try {
                // Read file size
                long fileSize = dataIn.readLong();

                // Prepare file path
                Path filePath = Paths.get(SERVER_STORAGE_DIR, authenticatedUser, filename);

                out.println("READY");

                // Read the encrypted file
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;

                try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
                    while (totalBytesRead < fileSize &&
                            (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                }

                out.println("SUCCESS: File uploaded successfully");
                System.out.println("File uploaded: " + filename + " by " + authenticatedUser);

            } catch (IOException e) {
                out.println("FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleDownload(String filename) throws IOException {
            Path filePath = Paths.get(SERVER_STORAGE_DIR, authenticatedUser, filename);
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                out.println("FAILED: File not found");
                return;
            }

            try {
                // Send file size
                long fileSize = file.length();
                out.println("SIZE " + fileSize);

                // Wait for client to be ready
                String response = in.readLine();
                if (!"READY".equals(response)) {
                    return;
                }

                // Send the encrypted file
                byte[] buffer = new byte[4096];
                int bytesRead;

                try (FileInputStream fileIn = new FileInputStream(file)) {
                    while ((bytesRead = fileIn.read(buffer)) != -1) {
                        dataOut.write(buffer, 0, bytesRead);
                    }
                    dataOut.flush();
                }

                System.out.println("File downloaded: " + filename + " by " + authenticatedUser);

            } catch (IOException e) {
                out.println("FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleListFiles() {
            File userDir = new File(SERVER_STORAGE_DIR + File.separator + authenticatedUser);
            String[] files = userDir.list();

            if (files == null || files.length == 0) {
                out.println("No files found");
            } else {
                out.println("Files: " + files.length);
                for (String file : files) {
                    out.println(file);
                }
                out.println("END");
            }
        }
    }
}
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8444;
    private static final String CLIENT_STORAGE_DIR = "client_storage";
    private static SecretKey encryptionKey;

    private SSLSocket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private Scanner scanner;
    private String username;

    public static void main(String[] args) {
        // Create client storage directory if it doesn't exist
        createStorageDirectory();

        // Set up SSL properties
        System.setProperty("javax.net.ssl.trustStore", "client_truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");

        Client client = new Client();
        client.start();
    }

    private static void createStorageDirectory() {
        File dir = new File(CLIENT_STORAGE_DIR);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                System.out.println("Created client storage directory");
            } else {
                System.err.println("Failed to create client storage directory");
            }
        }
    }

    public void start() {
        scanner = new Scanner(System.in);

        try {
            connectToServer();
            authenticate();
            commandLoop();

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void connectToServer() throws IOException {
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = (SSLSocket) ssf.createSocket(SERVER_HOST, SERVER_PORT);

        // Set up the input and output streams
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        dataIn = new DataInputStream(socket.getInputStream());
        dataOut = new DataOutputStream(socket.getOutputStream());

        System.out.println("Connected to server");
    }

    private void authenticate() throws IOException {
        String prompt = in.readLine();
        System.out.println(prompt);

        System.out.print("Username: ");
        username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        // Derive encryption key from password
        try {
            encryptionKey = deriveKeyFromPassword(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error generating encryption key: " + e.getMessage());
            e.printStackTrace();
        }

        out.println(username + ":" + password);
        String response = in.readLine();
        System.out.println(response);

        if (!response.startsWith("SUCCESS")) {
            throw new IOException("Authentication failed");
        }
    }

    private static SecretKey deriveKeyFromPassword(String password) throws NoSuchAlgorithmException {
        // In a real system, you would use a proper key derivation function like PBKDF2
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest(password.getBytes());
        return new SecretKeySpec(key, "AES");
    }

    private void commandLoop() throws IOException {
        while (true) {
            System.out.println("\nAvailable commands:");
            System.out.println("1. UPLOAD <filename> - Upload a file to the server");
            System.out.println("2. DOWNLOAD <filename> - Download a file from the server");
            System.out.println("3. LIST - List all files on the server");
            System.out.println("4. EXIT - Close the connection and exit");
            System.out.print("\nEnter command: ");

            String command = scanner.nextLine();

            if (command.equalsIgnoreCase("EXIT")) {
                out.println("EXIT");
                String response = in.readLine();
                System.out.println(response);
                break;
            } else if (command.toUpperCase().startsWith("UPLOAD ")) {
                handleUpload(command.substring(7));
            } else if (command.toUpperCase().startsWith("DOWNLOAD ")) {
                handleDownload(command.substring(9));
            } else if (command.equalsIgnoreCase("LIST")) {
                out.println("LIST");
                handleListResponse();
            } else {
                System.out.println("Unknown command. Please try again.");
            }
        }
    }

    private void handleUpload(String filename) throws IOException {
        File file = new File(CLIENT_STORAGE_DIR + File.separator + filename);

        if (!file.exists() || !file.isFile()) {
            System.out.println("File not found: " + filename);
            return;
        }

        try {
            // Encrypt the file before sending
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] encryptedBytes = encrypt(fileBytes);

            // Send upload command
            out.println("UPLOAD " + filename);

            // Send file size
            dataOut.writeLong(encryptedBytes.length);

            // Wait for server to be ready
            String response = in.readLine();
            if (!"READY".equals(response)) {
                System.out.println("Server not ready: " + response);
                return;
            }

            // Send the encrypted file
            dataOut.write(encryptedBytes);
            dataOut.flush();

            // Get the server's response
            response = in.readLine();
            System.out.println(response);

        } catch (Exception e) {
            System.err.println("Error uploading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDownload(String filename) throws IOException {
        out.println("DOWNLOAD " + filename);

        String response = in.readLine();
        if (!response.startsWith("SIZE")) {
            System.out.println(response);
            return;
        }

        long fileSize = Long.parseLong(response.substring(5));

        File outputFile = new File(CLIENT_STORAGE_DIR + File.separator + filename);

        try {
            // Tell server we're ready to receive
            out.println("READY");

            // Read the encrypted file
            byte[] encryptedBytes = new byte[(int) fileSize];
            int bytesRead = 0;
            int offset = 0;

            while (offset < fileSize) {
                bytesRead = dataIn.read(encryptedBytes, offset, (int) (fileSize - offset));
                if (bytesRead == -1) break;
                offset += bytesRead;
            }

            // Decrypt the file
            byte[] decryptedBytes = decrypt(encryptedBytes);

            // Save the decrypted file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(decryptedBytes);
            }

            System.out.println("File downloaded successfully: " + filename);

        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleListResponse() throws IOException {
        String response = in.readLine();

        if (response.startsWith("No files")) {
            System.out.println(response);
            return;
        }

        int numFiles = Integer.parseInt(response.substring(7));
        System.out.println("Files available on server:");

        for (int i = 0; i < numFiles; i++) {
            response = in.readLine();
            System.out.println("- " + response);
        }

        // Read the "END" marker
        in.readLine();
    }

    private static byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
        return cipher.doFinal(data);
    }

    private static byte[] decrypt(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
        return cipher.doFinal(encryptedData);
    }

    private void close() throws IOException {
        if (scanner != null) {
            scanner.close();
        }
        if (in != null) in.close();
        if (out != null) out.close();
        if (dataIn != null) dataIn.close();
        if (dataOut != null) dataOut.close();
        if (socket != null) socket.close();
    }
}
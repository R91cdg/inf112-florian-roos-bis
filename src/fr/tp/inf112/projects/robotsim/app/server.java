package fr.tp.inf112.projects.robotsim.app;

import java.net.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

public class server {

    private static final Logger LOGGER = Logger.getLogger(server.class.getName());
    ServerSocket serverSocket;
    
    public server() throws IOException {
        serverSocket = new ServerSocket(51100);
    }

    public void startServer() {
        LOGGER.info("Server started on port 51100. Waiting for clients...");
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Exception in server loop", e);
            }
        }
    }

    public static void main(String[] args) {
        try {
            server s = new server();
            s.startServer();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not start server", e);
        }
    }
}

class ClientHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
        ) {
            Object receivedObject = in.readObject();
            
            FactoryPersistenceManager persistenceManager = new FactoryPersistenceManager(null);

            if (receivedObject instanceof String) {
                String command = (String) receivedObject;
                if ("LIST_FILES".equals(command)) {
                    LOGGER.info("Received list files request.");
                    File dir = new File("."); // Current working directory
                    String[] files = dir.list((d, name) -> name.endsWith(".factory"));
                    out.writeObject(files);
                    out.flush();
                    LOGGER.info("File list sent to client.");
                } else {
                    String id = command;
                    LOGGER.info("Received read request for ID: " + id);
                    
                    Canvas canvas = persistenceManager.read(id);
                    out.writeObject(canvas);
                    out.flush();
                    LOGGER.info("Model sent back to client.");
                }
                
            } else if (receivedObject instanceof Factory) {
                Factory factory = (Factory) receivedObject;
                LOGGER.info("Received persist request for Factory: " + factory.getId());
                
                persistenceManager.persist(factory);
                LOGGER.info("Factory persisted to server file system.");
            } else if (receivedObject instanceof Object[]) {
                // Format: [simulationId (String), factory (Factory)]
                // Utilisé pour sauvegarder avec un ID différent de celui de la factory
                Object[] data = (Object[]) receivedObject;
                if (data.length == 2 && data[0] instanceof String && data[1] instanceof Factory) {
                    String simulationId = (String) data[0];
                    Factory factory = (Factory) data[1];
                    String originalId = factory.getId();
                    
                    LOGGER.info("Received persist request with override ID: " + simulationId + " (original: " + originalId + ")");
                    
                    // Temporairement définir l'ID pour la sauvegarde
                    factory.setId(simulationId);
                    persistenceManager.persist(factory);
                    // Restaurer l'ID original
                    factory.setId(originalId);
                    
                    LOGGER.info("Factory persisted with ID: " + simulationId);
                } else {
                    LOGGER.warning("Received invalid Object[] format");
                }
            } else {
                LOGGER.warning("Received unknown object type: " + receivedObject.getClass().getName());
            }

        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Exception in client handler", e);
        } finally {
            try {
                clientSocket.close();
                LOGGER.info("Client disconnected: " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close client socket", e);
            }
        }
    }
}
package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import fr.tp.inf112.projects.canvas.controller.CanvasViewerController;
import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.canvas.model.impl.BasicVertex;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;

public class RemoteSimulatorController implements CanvasViewerController {

    private static final Logger LOGGER = Logger.getLogger(RemoteSimulatorController.class.getName());

    private Factory factoryModel;
    private final CanvasPersistenceManager persistenceManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile boolean running = false;
    
    private final List<Observer> observers = new CopyOnWriteArrayList<>();

    public RemoteSimulatorController(CanvasPersistenceManager persistenceManager) {
        this(null, persistenceManager);
    }

    public RemoteSimulatorController(Factory factoryModel, CanvasPersistenceManager persistenceManager) {
        this.factoryModel = factoryModel;
        this.persistenceManager = persistenceManager;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.addMixIn(BasicVertex.class, BasicVertexMixin.class);

        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(PositionedShape.class.getPackageName())
                .allowIfSubType(Component.class.getPackageName())
                .allowIfSubType("fr.tp.inf112.projects.canvas.model") 
                .allowIfSubType(ArrayList.class.getName())
                .allowIfSubType(LinkedHashSet.class.getName())
                .build();
        objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @Override
    public boolean addObserver(Observer observer) {
        LOGGER.info("Adding observer: " + observer);
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
        if (factoryModel != null) {
            return factoryModel.addObserver(observer);
        }
        return true;
    }

    @Override
    public boolean removeObserver(Observer observer) {
        LOGGER.info("Removing observer: " + observer);
        observers.remove(observer);
        if (factoryModel != null) {
            return factoryModel.removeObserver(observer);
        }
        return false;
    }

    @Override
    public void setCanvas(Canvas canvasModel) {
        // Cette méthode est appelée lors du chargement d'un fichier.
        // On doit remplacer complètement le modèle pour avoir les bonnes dimensions, etc.
        Factory newFactory = (Factory) canvasModel;
        
        if (this.factoryModel != null) {
            // On détache les observateurs de l'ancien modèle
            for (Observer observer : observers) {
                this.factoryModel.removeObserver(observer);
            }
        }

        this.factoryModel = newFactory;

        // On rattache les observateurs au nouveau modèle
        if (this.factoryModel != null) {
            for (Observer observer : observers) {
                this.factoryModel.addObserver(observer);
            }
        }
        
        refreshView();
    }
    
    /**
     * Méthode spécifique pour la mise à jour via le réseau.
     * Elle ne remplace pas l'objet mais met à jour ses composants pour éviter le scintillement.
     */
    private void updateModel(Factory remoteFactory) {
        if (this.factoryModel == null) return;

        // Protection : si le serveur renvoie une liste vide (bug de sérialisation), on ignore
        if (remoteFactory.getComponents() == null || remoteFactory.getComponents().isEmpty()) {
            // LOGGER.warning("Received empty component list from server. Ignoring.");
            return;
        }

        if (remoteFactory.getId() != null) {
            this.factoryModel.setId(remoteFactory.getId());
        }
        
        // Mise à jour des composants in-place
        if (this.factoryModel.getComponents() != null) {
            this.factoryModel.getComponents().clear();
            this.factoryModel.getComponents().addAll(remoteFactory.getComponents());
        }
        
        refreshView();
    }

    private void refreshView() {
        for (Observer observer : observers) {
            if (observer instanceof java.awt.Component) {
                ((java.awt.Component) observer).repaint();
            }
        }
    }

    @Override
    public Canvas getCanvas() {
        return factoryModel;
    }

    @Override
    public void startAnimation() {
        LOGGER.info("startAnimation called");
        if (factoryModel == null) {
            LOGGER.severe("startAnimation: Factory model is NULL");
            return;
        }

        if (factoryModel.getId() == null) {
            LOGGER.warning("startAnimation: Factory ID is NULL. Defaulting to 'autosave.factory'");
            factoryModel.setId("autosave.factory");
        }

        uploadFactoryToPersistenceServer(factoryModel);

        try {
            URI uri = new URI("http", null, "localhost", 8081, "/simulation/start/" + factoryModel.getId(), null, null);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            this.running = true;
            new Thread(this::updateViewer).start();
            LOGGER.info("Animation thread started for Factory ID: " + factoryModel.getId());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uploadFactoryToPersistenceServer(Factory factory) {
        int componentCount = (factory.getComponents() != null) ? factory.getComponents().size() : 0;
        LOGGER.info("Uploading factory '" + factory.getId() + "' with " + componentCount + " components to Persistence Server...");
        
        try (Socket socket = new Socket("localhost", 51100);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject(factory);
            out.flush();
            LOGGER.info("Factory upload completed.");
            
        } catch (IOException e) {
            LOGGER.severe("Failed to upload factory to Persistence Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stopAnimation() {
        LOGGER.info("stopAnimation called");
        this.running = false;
        if (factoryModel == null || factoryModel.getId() == null) return;
        try {
             URI uri = new URI("http", null, "localhost", 8081, "/simulation/stop/" + factoryModel.getId(), null, null);
             HttpRequest request = HttpRequest.newBuilder().uri(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
             httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isAnimationRunning() {
        return running;
    }

    @Override
    public CanvasPersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    private void updateViewer() {
        LOGGER.info("Entering updateViewer loop");
        while (this.running) {
            try {
                long start = System.currentTimeMillis();
                final URI uri = new URI("http", null, "localhost", 8081, "/simulation/" + factoryModel.getId(), null, null);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                    try {
                        Factory remoteFactory = objectMapper.readValue(response.body(), Factory.class);
                        SwingUtilities.invokeLater(() -> {
                            // ICI : on utilise updateModel au lieu de setCanvas
                            updateModel(remoteFactory);
                        });
                    } catch (Exception e) {
                        LOGGER.severe("JSON Deserialization failed: " + e.getMessage());
                    }
                }
                
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 100) {
                    Thread.sleep(100 - elapsed);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("Exiting updateViewer loop");
    }
    
    abstract static class BasicVertexMixin {
        @JsonCreator
        public BasicVertexMixin(@JsonProperty("xCoordinate") int xCoordinate, @JsonProperty("yCoordinate") int yCoordinate) {}
    }
}
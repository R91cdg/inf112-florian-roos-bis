package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import fr.tp.inf112.projects.canvas.controller.CanvasViewerController;
import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;

public class RemoteSimulatorController implements CanvasViewerController {

    private Factory factoryModel;
    private final CanvasPersistenceManager persistenceManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RemoteSimulatorController(CanvasPersistenceManager persistenceManager) {
        this(null, persistenceManager);
    }

    public RemoteSimulatorController(Factory factoryModel, CanvasPersistenceManager persistenceManager) {
        this.factoryModel = factoryModel;
        this.persistenceManager = persistenceManager;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

        // Configuration du polymorphisme pour Jackson
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
        if (factoryModel != null) {
            return factoryModel.addObserver(observer);
        }
        return false;
    }

    @Override
    public boolean removeObserver(Observer observer) {
        if (factoryModel != null) {
            return factoryModel.removeObserver(observer);
        }
        return false;
    }

    @Override
    public void setCanvas(Canvas canvasModel) {
        // Logique pour conserver les observateurs (l'affichage) lors du changement de modèle
        if (this.factoryModel != null && canvasModel instanceof Factory) {
            List<Observer> observers = this.factoryModel.getObservers();
            this.factoryModel = (Factory) canvasModel;
            for (Observer observer : observers) {
                this.factoryModel.addObserver(observer);
            }
            // On notifie pour repeindre l'écran
            this.factoryModel.notifyObservers();
        } else {
            this.factoryModel = (Factory) canvasModel;
        }
        
        // FIX IMPORTANT : On s'assure que l'ID est propagé si on vient de le lire
        if (this.factoryModel != null && this.factoryModel.getId() == null && canvasModel.getId() != null) {
            this.factoryModel.setId(canvasModel.getId());
        }
    }

    @Override
    public Canvas getCanvas() {
        if (factoryModel == null || factoryModel.getId() == null) {
            return factoryModel;
        }
        try {
            final URI uri = new URI("http", null, "localhost", 8081, "/simulation/" + factoryModel.getId(), null, null);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // FIX : Vérification stricte avant de parser le JSON
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                this.factoryModel = objectMapper.readValue(response.body(), Factory.class);
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            // On loggue juste l'erreur sans planter, car c'est appelé en boucle
            System.err.println("Erreur de récupération : " + e.getMessage());
        }
        return factoryModel;
    }

    @Override
    public void startAnimation() {
        if (factoryModel == null) return;

        // FIX : Bloc Try-Catch OBLIGATOIRE pour compiler
        try {
            // 1. Sauvegarde automatique si pas d'ID
            if (factoryModel.getId() == null) {
                factoryModel.setId("autosave.factory");
                persistenceManager.persist(factoryModel);
            }

            // 2. Appel REST pour démarrer
            final URI uri = new URI("http", null, "localhost", 8081, "/simulation/start/" + factoryModel.getId(), null, null);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 3. Lancement du thread de mise à jour
            new Thread(this::updateViewer).start();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Impossible de démarrer la simulation : " + e.getMessage());
        }
    }

    @Override
    public void stopAnimation() {
        if (factoryModel == null || factoryModel.getId() == null) return;
        try {
            final URI uri = new URI("http", null, "localhost", 8081, "/simulation/stop/" + factoryModel.getId(), null, null);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isAnimationRunning() {
        if (factoryModel != null) {
            return factoryModel.isSimulationStarted();
        }
        return false;
    }

    @Override
    public CanvasPersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    private void updateViewer() {
        // Boucle tant que la simulation tourne
        while (isAnimationRunning()) {
            try {
                final URI uri = new URI("http", null, "localhost", 8081, "/simulation/" + factoryModel.getId(), null, null);
                HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                    Factory remoteFactory = objectMapper.readValue(response.body(), Factory.class);
                    // Mise à jour du modèle local
                    setCanvas(remoteFactory);
                }
                // Pause pour ne pas saturer le réseau
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
                break; // On arrête la boucle en cas d'erreur grave
            }
        }
    }
}
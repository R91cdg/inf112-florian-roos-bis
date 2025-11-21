package fr.tp.slr201.projects.robotsim.service.service.implem;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.slr201.projects.robotsim.service.service.SimulationService;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationServiceImpl.class);
    
    private final Map<String, Factory> simulatedFactories = new ConcurrentHashMap<>();

    private static final String PERSISTENCE_HOST = "127.0.0.1";
    private static final int PERSISTENCE_PORT = 51100;
    
    public SimulationServiceImpl() {
        logger.info("SimulationServiceImpl instantiated. Hash: {}", System.identityHashCode(this));
    }

    @Override
    public boolean startSimulation(String factoryId) {
        logger.info("Attempting to start simulation for factory ID: {} on Service instance: {}", factoryId, System.identityHashCode(this));

        if (simulatedFactories.containsKey(factoryId)) {
            logger.warn("Factory {} is already running.", factoryId);
            return false;
        }

        Factory factory = fetchFactoryFromPersistence(factoryId);

        if (factory != null) {
            int compCount = (factory.getComponents() != null) ? factory.getComponents().size() : 0;
            logger.info("Fetched factory {} from persistence. Components count: {}", factoryId, compCount);
            
            simulatedFactories.put(factoryId, factory);
            logger.info("Calling factory.startSimulation() for ID: {}", factoryId);
            factory.startSimulation();
            logger.info("Simulation started successfully for factory ID: {}", factoryId);
            return true;
        } else {
            logger.error("Could not fetch factory {} from persistence server.", factoryId);
            return false;
        }
    }

    private Factory fetchFactoryFromPersistence(String factoryId) {
        logger.info("Connecting to Persistence Server at {}:{}...", PERSISTENCE_HOST, PERSISTENCE_PORT);
        try (Socket socket = new Socket(PERSISTENCE_HOST, PERSISTENCE_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(factoryId);
            out.flush();
            Object response = in.readObject();

            if (response instanceof Factory) {
                return (Factory) response;
            } else {
                logger.error("Received object is not a Factory: {}", response != null ? response.getClass().getName() : "null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error communicating with persistence server", e);
            return null;
        }
    }

    @Override
    public Factory getSimulatedFactory(String factoryId) {
        Factory f = simulatedFactories.get(factoryId);
        if (f != null) {
            int count = (f.getComponents() != null) ? f.getComponents().size() : 0;
            // J'ai retiré le code de debug qui causait l'erreur de compilation (cast invalide)
            logger.info("getSimulatedFactory SERVER SIDE: ID={}, Components={}", factoryId, count);
        } else {
            logger.warn("getSimulatedFactory: Factory {} not found in memory.", factoryId);
        }
        return f;
    }

    @Override
    public boolean stopSimulation(String factoryId) {
        if (simulatedFactories.containsKey(factoryId)) {
            Factory factory = simulatedFactories.get(factoryId);
            factory.stopSimulation();
            simulatedFactories.remove(factoryId);
            logger.info("Simulation stopped successfully for factory ID: {}", factoryId);
            return true;
        }
        return false;
    }
}
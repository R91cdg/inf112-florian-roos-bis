package fr.tp.slr201.projects.robotsim.service.service.implem;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.slr201.projects.robotsim.service.service.SimulationService;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationServiceImpl.class);
    
    private final Map<String, Factory> simulatedFactories = new HashMap<>();

    private static final String PERSISTENCE_HOST = "127.0.0.1";
    private static final int PERSISTENCE_PORT = 51100;

    @Override
    public boolean startSimulation(String factoryId) {
        logger.info("Attempting to start simulation for factory ID: {}", factoryId);

        if (simulatedFactories.containsKey(factoryId)) {
            logger.warn("Factory {} is already running.", factoryId);
            return false;
        }

        Factory factory = fetchFactoryFromPersistence(factoryId);

        if (factory != null) {
            simulatedFactories.put(factoryId, factory);
            
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
                logger.error("Received object is not a Factory: {}", response.getClass().getName());
                return null;
            }

        } catch (Exception e) {
            logger.error("Error communicating with persistence server", e);
            return null;
        }
    }

    @Override
    public Factory getSimulatedFactory(String factoryId) {
        logger.info("Retrieving simulated factory for ID: {}", factoryId);
        return simulatedFactories.get(factoryId);
    }

    @Override
    public boolean stopSimulation(String factoryId) {
        logger.info("Attempting to stop simulation for factory ID: {}", factoryId);
        if (simulatedFactories.containsKey(factoryId)) {
            Factory factory = simulatedFactories.get(factoryId);
            factory.stopSimulation();
            simulatedFactories.remove(factoryId);
            logger.info("Simulation stopped successfully for factory ID: {}", factoryId);
            return true;
        }
        logger.warn("No simulation found for factory ID: {}", factoryId);
        return false;
    }
}
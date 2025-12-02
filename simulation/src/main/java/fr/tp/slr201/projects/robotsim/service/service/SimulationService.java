package fr.tp.slr201.projects.robotsim.service.service;

import fr.tp.inf112.projects.robotsim.model.Factory;

public interface SimulationService {

    boolean startSimulation(String factoryId);

    Factory getSimulatedFactory(String factoryId);

    boolean stopSimulation(String factoryId);
}

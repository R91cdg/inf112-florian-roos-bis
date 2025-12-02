package fr.tp.slr201.projects.robotsim.service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.slr201.projects.robotsim.service.service.SimulationService;

@RestController
@RequestMapping("/simulation")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

    @Autowired
    private SimulationService simulationService;

    @PostMapping("/start/{factoryId}")
    public boolean startSimulation(@PathVariable String factoryId) {
        logger.info("Request to start simulation for factory ID: {}", factoryId);
        return simulationService.startSimulation(factoryId);
    }

    @GetMapping("/{factoryId}")
    public Factory getSimulatedFactory(@PathVariable String factoryId) {
        logger.info("Request to get simulated factory for ID: {}", factoryId);
        return simulationService.getSimulatedFactory(factoryId);
    }

    @PostMapping("/stop/{factoryId}")
    public boolean stopSimulation(@PathVariable String factoryId) {
        logger.info("Request to stop simulation for factory ID: {}", factoryId);
        return simulationService.stopSimulation(factoryId);
    }
}
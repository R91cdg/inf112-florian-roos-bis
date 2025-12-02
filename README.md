## Installation

1. Import via **Existing Maven Projects**.
2. Run a **Maven Update Project** (Force Update) on the root folder to resolve module dependencies.

## Execution

A **Launch Group** named `Global launch` has been configured to start the components in the correct order.

If the Launch Group is unavailable, here is the manual execution order:

1. **Server** (Persistence Backend): `fr.tp.inf112.projects.robotsim.app.server`
2. **Simulation** (Spring Boot Microservice): `fr.tp.slr201.projects.robotsim.service.SimulationApplication`
   *(Wait for the service to fully start)*
3. **App** (Controller): `fr.tp.inf112.projects.robotsim.app.SimulatorApplication`

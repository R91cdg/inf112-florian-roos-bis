package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.canvas.model.impl.RGBColor;
import fr.tp.inf112.projects.robotsim.model.motion.Motion;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Robot extends Component {
    
    private static final long serialVersionUID = -1218857231970296747L;
    
    private static final Logger LOGGER = Logger.getLogger(Robot.class.getName());

    private static final Style STYLE = new ComponentStyle(RGBColor.GREEN, RGBColor.BLACK, 3.0f, null);

    private static final Style BLOCKED_STYLE = new ComponentStyle(RGBColor.RED, RGBColor.BLACK, 3.0f, new float[]{4.0f});

    private final Battery battery;
    
    private int speed;
    
    @JsonIdentityReference(alwaysAsId = true)
    private List<Component> targetComponents;
    
    private transient Iterator<Component> targetComponentsIterator;
    
    private Component currTargetComponent;
    
    private transient Iterator<Position> currentPathPositionsIter;
    
    private boolean blocked;
    
    // Indique si le robot est bloqué car aucun chemin n'a été trouvé vers la cible
    private boolean pathNotFound;
    
    private Position memorizedTargetPosition;

    private Position nextPosition; 
    
    private FactoryPathFinder pathFinder;

    // Variable transient pour gérer l'initialisation après désérialisation
    private transient boolean pathFinderInitialized = false;

    public Robot(final Factory factory,
                 final FactoryPathFinder pathFinder,
                 final CircularShape shape,
                 final Battery battery,
                 final String name ) {
        super(factory, shape, name);
        
        this.pathFinder = pathFinder;
        
        this.battery = battery;
        
        targetComponents = new ArrayList<>();
        currTargetComponent = null;
        currentPathPositionsIter = null;
        speed = 5;
        blocked = false;
        pathNotFound = false;
        memorizedTargetPosition = null;
        nextPosition = null; 
        pathFinderInitialized = true; 
    }
    
    public Robot() {
        this(null, null, null, null, null);
    }

    @Override
    public String toString() {
        return super.toString() + " battery=" + battery + "]";
    }

    protected int getSpeed() {
        return speed;
    }

    protected void setSpeed(final int speed) {
        this.speed = speed;
    }
    
    @JsonIgnore
    public Position getMemorizedTargetPosition() {
        return memorizedTargetPosition;
    }
    
    public List<Component> getTargetComponents() {
        if (targetComponents == null) {
            targetComponents = new ArrayList<>();
        }
        
        return targetComponents;
    }
    
    public boolean addTargetComponent(final Component targetComponent) {
        return getTargetComponents().add(targetComponent);
    }
    
    public boolean removeTargetComponent(final Component targetComponent) {
        return getTargetComponents().remove(targetComponent);
    }
    
    @Override
    public boolean isMobile() {
        return true;
    }

    @Override
    public boolean behave() {
        // Initialisation du PathFinder après désérialisation (côté serveur)
        if (pathFinder instanceof fr.tp.inf112.projects.robotsim.model.path.AbstractFactoryPathFinder) {
            var abstractFinder = (fr.tp.inf112.projects.robotsim.model.path.AbstractFactoryPathFinder) pathFinder;
            
            if (!pathFinderInitialized || abstractFinder.getFactoryModel() == null) {
                LOGGER.info("Robot " + getName() + ": Initializing PathFinder after deserialization...");
                abstractFinder.setFactoryModel(getFactory());
                try {
                    abstractFinder.buildGraph();
                    pathFinderInitialized = true;
                    LOGGER.info("Robot " + getName() + ": PathFinder graph built successfully.");
                } catch (Throwable e) {
                    LOGGER.severe("Robot " + getName() + ": CRITICAL ERROR building graph! " + e.toString());
                    e.printStackTrace();
                    return false;
                }
            }
        }

        if (getTargetComponents().isEmpty()) {
            LOGGER.fine("Robot " + getName() + ": No target components");
            return false;
        }
        
        // Si le robot est bloqué car aucun chemin n'a été trouvé, il reste immobile
        if (pathNotFound) {
            blocked = true; // Assurer que le robot est affiché en rouge
            LOGGER.info("Robot " + getName() + ": Permanently blocked - no path to " + currTargetComponent.getName());
            return false;
        }
        
        if (currTargetComponent == null || hasReachedCurrentTarget()) {
            if (currTargetComponent != null) {
                LOGGER.info("Robot " + getName() + " REACHED target: " + currTargetComponent.getName() + " at position " + getPosition());
            }
            currTargetComponent = nextTargetComponentToVisit();
            
            if (currTargetComponent != null) {
                LOGGER.info("Robot " + getName() + " NEW TARGET: " + currTargetComponent.getName() + " at position " + currTargetComponent.getPosition());
                computePathToCurrentTargetComponent();
                
                // Si aucun chemin n'a été trouvé, le robot devient définitivement bloqué
                if (pathNotFound) {
                    LOGGER.warning("Robot " + getName() + ": Cannot reach " + currTargetComponent.getName() + " - robot will stay blocked");
                    return false;
                }
            }
        }

        int displacement = moveToNextPathPosition();
        LOGGER.fine("Robot " + getName() + " displacement: " + displacement + ", position: " + getPosition() + ", blocked: " + blocked);
        return displacement != 0;
    }
        
    private Component nextTargetComponentToVisit() {
        if (targetComponentsIterator == null || !targetComponentsIterator.hasNext()) {
            targetComponentsIterator = getTargetComponents().iterator();
        }
        
        return targetComponentsIterator.hasNext() ? targetComponentsIterator.next() : null;
    }
    
    private int moveToNextPathPosition() {
        final Motion motion = computeMotion();
        
        if (motion == null) {
            LOGGER.info("Robot " + getName() + ": No motion computed (null), blocked=" + blocked + ", memorizedTargetPosition=" + memorizedTargetPosition);
        } else {
            LOGGER.info("Robot " + getName() + ": Motion from " + motion.getCurrentPosition() + " to " + motion.getTargetPosition());
        }
        
        int displacement = motion == null ? 0 : getFactory().moveComponent(motion, this);

        if (displacement != 0) {
            blocked = false;
            LOGGER.info("Robot " + getName() + ": Moved successfully, displacement=" + displacement + ", new position=" + getPosition());
            notifyObservers();
        }
        else if (isLivelyLocked()) {
            LOGGER.info("Robot " + getName() + ": LIVELOCK DETECTED at position " + getPosition());
            
            // Vérifier si l'autre robot est définitivement bloqué (pathNotFound)
            final Component otherComponent = getFactory().getMobileComponentAt(memorizedTargetPosition, this);
            if (otherComponent instanceof Robot && ((Robot) otherComponent).isPathNotFound()) {
                // L'autre robot ne bougera jamais - passer à la cible suivante
                LOGGER.info("Robot " + getName() + ": Other robot is permanently blocked, skipping to next target");
                currTargetComponent = nextTargetComponentToVisit();
                if (currTargetComponent != null) {
                    LOGGER.info("Robot " + getName() + " NEW TARGET (skipped blocked): " + currTargetComponent.getName());
                    computePathToCurrentTargetComponent();
                    this.memorizedTargetPosition = null;
                    blocked = false;
                }
                return 0;
            }
            
            final Position freeNeighbouringPosition = findFreeNeighbouringPosition();
            if (freeNeighbouringPosition != null) {
                LOGGER.info("Robot " + getName() + ": Moving to free position " + freeNeighbouringPosition);
                this.nextPosition = freeNeighbouringPosition;
                this.memorizedTargetPosition = null;
                final Motion escapeMotion = new Motion(getPosition(), freeNeighbouringPosition);
                displacement = getFactory().moveComponent(escapeMotion, this);
                if (displacement != 0) {
                    blocked = false;
                    notifyObservers();
                    computePathToCurrentTargetComponent();
                } else {
                    LOGGER.warning("Robot " + getName() + ": Failed to escape livelock");
                }
            } else {
                LOGGER.warning("Robot " + getName() + ": No free neighbouring position found to escape livelock");
            }
        } else {
            LOGGER.info("Robot " + getName() + ": Blocked but no livelock, waiting... memorizedTargetPosition=" + memorizedTargetPosition);
        }
        return displacement;
    }
    
    private Position findFreeNeighbouringPosition() {
        Position currentPosition = getPosition();

        int step = getSpeed(); 
        Position[] neighbouringPositions = new Position[] {
            new Position(currentPosition.getxCoordinate(), currentPosition.getyCoordinate() - step), 
            new Position(currentPosition.getxCoordinate(), currentPosition.getyCoordinate() + step), 
            new Position(currentPosition.getxCoordinate() - step, currentPosition.getyCoordinate()), 
            new Position(currentPosition.getxCoordinate() + step, currentPosition.getyCoordinate())  
        };

        for (Position position : neighbouringPositions) {
            if (!getFactory().hasMobileComponentAt(new RectangularShape(position.getxCoordinate(), 
                                                                        position.getyCoordinate(), 
                                                                        2, 2), 
                                                this)) {
                return position; 
            }
        }

        return null;
    }

    private void computePathToCurrentTargetComponent() {
        try {
            LOGGER.info("Robot " + getName() + ": Computing path from " + getPosition() + " to " + currTargetComponent.getName() + " at " + currTargetComponent.getPosition());
            final List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);
            
            if (currentPathPositions != null && !currentPathPositions.isEmpty()) {
                LOGGER.info("Robot " + getName() + ": Path found with " + currentPathPositions.size() + " steps.");
                currentPathPositionsIter = currentPathPositions.iterator();
                pathNotFound = false;
                blocked = false;
            } else {
                LOGGER.warning("Robot " + getName() + ": No path found to " + currTargetComponent.getName());
                currentPathPositionsIter = null;
                pathNotFound = true;
                blocked = true;
            }
        } catch (Exception e) {
            LOGGER.severe("Robot " + getName() + ": Error computing path: " + e.getMessage());
            e.printStackTrace();
            pathNotFound = true;
            blocked = true;
        }
    }
    
    private Motion computeMotion() {
        Position targetPosition = getTargetPosition();
        
        if (targetPosition == null) {
            LOGGER.info("Robot " + getName() + ": No target position (path exhausted or not computed)");
            // Vérifier si on est bloqué par un autre robot sur notre position actuelle
            // Chercher les robots voisins qui pourraient nous bloquer
            if (currTargetComponent != null && !hasReachedCurrentTarget()) {
                // On n'a plus de chemin mais on n'a pas atteint la cible - recalculer
                LOGGER.info("Robot " + getName() + ": Path exhausted but target not reached, recomputing path...");
                computePathToCurrentTargetComponent();
                targetPosition = getTargetPosition();
                if (targetPosition != null) {
                    // Continuer avec la nouvelle position
                    LOGGER.info("Robot " + getName() + ": New path computed, target position: " + targetPosition);
                } else {
                    blocked = true;
                    return null;
                }
            } else {
                blocked = true;
                return null;
            }
        }
        
        LOGGER.info("Robot " + getName() + ": Target position is " + targetPosition);
        
        final PositionedShape shape = new RectangularShape(targetPosition.getxCoordinate(),
                                                           targetPosition.getyCoordinate(),
                                                              2,
                                                              2);
        
        if (getFactory().hasMobileComponentAt(shape, this)) {
            LOGGER.info("Robot " + getName() + ": Another robot is at target position " + targetPosition);
            this.memorizedTargetPosition = targetPosition;
            blocked = true;
            return null;
        }

        this.memorizedTargetPosition = null;
        blocked = false;
            
        return new Motion(getPosition(), targetPosition);
    }
    
    public Position getTargetPosition() {
        if (this.nextPosition != null) {
            Position temp = this.nextPosition;
            this.nextPosition = null;
            LOGGER.info("Robot " + getName() + ": Using nextPosition (escape position) " + temp);
            return temp;
        }

        if (this.memorizedTargetPosition != null) {
            LOGGER.info("Robot " + getName() + ": Using memorizedTargetPosition " + memorizedTargetPosition);
            return this.memorizedTargetPosition;
        }

        if (currentPathPositionsIter != null && currentPathPositionsIter.hasNext()) {
            Position next = currentPathPositionsIter.next();
            LOGGER.info("Robot " + getName() + ": Next position from path " + next);
            return next;
        }

        LOGGER.info("Robot " + getName() + ": No more positions in path (iterator null or empty)");
        return null;
    }
    
    @JsonIgnore
    public boolean isLivelyLocked() {
        if (memorizedTargetPosition == null) {
            return false;
        }
            
        final Component otherComponent = getFactory().getMobileComponentAt(memorizedTargetPosition, this);

        if (otherComponent instanceof Robot) {
            Robot otherRobot = (Robot) otherComponent;
            // Livelock classique : les deux robots veulent aller à la position de l'autre
            if (getPosition().equals(otherRobot.getMemorizedTargetPosition())) {
                return true;
            }
            // Nouveau cas : l'autre robot est définitivement bloqué (pathNotFound)
            // Dans ce cas, on doit aussi contourner
            if (otherRobot.isPathNotFound()) {
                LOGGER.info("Robot " + getName() + ": Other robot " + otherRobot.getName() + " is permanently blocked, need to go around");
                return true;
            }
        }
        
        return false;
    }
    
    @JsonIgnore
    public boolean isPathNotFound() {
        return pathNotFound;
    }

    private boolean hasReachedCurrentTarget() {
        return getPositionedShape().overlays(currTargetComponent.getPositionedShape());
    }
    
    @Override
    public boolean canBeOverlayed(final PositionedShape shape) {
        return true;
    }
    
    @Override
    public Style getStyle() {
        return blocked ? BLOCKED_STYLE : STYLE;
    }
    
    @JsonIgnore
    public FactoryPathFinder getPathFinder() {
        return pathFinder;
    }
}

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
    
    private transient boolean blocked;
    
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
            return false;
        }
        
        if (currTargetComponent == null || hasReachedCurrentTarget()) {
            currTargetComponent = nextTargetComponentToVisit();
            
            if (currTargetComponent != null) {
                LOGGER.info("Robot " + getName() + " targeting: " + currTargetComponent.getName());
                computePathToCurrentTargetComponent();
            }
        }

        return moveToNextPathPosition() != 0;
    }
        
    private Component nextTargetComponentToVisit() {
        if (targetComponentsIterator == null || !targetComponentsIterator.hasNext()) {
            targetComponentsIterator = getTargetComponents().iterator();
        }
        
        return targetComponentsIterator.hasNext() ? targetComponentsIterator.next() : null;
    }
    
    private int moveToNextPathPosition() {
        final Motion motion = computeMotion();
        int displacement = motion == null ? 0 : getFactory().moveComponent(motion, this);

        if (displacement != 0) {
            notifyObservers();
        }
        else if (isLivelyLocked()) {
            // CORRECTION TYPO ICI : freeNeighbouringPosition
            final Position freeNeighbouringPosition = findFreeNeighbouringPosition();
            if (freeNeighbouringPosition != null) {
                this.nextPosition = freeNeighbouringPosition; 
                displacement = moveToNextPathPosition(); 
                computePathToCurrentTargetComponent(); 
            }
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
            final List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);
            
            if (currentPathPositions != null && !currentPathPositions.isEmpty()) {
                LOGGER.info("Robot " + getName() + ": Path found with " + currentPathPositions.size() + " steps.");
                currentPathPositionsIter = currentPathPositions.iterator();
            } else {
                LOGGER.warning("Robot " + getName() + ": No path found to " + currTargetComponent.getName());
                currentPathPositionsIter = null;
            }
        } catch (Exception e) {
            LOGGER.severe("Robot " + getName() + ": Error computing path: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private Motion computeMotion() {
        Position targetPosition = getTargetPosition();
        
        if (targetPosition == null) {
            blocked = true;
            return null;
        }
        
        final PositionedShape shape = new RectangularShape(targetPosition.getxCoordinate(),
                                                           targetPosition.getyCoordinate(),
                                                              2,
                                                              2);
        
        if (getFactory().hasMobileComponentAt(shape, this)) {
            this.memorizedTargetPosition = targetPosition;
            return null;
        }

        this.memorizedTargetPosition = null;
            
        return new Motion(getPosition(), targetPosition);
    }
    
    public Position getTargetPosition() {
        if (this.nextPosition != null) {
            Position temp = this.nextPosition;
            this.nextPosition = null;
            return temp;
        }

        if (this.memorizedTargetPosition != null) {
            return this.memorizedTargetPosition;
        }

        if (currentPathPositionsIter != null && currentPathPositionsIter.hasNext()) {
            return currentPathPositionsIter.next();
        }

        return null;
    }
    
    @JsonIgnore
    public boolean isLivelyLocked() {
        if (memorizedTargetPosition == null) {
            return false;
        }
            
        final Component otherComponent = getFactory().getMobileComponentAt(memorizedTargetPosition,     
                                                                       this);

        if (otherComponent instanceof Robot)  {
            return getPosition().equals(((Robot) otherComponent).getMemorizedTargetPosition());
        }
        
        return false;
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
package fr.tp.inf112.projects.robotsim.model.shapes;

import fr.tp.inf112.projects.canvas.model.OvalShape;
import com.fasterxml.jackson.annotation.JsonIgnore; 

public class CircularShape extends PositionedShape implements OvalShape {
	
	private static final long serialVersionUID = -1912941556210518344L;

	public final int radius;
	
	public CircularShape() {
	    this(0, 0, 0);
	}
	
	public CircularShape( 	final int xCoordinate,
							final int yCoordinate,
							final int radius ) {
		super( xCoordinate, yCoordinate );
		
		this.radius = radius;
	}

	@Override
	@JsonIgnore
	public int getWidth() {
		return 2 * radius;
	}

	@Override
	@JsonIgnore
	public int getHeight() {
		return getWidth();
	}

	@Override
	public String toString() {
		return super.toString() + " [radius=" + radius + "]";
	}
}

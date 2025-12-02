package fr.tp.inf112.projects.robotsim.model;

import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;

public class Puck extends Component {

	private static final long serialVersionUID = -2194778403928041427L;

	public Puck(final Factory factory,
				final CircularShape shape,
				final String name) {
		super(factory, shape, name);
	}

	public Puck() {
		super(null, null, null);
	}

	@Override
	public String toString() {
		return super.toString() + "]";
	}

	@Override
	public boolean behave() {
		return true;
	}
}
package amy.astar;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;

/**
 * This action takes a path as input and outputs the primitive commands 
 * necessary to follow the path.  Path following is accomplished using pd-control.
 * 
 * This tries to move to the next vertex as we slow down from the current one (rather than getting
 * all the way to the next one)
 * 
 * @author amy
 *
 */
public class FollowPathActionFaster {
	Vertex[] path;
	int currentVertex;
	boolean finishedShortAction;
	AbstractAction lastCommand;

	public FollowPathActionFaster() {
		path = null;
		currentVertex = -1;
		lastCommand = null;
	}

	public FollowPathActionFaster (Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}

	public void followNewPath(Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}

	/**
	 * 
	 * @param state
	 * @param ship
	 * @return
	 */
	public AbstractAction followPath(Toroidal2DPhysics state, Ship ship) {
		//System.out.println("Following path at current action " + currentVertex);

		// safety case:  break if we have a null path
		if (path == null || currentVertex < 0) {
			DoNothingAction doNothing = new DoNothingAction();
			lastCommand = doNothing;
			return lastCommand;
		}
		
		if (lastCommand == null || lastCommand.isMovementFinished(state)) {
			currentVertex++;
			// force a replan every time a vertex is reached

			if (currentVertex >= path.length) {
				//System.out.println("Done!");
				DoNothingAction doNothing = new DoNothingAction();
				lastCommand = doNothing;
			} else {
				MoveAction command = new MoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				//AbstractAction command = new MyFasterMoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				lastCommand = command;
			}
		}

		// are we starting to slow down?
		Movement movement = lastCommand.getMovement(state, ship);
		//System.out.println("Movement is " + movement.getTranslationalAcceleration());
		if (movement.getTranslationalAcceleration().getXValue() < 0 || movement.getTranslationalAcceleration().getYValue() < 0) {
			//System.out.println("Going to the next vertex");
			currentVertex++;
		}

		//System.out.println("Current command " + command);
		return lastCommand;
	}



}

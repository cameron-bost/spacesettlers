package amy.astar;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * This action takes a path as input and outputs the primitive commands 
 * necessary to follow the path.  Path following is accomplished using pd-control.
 * @author amy
 *
 */
public class FollowPathAction {
	Vertex[] path;
	int currentVertex;
	boolean finishedShortAction;
	AbstractAction lastCommand;

	public FollowPathAction() {
		path = null;
		currentVertex = -1;
		lastCommand = null;
	}

	public FollowPathAction (Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}

	public void followNewPath(Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}
	
	/**
	 * Return the current vertex (for debugging and graphics)
	 * @return
	 */
	public Vertex getCurrentVertex() {
		if (currentVertex >= 0) {
			return path[currentVertex];
		} else {
			return null;
		}
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
			//System.out.println("Do nothing because path is null or vertex is < 0" + path + " " + currentVertex);
			DoNothingAction doNothing = new DoNothingAction();
			lastCommand = doNothing;
			return lastCommand;
		}
		
		if (lastCommand == null || lastCommand.isMovementFinished(state)) {
			currentVertex++;
			// force a replan every time a vertex is reached

			if (currentVertex >= path.length) {
				//System.out.println("Done! Need to replan!");
				DoNothingAction doNothing = new DoNothingAction();
				lastCommand = doNothing;
			} else {
				//System.out.println("Going to current vertex " + currentVertex + " at position " + path[currentVertex].getPosition() + " from position " + ship.getPosition());
				MoveAction command = new MoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				//AbstractAction command = new MyFasterMoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				lastCommand = command;
			}
		}

		//System.out.println("Current command " + lastCommand);
		return lastCommand;
	}



}

package bost7517;

import java.util.Set;

import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Represents the state of the system.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 *
 */
public class BDSM_P4_State {
	/**Number of ships expected in goal state*/
	static final int NUM_SHIPS = 4;
	/**Number of bases expected in goal state*/
	static final int NUM_BASES = 4;
	
	private Ship[] ships;
	private Base[] bases;
	
	/**
	 * Constructor. Physics model is required.
	 * 
	 * @param space physics model
	 */
	public BDSM_P4_State(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		ships = new Ship[NUM_SHIPS];
		bases = new Base[NUM_BASES];
		initStateArrays(actionableObjects);
	}
	
	public void applyAction(Toroidal2DPhysics space, BDSM_P4_Action action) {
		action.doAction(space);
	}
	
	private void initStateArrays(Set<AbstractActionableObject> actionableObjects) {
		int shipIdx = 0, baseIdx = 0;
		for(AbstractActionableObject obj: actionableObjects) {
			if(obj instanceof Ship) {
				ships[shipIdx++] = (Ship) obj;
			}
			else if(obj instanceof Base) {
				bases[baseIdx++] = (Base) obj;
			}
		}
	}
	
	/**
	 * Goal state definition: 3 guard ships placed
	 * 
	 * @param space physics model
	 * @param ships all ships on this team
	 * @param bases all bases for this team
	 * @return whether the goal state is reached
	 */
	public boolean isGoalState(Toroidal2DPhysics space) {
		// Sanity check in event of poor programming
		if(ships.length != NUM_SHIPS || bases.length != NUM_BASES) {
			System.err.format("<Planner.isGoal> - Wrong list size. Ships.size = %d, Bases.size = %d\n", ships.length, bases.length);
			return false;
		}
		
		// Determine if goal state has been reached
		boolean isGoal = true;
		for(int i = 0; i<NUM_SHIPS; i++) {
			Ship s = ships[i];
			Base b = bases[i];
			boolean isGuarding = BDSM_P4_Relations.isGuarding(space, s, b);
			if(AgentUtils.DEBUG) {
				System.out.format("<Planner.isGoal> - Is ship %d guarding base %d? %s\n", i, i, Boolean.toString(isGuarding));
			}
			isGoal &= isGuarding;
		}
		return isGoal;
	}

	public Ship[] getShips() {
		return ships;
	}
}

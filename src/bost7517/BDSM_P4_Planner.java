package bost7517;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import spacesettlers.actions.AbstractAction;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Planner class, capable of determining a sequence of actions 
 * to lead to the system's goal state.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public class BDSM_P4_Planner {
	private BDSM_P4_State state;
	
	public BDSM_P4_Planner(BDSM_P4_State state) {
		this.state = state;
	}
	/**
	 * Performs BFS forward search to navigate from initial state to goal state.
	 * Once a path has been found, each ship is given their next action via the return value.
	 * 
	 * @param space physics model
	 * @return Map of all team object IDs to their corresponding actions
	 */
	public HashMap<UUID, AbstractAction> getNextActions(Toroidal2DPhysics space){
		HashMap<UUID, AbstractAction> ret = new HashMap<>();
		BDSM_P4_PlanPath bestPath = null;
		Queue<BDSM_P4_PlanPath> q = new LinkedBlockingQueue<>();
		q.add(new BDSM_P4_PlanPath(state));
		while(!q.isEmpty()) {
			BDSM_P4_PlanPath tPath = q.poll();
			if(state.isGoalState(space)) {
				bestPath = tPath;
				break;
			}
			else {
				// Do next round of BFS
			}
		}
		
		// If a path to goal state cannot be found, a bad has been did.
		if(bestPath != null) {
			return bestPath.getActions();
		}
		return ret;
	}
	
	static class BDSM_P4_PlanPath{
		LinkedList<BDSM_P4_Action> actions;
		BDSM_P4_State state;
		
		BDSM_P4_PlanPath(BDSM_P4_State initialState){
			actions = new LinkedList<>();
			state = initialState;
		}
		
		HashMap<UUID, AbstractAction> getActions(){
			HashMap<UUID, AbstractAction> ret = new HashMap<>();
			for(Ship s: state.getShips()) {
				for(BDSM_P4_Action action: actions) {
					// TODO associate action with actor, put into map
				}
			}
			return ret;
		}
		
		static BDSM_P4_PlanPath makeCopyOf(BDSM_P4_PlanPath path){
			BDSM_P4_PlanPath ret = new BDSM_P4_PlanPath(path.state);
			for(BDSM_P4_Action action: path.actions) {
				ret.actions.add(action);
			}
			return ret;
		}
	}
}

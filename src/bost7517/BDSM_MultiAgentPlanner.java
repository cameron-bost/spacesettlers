package bost7517;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

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
public class BDSM_MultiAgentPlanner {
	/**State of system*/
	private BDSM_PlanState state;
	/**Actor's UUID*/
	private UUID actorId;
	
	/**
	 * Planner requires full game state
	 * 
	 * @param space physics model
	 * @param actionableObjects actionable objects
	 * @param s ship object
	 * @param role ship's role
	 */
	public BDSM_MultiAgentPlanner(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship s, BDSM_ShipRole role) {
		state = new BDSM_PlanState(space, actionableObjects, s, role);
		actorId = s.getId();
	}
	
	/**
	 * Performs BFS forward search to navigate from initial state to goal state.
	 * Once a path has been found, each ship is given their next action via the return value.
	 * 
	 * @return Best action for this ship
	 */
	public BDSM_PlanActions getNextAction() {
		// Run BFS to find path to goal. once path is found, return first action for this ship
		Queue<BDSM_PlanPath> q = new LinkedBlockingQueue<>();
		BDSM_PlanPath initialPath = new BDSM_PlanPath(state), bestPath = initialPath;
		q.add(initialPath);
		int numIterations = 0;
		while(!q.isEmpty() && numIterations <= 1000) {
			BDSM_PlanPath tPath = q.poll();
			
			// Goal check
			if(tPath.state.isGoalState()) {
				bestPath = tPath;
				break;
			}
			
			
			// BFS sweep, perform all actions with accepted preconditions, add to queue
			for(BDSM_PlanAction action: BDSM_PlanActions.genAllPossibleActions(tPath.state, actorId)) {
				if(action.preCondition()) {
					BDSM_PlanPath tPathCopy = BDSM_PlanPath.makeCopyOf(tPath);
					tPathCopy.addAction(action);
					q.add(tPathCopy);
				}
			}
			numIterations++;
		}
		
		// Apply actor action to overall state
		BDSM_PlanAction actortion = bestPath.getNextActionForActor(actorId);
		actortion.setState(this.state);
		actortion.applyAction();
		return actortion.getAction();
	}
	
	/**
	 * Path through the planning search tree.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC 
	 * @version 0.4
	 */
	static class BDSM_PlanPath{
		LinkedList<BDSM_PlanAction> actions;
		BDSM_PlanState state;
		HashMap<UUID, BDSM_PlanAction> actionMap;
		
		BDSM_PlanPath(BDSM_PlanState initialState){
			actions = new LinkedList<>();
			actionMap = new HashMap<>();
			state = initialState;
		}
		
		public BDSM_PlanAction getNextActionForActor(UUID actorId) {
			return actionMap.containsKey(actorId) ? actionMap.get(actorId)
					: new BDSM_PlanActions.BDSM_DoNothingAction(state, actorId);
		}

		/**
		 * Applies action effect to state. If this is
		 * the specified actor's first action, it is
		 * noted in the actionMap.
		 * 
		 * @param action action to be applied
		 */
		void addAction(BDSM_PlanAction action) {
			state = action.applyAction();
			actions.add(action);
			// Only mark actions that are performed by the actor being planned for
			if(action.getActor() != BDSM_PlanAction.NO_ACTOR) {
				actionMap.putIfAbsent(action.getActor(), action);
			}
		}

		static BDSM_PlanPath makeCopyOf(BDSM_PlanPath path){
			BDSM_PlanPath ret = new BDSM_PlanPath(BDSM_PlanState.makeCopyOf(path.state));
			for(BDSM_PlanAction action: path.actions) {
				ret.addAction(action);
			}
			return ret;
		}
	}
}

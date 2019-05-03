package bost7517;

import java.util.UUID;

import bost7517.BDSM_P4_Planner.BDSM_P4_State;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Representation of a high-level action.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public abstract class BDSM_P4_Action {
	/**Flags for pre/postconditions having been met (prevents repeated computations)*/
	private boolean preMet = false, postMet = false;
	
	private BDSM_P4_State state;
	private UUID[] actors;
	
	BDSM_P4_Action(BDSM_P4_State _state){
		state = _state;
	}
	
	/**
	 * The precondition. This must be true before calling doAction()
	 * @return whether the precondition has been met.
	 */
	public boolean preCondition() {
		return preMet || _preCondition(state);
	}
	
	/**
	 * Precondition logic goes here.
	 * 
	 * @param state current state
	 * @return whether the precondition has been met
	 */
	protected abstract boolean _preCondition(BDSM_P4_State state);
	
	/**
	 * The postcondition. This must be true before terminating the action.
	 * @return whether the postcondition has been met.
	 */
	public abstract boolean postCondition(Toroidal2DPhysics space);
	
	/**
	 * Accessor for corresponding enum entry
	 * 
	 * @return corresponding enum entry in BDSM_P4_Actions
	 */
	public abstract BDSM_P4_Actions getAction();
}

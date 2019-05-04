package bost7517;

import java.util.LinkedList;
import java.util.UUID;

import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Representation of a high-level action, used for planning.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public abstract class BDSM_PlanAction {
	/**Randomly generated ID to mark unnecessary actions*/
	public static final UUID NO_ACTOR = UUID.randomUUID();
	/**Flags for pre/postconditions having been met (prevents repeated computations)*/
	private boolean preMet = false;
	/**Planning State*/
	protected BDSM_PlanState state;
	protected UUID actorId;
	
	BDSM_PlanAction(BDSM_PlanState _state, UUID _actorId){
		state = _state;
		this.actorId = _actorId;
	}
	
	/**
	 * The precondition. This must be true before calling doAction()
	 * @return whether the precondition has been met.
	 */
	public boolean preCondition() {
		return preMet || (preMet = _preCondition());
	}
	
	/**
	 * Precondition logic goes here.
	 * 
	 * @param state current state
	 * @return whether the precondition has been met
	 */
	protected abstract boolean _preCondition();
	
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
	public abstract BDSM_PlanActions getAction();
	
	/**
	 * Applies effects of the action to the state field.
	 * 
	 * @return state object after modification
	 */
	abstract BDSM_PlanState applyAction();

	/**
	 * Accessor for primary actor's UUID
	 * 
	 * @return primary actor's UUID
	 */
	public UUID getActor() {
		return actorId;
	}

	/**
	 * Sets the state to a different value.
	 * 
	 * @param state state to be set to
	 */
	public void setState(BDSM_PlanState state) {
		this.state = state;
	}
	
	/**
	 * Generates all possible actions for some given state
	 * 
	 * @return 
	 */
	public abstract LinkedList<? extends BDSM_PlanAction> genAllActions(BDSM_PlanState state);
}

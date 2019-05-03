package bost7517;

import java.util.List;
import java.util.LinkedList;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
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
	
	public BDSM_P4_Action(BDSM_P4_State state) {
		this.state = state;
	}
	
	public BDSM_P4_State getState() {
		return state;
	}
	
	/**
	 * The precondition. This must be true before calling doAction()
	 * @return whether the precondition has been met.
	 */
	public abstract boolean preCondition(Toroidal2DPhysics space);
	/**
	 * The postcondition. This must be true before terminating the action.
	 * @return whether the postcondition has been met.
	 */
	public abstract boolean postCondition(Toroidal2DPhysics space);
	/**
	 * Performs action logic
	 */
	protected abstract List<AbstractAction> _doAction(Toroidal2DPhysics space);
	
	/**
	 * Initializes the action and any necessary structures
	 */
	protected abstract void _beginAction(Toroidal2DPhysics space);
	
	/**
	 * Performs end of action logic. May be empty.
	 */
	protected abstract void _endAction(Toroidal2DPhysics space);
	
	/**
	 * Performs action if precondition has been met
	 * 
	 * @param space physics model
	 * @return List of actions to perform for this high-level action
	 */
	public List<AbstractAction> doAction(Toroidal2DPhysics space) {
		LinkedList<AbstractAction> ret = new LinkedList<>();
		// Precondition is not met
		if(!preMet) {
			if(preCondition(space)) {
				preMet = true;
				_beginAction(space);
				return _doAction(space);
			}
			// Precondition is not met, don't do anything for this action
			else {
				ret.add(new DoNothingAction());
			}
		}
		// Precondition is met, postcondition is not
		else if(!postMet){
			// Postcondition has been met
			if(postCondition(space)) {
				postMet = true;
				_endAction(space);
				ret.add(new DoNothingAction());
			}
			// Postcondition has not been met, proceed normally
			else {
				return _doAction(space);
			}
		}
		// Precondition and postcondition are met
		else{
			// Do nothing
			ret.add(new DoNothingAction());
		}
		
		return ret;
	}
}

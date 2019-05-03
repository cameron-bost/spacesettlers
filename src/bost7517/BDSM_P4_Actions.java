package bost7517;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Defines the action space for the planner.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public enum BDSM_P4_Actions {
	GetEnergy, ReturnFlag,				// Universal actions
	DeliverFlag, CaptureFlag, Guard,	// Flag actions
	DumpResources, GetResoruces;		// Resource actions
	
	static BDSM_P4_Actions[] allActions = new BDSM_P4_Actions[] {GetEnergy, ReturnFlag, DeliverFlag, CaptureFlag, Guard, DumpResources, GetResoruces};
	
	/**
	 * Retrieve all possible actions as array
	 * @return array of all possible actions
	 */
	public static BDSM_P4_Actions[] getAllActions() {
		return Arrays.copyOf(allActions, allActions.length);
	}
	
	/**
	 * Retrieves the literal action object for a 
	 * specified enum member.
	 * 
	 * @param action enum member to retrieve action for
	 * @param actor Primary actor for the action
	 * @return Action object, initialized with the actor
	 */
	public static BDSM_P4_Action getAction(BDSM_P4_State state, BDSM_P4_Actions action, AbstractActionableObject actor) {
		switch(action) {
		case GetEnergy:
			return new GetEnergyAction(state, (Ship) actor);
		case CaptureFlag:
			System.err.println("CaptureFlag not yet implemented.");
			break;
		case DeliverFlag:
			System.err.println("DeliverFlag not yet implemented.");
			break;
		case DumpResources:
			System.err.println("DumpResources not yet implemented.");
			break;
		case GetResoruces:
			System.err.println("GetResoruces not yet implemented.");
			break;
		case Guard:
			System.err.println("Guard not yet implemented.");
			break;
		case ReturnFlag:
			System.err.println("ReturnFlag not yet implemented.");
			break;
		default:
			System.err.println("Invalid action specified.");
			break;
		}
		if(AgentUtils.DEBUG) {
			System.out.println("Returning DoNothingAction...");
		}
		return new BDSM_DoNothingAction(state);
	}
	
	/**
	 * Action for retrieving energy.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 *
	 */
	static class GetEnergyAction extends BDSM_P4_Action{

		private Ship ship;
		
		final double SENTINEL_ENERGYINITIAL = -1.0;
		private double energyInitial;
		
		public GetEnergyAction(BDSM_P4_State state, Ship actor) {
			super(state);
			ship = actor;
			energyInitial = SENTINEL_ENERGYINITIAL;
		}

		@Override
		public boolean preCondition(Toroidal2DPhysics space) {
			return ship.getEnergy() <= AgentUtils.LOW_ENERGY_THRESHOLD;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			return ship.getEnergy() > energyInitial;
		}

		@Override
		protected List<AbstractAction> _doAction(Toroidal2DPhysics space) {
			if(energyInitial == SENTINEL_ENERGYINITIAL) {
				energyInitial = ship.getEnergy();
			}
			LinkedList<AbstractAction> ret = new LinkedList<>();
			
			AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
			LinkedList<Position> currentPath = AStarGraph.getPathTo(ship, energyTarget, space).getPositions();
			Position prevPos = null;
			for(Position currentPosition: currentPath) {
				AbstractAction nextAction = new BDSMMoveAction(space, prevPos == null ? ship.getPosition() : prevPos, currentPosition);
				ret.add(nextAction);
				prevPos = currentPosition;
			}
			return ret;
		}

		@Override
		protected void _beginAction(Toroidal2DPhysics space) {
			// TODO Auto-generated method stub
			
		}

		@Override
		protected void _endAction(Toroidal2DPhysics space) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	/**
	 * Action for doing nothing.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 */
	static class BDSM_DoNothingAction extends BDSM_P4_Action{

		public BDSM_DoNothingAction(BDSM_P4_State state) {
			super(state);
		}

		@Override
		public boolean preCondition(Toroidal2DPhysics space) {
			return true;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			return true;
		}

		@Override
		protected List<AbstractAction> _doAction(Toroidal2DPhysics space) {
			List<AbstractAction> ret = new LinkedList<>();
			ret.add(new DoNothingAction());
			return ret;
		}

		@Override
		protected void _beginAction(Toroidal2DPhysics space) {
			// Empty
		}

		@Override
		protected void _endAction(Toroidal2DPhysics space) {
			// Empty
		}
		
	}

}

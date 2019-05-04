package bost7517;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

import bost7517.BDSM_PlanState.StateShip;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Defines the action space for the planner.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public enum BDSM_PlanActions {
	GetEnergy, ReturnFlag,				// Universal actions
	DeliverFlag, CaptureFlag, Guard,	// Flag collector actions
	DumpResources, GetResoruces,		// Resource collector actions
	DoNothing;
	
	static BDSM_PlanActions[] allActions = new BDSM_PlanActions[] {GetEnergy, ReturnFlag, DeliverFlag, CaptureFlag, Guard, DumpResources, GetResoruces};
	
	/**
	 * Retrieve all possible actions as array
	 * @return array of all possible actions
	 */
	public static BDSM_PlanActions[] getAllActions() {
		return Arrays.copyOf(allActions, allActions.length);
	}
	
	/**
	 * Action for retrieving energy.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 *
	 */
	static class GetEnergyAction extends BDSM_PlanAction{
		StateShip actor;
		GetEnergyAction(BDSM_PlanState _state, UUID actorId) {
			super(_state, actorId);
			actor = state.getStateShipById(actorId);
		}

		@Override
		protected boolean _preCondition() {
			return actor.lowEnergy;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public BDSM_PlanActions getAction() {
			return GetEnergy;
		}

		@Override
		BDSM_PlanState applyAction() {
			actor.lowEnergy = false;
			return state;
		}

		@Override
		public LinkedList<? extends BDSM_PlanAction> genAllActions(BDSM_PlanState state) {
			LinkedList<GetEnergyAction> ret = new LinkedList<>();
			for(UUID shipId: state.idGameShipMap.keySet()) {
				GetEnergyAction tAction = new GetEnergyAction(state, shipId);
				if(tAction.preCondition()) {
					ret.add(tAction);
				}
			}
			return ret;
		}
		
	}
	
	/**
	 * Action for doing nothing.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 */
	static class DoNothingAction extends BDSM_PlanAction{

		DoNothingAction(BDSM_PlanState _state, UUID actorId) {
			super(_state, actorId);
		}

		@Override
		protected boolean _preCondition() {
			return true;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			return false;
		}

		@Override
		public BDSM_PlanActions getAction() {
			return DoNothing;
		}

		@Override
		BDSM_PlanState applyAction() {
			return state;
		}

		@Override
		public LinkedList<? extends BDSM_PlanAction> genAllActions(BDSM_PlanState state) {
			LinkedList<DoNothingAction> ret = new LinkedList<>();
			for(UUID shipId: state.idGameShipMap.keySet()) {
				ret.add(new DoNothingAction(state, shipId));
			}
			return ret;
		}
		
	}

	static class CaptureFlagAction extends BDSM_PlanAction{

		StateShip actor;
		CaptureFlagAction(BDSM_PlanState _state, UUID _actorId) {
			super(_state, _actorId);
			actor = state.getStateShipById(actorId);
		}

		@Override
		protected boolean _preCondition() {
			return !actor.isLoaded && !actor.lowEnergy && actor.isAlive;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public BDSM_PlanActions getAction() {
			return CaptureFlag;
		}

		@Override
		BDSM_PlanState applyAction() {
			actor.hasFlag = true;
			return state;
		}

		@Override
		public LinkedList<? extends BDSM_PlanAction> genAllActions(BDSM_PlanState state) {
			LinkedList<CaptureFlagAction> ret = new LinkedList<>();
			for(StateShip sShip: state.ships) {
				ret.add(new CaptureFlagAction(state, sShip.id));
			}
			return ret;
		}
		
	}
	
	public static BDSM_PlanAction lookupAction(BDSM_PlanActions actionPlan, BDSM_PlanState state, UUID actor) {
		switch(actionPlan) {
		case GetEnergy:
			return new GetEnergyAction(state, actor);
		case CaptureFlag:
			return new CaptureFlagAction(state, actor);
		case DeliverFlag:
			break;
		case DoNothing:
			return new DoNothingAction(state, actor);
		case DumpResources:
			break;
		case GetResoruces:
			break;
		case Guard:
			break;
		case ReturnFlag:
			break;
		default:
			System.out.println("<PlanActions.lookupaction> - Invalid enum element given.");
			break;
		}

		return new DoNothingAction(state, actor);
	}

	public static BDSM_PlanAction[] genAllPossibleActions(BDSM_PlanState state, UUID actorId) {
		LinkedList<BDSM_PlanAction> actionList = new LinkedList<>();
		for(BDSM_PlanActions actionPlan: allActions) {
			for(BDSM_PlanAction possibleAction: lookupAction(actionPlan, state, actorId).genAllActions(state)) {
				if(possibleAction.preCondition()) {
					actionList.add(possibleAction);
				}
			}
		}
		return actionList.toArray(new BDSM_PlanAction[actionList.size()]);
	}

}

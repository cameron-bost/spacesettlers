package bost7517;

import java.util.Arrays;

import bost7517.BDSM_P4_Planner.BDSM_P4_State;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Defines the action space for the planner.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public enum BDSM_P4_Actions {
	GetEnergy, ReturnFlag,				// Universal actions
	DeliverFlag, CaptureFlag, Guard,	// Flag collector actions
	DumpResources, GetResoruces,		// Resource collector actions
	DoNothing;
	
	static BDSM_P4_Actions[] allActions = new BDSM_P4_Actions[] {GetEnergy, ReturnFlag, DeliverFlag, CaptureFlag, Guard, DumpResources, GetResoruces};
	
	/**
	 * Retrieve all possible actions as array
	 * @return array of all possible actions
	 */
	public static BDSM_P4_Actions[] getAllActions() {
		return Arrays.copyOf(allActions, allActions.length);
	}
	
	/**
	 * Action for retrieving energy.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 *
	 */
	static class GetEnergyAction extends BDSM_P4_Action{

		GetEnergyAction(BDSM_P4_State _state) {
			super(_state);
			// TODO Auto-generated constructor stub
		}

		@Override
		protected boolean _preCondition(BDSM_P4_State state) {
			return false;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public BDSM_P4_Actions getAction() {
			return GetEnergy;
		}
		
	}
	
	/**
	 * Action for doing nothing.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 */
	static class BDSM_DoNothingAction extends BDSM_P4_Action{

		BDSM_DoNothingAction(BDSM_P4_State _state) {
			super(_state);
		}

		@Override
		protected boolean _preCondition(BDSM_P4_State state) {
			return true;
		}

		@Override
		public boolean postCondition(Toroidal2DPhysics space) {
			return false;
		}

		@Override
		public BDSM_P4_Actions getAction() {
			return DoNothing;
		}
		
	}

}

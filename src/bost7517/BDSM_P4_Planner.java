package bost7517;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Base;
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
	
	public BDSM_P4_Planner(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship s, BDSM_P4_ShipRole role) {
		state = new BDSM_P4_State(space, actionableObjects, s, role);
	}
	
	/**
	 * Performs BFS forward search to navigate from initial state to goal state.
	 * Once a path has been found, each ship is given their next action via the return value.
	 * 
	 * @param space physics model
	 * @param s acting ship
	 * @return Best action for this ship
	 */
	public BDSM_P4_Actions getNextAction(Toroidal2DPhysics space, Ship s) {
		// TODO run BFS to find path to goal. once path is found, return first action for this ship
		UUID shipId = s.getId();
		return null;
	}
	
	static class BDSM_P4_PlanPath{
		LinkedList<BDSM_P4_Action> actions;
		BDSM_P4_State state;
		HashMap<UUID, BDSM_P4_Actions> actionMap;
		
		BDSM_P4_PlanPath(BDSM_P4_State initialState){
			actions = new LinkedList<>();
			actionMap = new HashMap<>();
			state = initialState;
		}
		
		void addAction(BDSM_P4_Action action) {
			actions.add(action);
		}

		static BDSM_P4_PlanPath makeCopyOf(BDSM_P4_PlanPath path){
			BDSM_P4_PlanPath ret = new BDSM_P4_PlanPath(path.state);
			for(BDSM_P4_Action action: path.actions) {
				ret.addAction(action);
			}
			return ret;
		}
	}
	
	static class BDSM_P4_State {
		final int TOTAL_SHIPS = 4, TOTAL_BASES = 4, INITIAL_SHIPS = 3, INITIAL_BASES = 1;
		StateShip[] ships = new StateShip[4];
		StateBase[] bases = new StateBase[4];
		/**Base locations, in intended purchasing order*/
		final StateLocation[] baseLocations = new StateLocation[] {StateLocation.Home, StateLocation.RedAlcove1, StateLocation.RedAlcove2, StateLocation.RedCenter};
		
		int purchaseCount;
		
		int numShips, numBases;
		
		public BDSM_P4_State(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship s,
				BDSM_P4_ShipRole role) {
			initState(space, actionableObjects, s, role);
		}

		/**
		 * Generates internal state representation from real game data.
		 * 
		 * @param space physics model
		 * @param actionableObjects all available objects
		 * @param roleMap maps ships to their roles
		 */
		private void initState(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship ship, BDSM_P4_ShipRole role) {
			// Init ships
			LinkedList<Ship> realShips = new LinkedList<>();
			LinkedList<Base> realBases = new LinkedList<>();
			for(AbstractActionableObject obj: actionableObjects) {
				if(obj instanceof Ship) {
					realShips.add((Ship) obj);
				}
				if(obj instanceof Base) {
					realBases.add((Base) obj);
				}
			}
			numShips = realShips.size();
			numBases = realBases.size();
			
			
			int i = 0;
			for(Ship s: realShips) {
				ships[i++] = new StateShip(s, space, role);
			}
			for(; i<ships.length; i++) {
				ships[i] = new StateShip();
			}
			
			// Init base
			i = 0;
			for(; i < numBases; i++) {
				bases[i] = new StateBase(space, realBases.get(i));
			}
			for(; i < bases.length; i++) {
				bases[i] = new StateBase();
			}
		}
		
		/**
		 * Checks if an object is alive (i.e. in-game, visible)
		 * 
		 * @param obj object being checked
		 * @return whether the object is alive
		 */
		boolean isAlive(StateObject obj) {
			return obj.isAlive;
		}
		
		/**
		 * Checks if two objects are near each other (i.e. at the same location).
		 * 
		 * @param obj one object
		 * @param obj2 the other object
		 * @return whether the two objects are at the same location.
		 */
		boolean isNear(StateObject obj, StateObject obj2) {
			return obj.location == obj2.location;
		}
		
		/**
		 * Checks if an object is at a location.
		 * 
		 * @param obj object
		 * @param loc location
		 * @return whether an object is at a location
		 */
		boolean at(StateObject obj, StateLocation loc) {
			return obj.location == loc;
		}
		
		/**
		 * Checks if a base is being guarded by a ship at a location.
		 * 
		 * @param b base being checked
		 * @param s ship being checked
		 * @param loc location being guarded
		 * @return whether the base is being guarded by the ship at the location
		 */
		boolean isGuarded(StateBase b, StateShip s, StateLocation loc) {
			return isAlive(b) && isAlive(s) && isNear(b, s) && at(b, loc);
		}
		
		/**
		 * Checks if a ship is currently a guard
		 * @param s ship being checked
		 * @return whether ship is a guard
		 */
		boolean isGuard(StateShip s) {
			return s.isGuard;
		}
		
		/**
		 * Goal condition: 3 bases have been purchased near opponents' alcoves,
		 * and are currently being guarded.
		 * @return whether goal condition is met
		 */
		boolean isGoalState() {
			boolean isGoal = false;
			for(int i = 1; i<=4; i++) {
				isGoal &= (isGuarded(bases[i], ships[i], baseLocations[i]));
			}
			return isGoal;
		}
		
		class StateObject{
			boolean isAlive = false;
			StateLocation location;
			
			StateObject(boolean _isAlive, StateLocation _location){
				isAlive = _isAlive;
				location = _location;
			}
		}
		
		class StateShip extends StateObject{
			boolean lowEnergy;
			boolean isGuard;
			boolean isLoaded;
			boolean isCollector;
			
			public StateShip(Ship s, Toroidal2DPhysics space, BDSM_P4_ShipRole role) {
				super(s.isAlive(), StateLocation.approximateLocation(space, s));
				lowEnergy = s.getEnergy() <= AgentUtils.LOW_ENERGY_THRESHOLD;
				isGuard = role == BDSM_P4_ShipRole.FlagBoy;
				isCollector = !isGuard;
				isLoaded = s.getResources().getTotal() >= AgentUtils.RESOURCE_THRESHOLD;
			}
			
			public StateShip() {
				super(false, StateLocation.NA);
				lowEnergy = false;
				isGuard = false;
				isLoaded = false;
				isCollector = false;
			}
		}
		
		class StateBase extends StateObject{
			boolean isHome;
			
			StateBase(Toroidal2DPhysics space, Base b){
				super(b.isAlive(), StateLocation.approximateLocation(space, b));
				isHome = this.location == StateLocation.Home;
			}
			
			StateBase() {
				super(false, StateLocation.NA);
				isHome = false;
			}
		}
		
		class StateFlag extends StateObject{
			boolean isHeld;
			
			StateFlag(boolean _isAlive, StateLocation _location, boolean _isHeld) {
				super(_isAlive, _location);
				isHeld = _isHeld;
			}
		}
		
		enum StateLocation{
			Home, RedAlcove1, RedAlcove2, RedCenter, NA;

			public static StateLocation approximateLocation(Toroidal2DPhysics space, AbstractActionableObject s) {
				return 	!s.isAlive() ? NA 
						: s.getPosition().getX() <= space.getWidth() / 2 ? Home 
						: s.getPosition().getY() <= space.getHeight() / 3 ? RedAlcove1
						: s.getPosition().getY() <= 2*space.getHeight()/3 ? RedCenter 
						: RedAlcove2;
			}
		}
	}
}

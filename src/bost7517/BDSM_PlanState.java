package bost7517;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
/**
 * Simplified state representation containing all relations.
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public class BDSM_PlanState {
	final int TOTAL_SHIPS = 4, INITIAL_SHIPS = 3, TOTAL_BASES = 4, INITIAL_BASES = 1;
	StateShip[] ships;
	StateBase[] bases;
	/**Base locations, in intended purchasing order*/
	final StateLocation[] baseLocations = new StateLocation[] {StateLocation.Home, StateLocation.RedAlcove1, StateLocation.RedAlcove2, StateLocation.RedCenter};
	HashMap<UUID, StateShip> idStateShipMap = new HashMap<>();
	HashMap<UUID, Ship> idGameShipMap = new HashMap<>();
	
	int purchaseCount;
	
	int numShips, numBases;
	
	public BDSM_PlanState(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship s,
			BDSM_ShipRole role) {
		initState(space, actionableObjects, s, role);
	}

	/**
	 * Generates internal state representation from real game data.
	 * 
	 * @param space physics model
	 * @param actionableObjects all available objects
	 * @param roleMap maps ships to their roles
	 */
	private void initState(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship ship, BDSM_ShipRole role) {
		// Init ships
		LinkedList<Ship> realShips = new LinkedList<>();
		LinkedList<Base> realBases = new LinkedList<>();
		for(AbstractActionableObject obj: actionableObjects) {
			if(obj instanceof Ship) {
				realShips.add((Ship) obj);
				idGameShipMap.put(obj.getId(), (Ship) obj);
			}
			if(obj instanceof Base) {
				realBases.add((Base) obj);
			}
		}
		numShips = realShips.size();
		numBases = realBases.size();
		
		
		int i = 0;
		for(Ship s: realShips) {
			ships[i] = new StateShip(s, space, role);
			idStateShipMap.put(s.getId(), ships[i++]);
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
	 * Lookup in-state ship representation
	 * 
	 * @param id UUID of state ship object
	 * @return State ship object with given UUID
	 */
	StateShip getStateShipById(UUID id) {
		return idStateShipMap.get(id);
	}
	
	/**
	 * Get in-game ship object
	 * 
	 * @param id UUID of ship object
	 * @return Ship with specified UUID
	 */
	Ship getGameShipById(UUID id) {
		return idGameShipMap.get(id);
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
	
	/**
	 * State representation of any actionable object.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 * @version 0.4
	 */
	class StateObject{
		boolean isAlive = false;
		StateLocation location;
		
		StateObject(boolean _isAlive, StateLocation _location){
			isAlive = _isAlive;
			location = _location;
		}
	}
	
	/**
	 * State representation of ship
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 * @version 0.4
	 */
	class StateShip extends StateObject{
		boolean lowEnergy;
		boolean isGuard;
		boolean isLoaded;
		boolean isCollector;
		BDSM_PlanActions nextAction;
		UUID id;
		
		public StateShip(Ship s, Toroidal2DPhysics space, BDSM_ShipRole role) {
			super(s.isAlive(), StateLocation.approximateLocation(space, s));
			lowEnergy = s.getEnergy() <= AgentUtils.LOW_ENERGY_THRESHOLD;
			isGuard = role == BDSM_ShipRole.FlagBoy;
			isCollector = !isGuard;
			isLoaded = s.getResources().getTotal() >= AgentUtils.RESOURCE_THRESHOLD;
			nextAction = null;
			id = s.getId();
		}
		
		public StateShip() {
			super(false, StateLocation.NA);
			lowEnergy = false;
			isGuard = false;
			isLoaded = false;
			isCollector = false;
			nextAction = null;
			id = null;
		}
	}
	
	/**
	 * In-state representation of a base
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 * @version 0.4
	 */
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
	
	/**
	 * In-state representation of a flag.
	 * 
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 * @version 0.4
	 */
	class StateFlag extends StateObject{
		boolean isHeld;
		
		StateFlag(boolean _isAlive, StateLocation _location, boolean _isHeld) {
			super(_isAlive, _location);
			isHeld = _isHeld;
		}
	}
	
	/**
	 * All locations on the map.
	 *  
	 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
	 * @version 0.4
	 */
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
	
	private BDSM_PlanState() {
		
	}

	/**
	 * Creates a copy of the specified state object.
	 * 
	 * @param state state to copy
	 * @return copy of state
	 */
	public static BDSM_PlanState makeCopyOf(BDSM_PlanState state) {
		BDSM_PlanState clone = new BDSM_PlanState();
		clone.ships = Arrays.copyOf(state.ships, state.ships.length);
		clone.bases = Arrays.copyOf(state.bases, state.bases.length);
		clone.idStateShipMap = new HashMap<>();
		for(UUID id: state.idStateShipMap.keySet()) {
			clone.idStateShipMap.put(id, state.idStateShipMap.get(id));
		}
		return clone;
	}
}

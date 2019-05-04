package bost7517;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * A high-level behavior asteroid collector. This collector uses planning to determine the next action.
 * once it finds its action it will execute that action using a star to try to reach the destination.
 * the ultimate goal of this asteroid collector is to show planning and multi agent coordination.
 * 
 * Multi-agent is accomplished by once a ship becomes a guard ship it will only try to collect the flag.
 * it will determine which of the ships is closer to the flag leaving one in the guard position and the other one to collect
 * the flag,also if there is an extra ship it will become a resource collector.
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public class BDSMFlagCollector extends TeamClient {
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	HashMap <UUID, Boolean> goingForCore;
	HashMap	<UUID,AStarPath> aStarCurrentPath;
	HashMap	<UUID,LinkedList<Position>> aStarPointsToVisit;
	HashMap	<UUID,LinkedList<AStarPath>> aStarCurrentSearchTree;
	HashMap	<UUID,Position> guardingPositions;
	private int timeSincePlan = 10;
	private boolean checkBaseLocation = true;
	private LinkedList<Position> positionList;
	public boolean noflagGuard1Base = true;
	private boolean debug = true;
	
	/**
	 * Assigns ships to asteroids and beacons, as described above
	 */
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		
		//Need to check our starting base location to determine what side of the map we are starting on.
		if(checkBaseLocation)
		{
			Set<Base> b = space.getBases();
			for(Base base: b)
			{				
				if(base.getTeamName().equalsIgnoreCase(getTeamName()) && base.getPosition().getX() < 800 )
				{
					positionList = new LinkedList<Position>();
					positionList.add(new Position(1450,300));
					positionList.add(new Position(1200,900));
					positionList.add(new Position(1450,500));
				}
				if(base.getTeamName().equalsIgnoreCase(getTeamName()) && base.getPosition().getX() > 800 )
				{
					positionList = new LinkedList<Position>();
					positionList.add(new Position(250,800));
					positionList.add(new Position(400,500));
					positionList.add(new Position(250,100));
					
				}
			}
			checkBaseLocation = false;
		}
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		
		// Check for replan
		boolean replan = true;
		LinkedList<Ship> ships = new LinkedList<>();
		LinkedList<Base> bases = new LinkedList<>();
		for(AbstractActionableObject obj: actionableObjects) {
			if(obj instanceof Ship) {
				ships.add((Ship) obj);
				if(((Ship) obj).getCurrentAction() == null) {
					replan = true;
				}
			}
			else if(obj instanceof Base) {
				bases.add((Base) obj);
			}
		}
		
		// If replan needed, call planner for all objects
		if(replan) {
			for(Ship s: ships) {
				
				// Plan
				BDSM_MultiAgentPlanner planner = new BDSM_MultiAgentPlanner(space, actionableObjects, s, BDSM_ShipRole.ResourceBoy);
				BDSM_PlanActions highLevelAction = planner.getNextAction();
				
				if(debug)//debug current action
					System.out.println(planner.getNextAction());
				
				// Parse _Actions member
				switch(highLevelAction) {
				case GetEnergy:
					actions.put(s.getId(), getGetEnergyAction(space, s));
					break;
				
				case ReturnFlag:
					actions.put(s.getId(), ReturnFlag(space, s));
					break;
					
				case CaptureFlag:
					actions.put(s.getId(), CaptureFlag(space, s));
					break;
					
				case DumpResources:
					actions.put(s.getId(), DumpResources(space, s));
					break;
				
				case GetResources:
					actions.put(s.getId(), GetResoruces(space, s));
					break;
					
				case DoNothing:
					actions.put(s.getId(), DoNothing(space, s));
					break;
					
				case Guard:
					actions.put(s.getId(), Guard(space, s));
					break;
					
				default:
					System.out.println("<ERROR> - Did not recieve an action from the planner");
					break;
				}
			}
		}
		
		return actions;
	}
	
	//Do Guarding case.
	private AbstractAction Guard(Toroidal2DPhysics space, Ship s) 
	{
		AbstractAction	action = null;
		//If ship is not guarding and needs to be give it the next location in the list and remove it from the list
		if(guardingPositions.get(s.getId()) == null)
		{
			Position temp = positionList.poll();
			guardingPositions.put(s.getId(),temp);
			positionList.offerLast(temp);
		}
		
		//Need to check if we are near base
		double distanceFromSpot = space.findShortestDistance(s.getPosition(),guardingPositions.get(s.getId()));
		
		//distance is greater than 100 return back to base.
		//Not a* due to the fact it only deals with objects
		if(distanceFromSpot > 70)
		{
			action = new BDSMMoveAction(space, s.getPosition(), guardingPositions.get(s.getId()));
		}
		else
		{
			action = new DoNothingAction();
		}
		
		return action;
	}
	
	//Do nothing case.
	private AbstractAction DoNothing(Toroidal2DPhysics space, Ship s) 
	{
		AbstractAction	newAction = new DoNothingAction();
		return newAction;
	}
	
	//Get resources case.
	private AbstractAction GetResoruces(Toroidal2DPhysics space, Ship s) 
	{
		Asteroid asteroid = pickHighestValueNearestFreeAsteroid(space, s);
		AbstractAction action = null;
		if (asteroid != null) {
			asteroidToShipMap.put(asteroid.getId(), s);
	
			//A* Attempt.
			if(timeSincePlan >= 20 ) 
			{
				s.setCurrentAction(null); //resets current step to null so it is able to update step
				//reset time since plan;
				timeSincePlan = 0;
				
				//Clean up hash maps for new data.
				if(aStarCurrentPath.get(s.getId()) != null)
					aStarCurrentPath.remove(s.getId());
				if(aStarCurrentSearchTree.get(s.getId()) != null)
					aStarCurrentSearchTree.remove(s.getId());
				if(aStarPointsToVisit.get(s.getId()) != null)
					aStarPointsToVisit.remove(s.getId());
				
				//Current path which is assoicated with with a ship
				aStarCurrentPath.put(s.getId(), AStarGraph.getPathTo(s,  asteroid, space)); //Will get the current path that a* has chosen
				//Current tree thats associated with a ship
				aStarCurrentSearchTree.put(s.getId(), AStarGraph.getSearchTree());
				//Will have a list of linked list position associated with a ship ID
				aStarPointsToVisit.put(s.getId(), aStarCurrentPath.get(s.getId()).getPositions());
			}
			//increase time since plan.
			else
				timeSincePlan++;
			
			if (aStarPointsToVisit.get(s.getId()) != null)
			{
				if(!aStarPointsToVisit.get(s.getId()).isEmpty())
				{	
					Position newPosition = new Position(aStarPointsToVisit.get(s.getId()).getFirst().getX(),aStarPointsToVisit.get(s.getId()).getFirst().getY());
					action = new BDSMMoveAction(space, s.getPosition(), newPosition);
					aStarPointsToVisit.get(s.getId()).poll();//pops the top
				}
				
			}
			else
				action = new BDSMMoveToObjectAction(space, s.getPosition(), asteroid, asteroid.getPosition().getTranslationalVelocity());
		}
		
		return action;
	}

	//Take resources to base case.
	private AbstractAction DumpResources(Toroidal2DPhysics space, Ship s) {
		Base base = findNearestBase(space, s);
		AbstractAction action = null;
		
		//A* Attempt.
		if(timeSincePlan >= 20) 
		{
			s.setCurrentAction(null); //resets current step to null so it is able to update step
			//reset time since plan;
			timeSincePlan = 0;
			
			//Clean up hash maps for new data.
			if(aStarCurrentPath.get(s.getId()) != null)
				aStarCurrentPath.remove(s.getId());
			if(aStarCurrentSearchTree.get(s.getId()) != null)
				aStarCurrentSearchTree.remove(s.getId());
			if(aStarPointsToVisit.get(s.getId()) != null)
				aStarPointsToVisit.remove(s.getId());
			
			//Current path which is assoicated with with a ship
			aStarCurrentPath.put(s.getId(), AStarGraph.getPathTo(s,  base, space)); //Will get the current path that a* has chosen
			//Current tree thats associated with a ship
			aStarCurrentSearchTree.put(s.getId(), AStarGraph.getSearchTree());
			//Will have a list of linked list position associated with a ship ID
			aStarPointsToVisit.put(s.getId(), aStarCurrentPath.get(s.getId()).getPositions());
		}
		//increase time since plan.
		else
			timeSincePlan++;
		
		if (aStarPointsToVisit.get(s.getId()) != null)
		{
			if(!aStarPointsToVisit.get(s.getId()).isEmpty())
			{	
				Position newPosition = new Position(aStarPointsToVisit.get(s.getId()).getFirst().getX(),aStarPointsToVisit.get(s.getId()).getFirst().getY());
				action = new BDSMMoveAction(space, s.getPosition(), newPosition);
				aStarPointsToVisit.get(s.getId()).poll();//pops the top
			}
			else
			{
				aStarPointsToVisit.get(s.getId()).poll();
			}
		}
		else
			action = new MoveToObjectAction(space, s.getPosition(), base);
		
		aimingForBase.put(s.getId(), true);
		return action;
	}
	
	//capture the enemy flag.
	private AbstractAction CaptureFlag(Toroidal2DPhysics space, Ship s) {
		Flag enemyFlag = getEnemyFlag(space);
		AbstractAction action = null;
		
		//A* Attempt.
		if(timeSincePlan >= 20) 
		{
			s.setCurrentAction(null); //resets current step to null so it is able to update step
			//reset time since plan;
			timeSincePlan = 0;
			
			//Clean up hash maps for new data.
			if(aStarCurrentPath.get(s.getId()) != null)
				aStarCurrentPath.remove(s.getId());
			if(aStarCurrentSearchTree.get(s.getId()) != null)
				aStarCurrentSearchTree.remove(s.getId());
			if(aStarPointsToVisit.get(s.getId()) != null)
				aStarPointsToVisit.remove(s.getId());
			
			//Current path which is assoicated with with a ship
			aStarCurrentPath.put(s.getId(), AStarGraph.getPathTo(s,  enemyFlag, space)); //Will get the current path that a* has chosen
			//Current tree thats associated with a ship
			aStarCurrentSearchTree.put(s.getId(), AStarGraph.getSearchTree());
			//Will have a list of linked list position associated with a ship ID
			aStarPointsToVisit.put(s.getId(), aStarCurrentPath.get(s.getId()).getPositions());
		}
		//increase time since plan.
		else
			timeSincePlan++;
		
		if (aStarPointsToVisit.get(s.getId()) != null)
		{
			if(!aStarPointsToVisit.get(s.getId()).isEmpty())
			{	
				Position newPosition = new Position(aStarPointsToVisit.get(s.getId()).getFirst().getX(),aStarPointsToVisit.get(s.getId()).getFirst().getY());
				action = new BDSMMoveAction(space, s.getPosition(), newPosition);
				aStarPointsToVisit.get(s.getId()).poll();//pops the top
			}
			else
			{
				aStarPointsToVisit.get(s.getId()).poll();
			}
		}
		else
			action = new BDSMMoveAction(space, s.getPosition(), enemyFlag.getPosition());
		
		return action;
	}

	//Return the flag to our base case.
	private AbstractAction ReturnFlag(Toroidal2DPhysics space, Ship s) 
	{
		Base base = findNearestBase(space, s);
		AbstractAction action = null;
		
		//A* Attempt.
		if(timeSincePlan >= 20) 
		{
			s.setCurrentAction(null); //resets current step to null so it is able to update step
			//reset time since plan;
			timeSincePlan = 0;
			
			//Clean up hash maps for new data.
			if(aStarCurrentPath.get(s.getId()) != null)
				aStarCurrentPath.remove(s.getId());
			if(aStarCurrentSearchTree.get(s.getId()) != null)
				aStarCurrentSearchTree.remove(s.getId());
			if(aStarPointsToVisit.get(s.getId()) != null)
				aStarPointsToVisit.remove(s.getId());
			
			//Current path which is assoicated with with a ship
			aStarCurrentPath.put(s.getId(), AStarGraph.getPathTo(s,  base, space)); //Will get the current path that a* has chosen
			//Current tree thats associated with a ship
			aStarCurrentSearchTree.put(s.getId(), AStarGraph.getSearchTree());
			//Will have a list of linked list position associated with a ship ID
			aStarPointsToVisit.put(s.getId(), aStarCurrentPath.get(s.getId()).getPositions());
		}
		//increase time since plan.
		else
			timeSincePlan++;
		
		if (aStarPointsToVisit.get(s.getId()) != null)
		{
			if(!aStarPointsToVisit.get(s.getId()).isEmpty())
			{	
				Position newPosition = new Position(aStarPointsToVisit.get(s.getId()).getFirst().getX(),aStarPointsToVisit.get(s.getId()).getFirst().getY());
				action = new BDSMMoveAction(space, s.getPosition(), newPosition);
				aStarPointsToVisit.get(s.getId()).poll();//pops the top
			}
			else
			{
				aStarPointsToVisit.get(s.getId()).poll();
			}
		}
		else
			action = new MoveToObjectAction(space, s.getPosition(), base);

		aimingForBase.put(s.getId(), true);
		return action;
	}

	//get energy action case.
	private AbstractAction getGetEnergyAction(Toroidal2DPhysics space, Ship s) 
	{	
		Beacon beacon = pickNearestBeacon(space, s);
		AbstractAction action = null;
		
		//A* Attempt.
		if(timeSincePlan >= 20) 
		{
			s.setCurrentAction(null); //resets current step to null so it is able to update step
			//reset time since plan;
			timeSincePlan = 0;
			
			//Clean up hash maps for new data.
			if(aStarCurrentPath.get(s.getId()) != null)
				aStarCurrentPath.remove(s.getId());
			if(aStarCurrentSearchTree.get(s.getId()) != null)
				aStarCurrentSearchTree.remove(s.getId());
			if(aStarPointsToVisit.get(s.getId()) != null)
				aStarPointsToVisit.remove(s.getId());
			
			//Current path which is assoicated with with a ship
			aStarCurrentPath.put(s.getId(), AStarGraph.getPathTo(s,  beacon, space)); //Will get the current path that a* has chosen
			//Current tree thats associated with a ship
			aStarCurrentSearchTree.put(s.getId(), AStarGraph.getSearchTree());
			//Will have a list of linked list position associated with a ship ID
			aStarPointsToVisit.put(s.getId(), aStarCurrentPath.get(s.getId()).getPositions());
		}
		//increase time since plan.
		else
			timeSincePlan++;
		
		if (aStarPointsToVisit.get(s.getId()) != null)
		{
			if(!aStarPointsToVisit.get(s.getId()).isEmpty())
			{	
				Position newPosition = new Position(aStarPointsToVisit.get(s.getId()).getFirst().getX(),aStarPointsToVisit.get(s.getId()).getFirst().getY());
				action = new BDSMMoveAction(space, s.getPosition(), newPosition);
				aStarPointsToVisit.get(s.getId()).poll();//pops the top
			}
			else
			{
				aStarPointsToVisit.get(s.getId()).poll();
			}
		}
		else
			action = new BDSMMoveToObjectAction(space, s.getPosition(), beacon);
		
		return action;
	}

	/**
	 * Finds and returns the enemy flag
	 * @param space
	 * @return
	 */
	private Flag getEnemyFlag(Toroidal2DPhysics space) {
		Flag enemyFlag = null;
		for (Flag flag : space.getFlags()) {
			if (flag.getTeamName().equalsIgnoreCase(getTeamName())) {
				continue;
			} else {
				enemyFlag = flag;
			}
		}
		return enemyFlag;
	}

	/**
	 * Find the base for this team nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	private Base findNearestBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		Base nearestBase = null;

		for (Base base : space.getBases()) {
			if (base.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestBase = base;
				}
			}
		}
		return nearestBase;
	}

	/**
	 * Returns the asteroid of highest value that isn't already being chased by this team
	 * 
	 * @return
	 */
	private Asteroid pickHighestValueNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
		Set<Asteroid> asteroids = space.getAsteroids();
		int bestMoney = Integer.MIN_VALUE;
		Asteroid bestAsteroid = null;
		double minDistance = Double.MAX_VALUE;

		for (Asteroid asteroid : asteroids) {
			if (!asteroidToShipMap.containsKey(asteroid.getId())) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > bestMoney) {
					double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
					if (dist < minDistance) {
						bestMoney = asteroid.getResources().getTotal();
						//System.out.println("Considering asteroid " + asteroid.getId() + " as a best one");
						bestAsteroid = asteroid;
						minDistance = dist;
					}
				}
			}
		}
		//System.out.println("Best asteroid has " + bestMoney);
		return bestAsteroid;
	}


	/**
	 * Find the nearest beacon to this ship
	 * @param space
	 * @param ship
	 * @return
	 */
	private Beacon pickNearestBeacon(Toroidal2DPhysics space, Ship ship) {
		// get the current beacons
		Set<Beacon> beacons = space.getBeacons();

		Beacon closestBeacon = null;
		double bestDistance = Double.POSITIVE_INFINITY;

		for (Beacon beacon : beacons) {
			double dist = space.findShortestDistance(ship.getPosition(), beacon.getPosition());
			if (dist < bestDistance) {
				bestDistance = dist;
				closestBeacon = beacon;
			}
		}

		return closestBeacon;
	}



	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		ArrayList<Asteroid> finishedAsteroids = new ArrayList<Asteroid>();

		for (UUID asteroidId : asteroidToShipMap.keySet()) {
			Asteroid asteroid = (Asteroid) space.getObjectById(asteroidId);
			if (asteroid != null && (!asteroid.isAlive() || asteroid.isMoveable())) {
				finishedAsteroids.add(asteroid);
				//System.out.println("Removing asteroid from map");
			}
		}

		for (Asteroid asteroid : finishedAsteroids) {
			asteroidToShipMap.remove(asteroid.getId());
		}

		// check to see who bounced off bases
		for (UUID shipId : aimingForBase.keySet()) {
			if (aimingForBase.get(shipId)) {
				Ship ship = (Ship) space.getObjectById(shipId);
				if (ship.getResources().getTotal() == 0 && ship.getNumFlags() == 0 && ship.getNumCores() == 0) {
					// we hit the base (or died, either way, we are not aiming for base now)
					//System.out.println("Hit the base and dropped off resources");
					aimingForBase.put(shipId, false);
					justHitBase.put(shipId, true);
					goingForCore.put(ship.getId(), false);
				}
			}
		}

	}

	/**
	 * Demonstrates one way to read in knowledge from a file
	 */
	@Override
	public void initialize(Toroidal2DPhysics space) {
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
		goingForCore = new HashMap<UUID, Boolean>();
		aStarCurrentPath = new HashMap<UUID,AStarPath>();
		aStarPointsToVisit = new HashMap<UUID,LinkedList<Position>>();
		aStarCurrentSearchTree = new HashMap	<UUID,LinkedList<AStarPath>>();
		guardingPositions = new HashMap	<UUID,Position>();
	}

	/**
	 * Demonstrates saving out to the xstream file
	 * You can save out other ways too.  This is a human-readable way to examine
	 * the knowledge you have learned.
	 */
	@Override
	public void shutDown(Toroidal2DPhysics space) {
	}

	@Override
	public Set<SpacewarGraphics> getGraphics() {
		return null;
	}

	@Override
	/**
	 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
	 * far away from the existing bases
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
		boolean bought_base = false;
		boolean boughtDrone = false;
		boolean boughtCore = false;

		for (AbstractActionableObject actionableObject : actionableObjects) {
			if (actionableObject instanceof Ship) {
				Ship ship = (Ship) actionableObject;
				if (!boughtDrone && ship.getNumCores() > 0 &&
						purchaseCosts.canAfford(PurchaseTypes.DRONE, resourcesAvailable)) { // Or some other criteria for buying a drone, depending on what user wants
					purchases.put(ship.getId(), PurchaseTypes.DRONE); //This spawns a drone within a certain radius of your ship
					boughtDrone = true;
					//System.out.println("Bought a drone!");
				}

				if (!boughtCore && ship.getNumCores() == 0 && 
						purchaseCosts.canAfford(PurchaseTypes.CORE, resourcesAvailable)) { //Or some other criteria for buying a core
					purchases.put(ship.getId(), PurchaseTypes.CORE); //This places a core in your ship’s inventory
					//System.out.println("Bought a core!!");
					boughtCore = true;
				}
				
			}
		}
		
		// now see if we can afford a base or a ship.  We want a base but we also really want a 4th ship
		// try to balance
		if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {

			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) 
				{
					Ship ship = (Ship) actionableObject;
					
					if(guardingPositions.get(ship.getId()) != null)
					{
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						bought_base = true;
						System.out.println("BDSM is buying a base!");
						break;
					}
					
				}
			}	
		} 

		// can I buy a ship?
		if (purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable) && bought_base == false) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Base) {
					Base base = (Base) actionableObject;

					purchases.put(base.getId(), PurchaseTypes.SHIP);
					System.out.println("Pacifist Flag Collector is buying a ship!");
					break;
				}

			}

		}

		return purchases;
	}

	/**
	 * The pacifist flag collector only uses the drone if it is available and no other powerups 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, SpaceSettlersPowerupEnum> powerUps = new HashMap<UUID, SpaceSettlersPowerupEnum>();

		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				// launch the drone with the flag
				if (ship.isCarryingFlag()) {
					if (ship.isValidPowerup(SpaceSettlersPowerupEnum.DRONE)) {
						powerUps.put(ship.getId(), SpaceSettlersPowerupEnum.DRONE);
					}
				}
			}
		}
		
		return powerUps;
	}

}

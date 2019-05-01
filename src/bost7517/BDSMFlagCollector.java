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
import spacesettlers.objects.AiCore;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Drone;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * A pacifist flag collector client that handles multiple agents in the team.  The heuristic works as follows:
 * 
 *   The nearest and healthy ship is assigned to go get the flag and bring it back.
 *   The other ships are assigned to resource collection.
 *   Resources are used to buy additional ships and bases (with the idea that bases are better to have near the 
 *   enemy flag locations).
 * 
 * (similar to PassiveHeuritsicAsteroidCollector).  
 *  
 * @author amy
 */
public class BDSMFlagCollector extends TeamClient {
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	HashMap <UUID, Boolean> goingForCore;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	private LinkedList<Position> pointsToVisit;
	private AStarPath currentPath = null;
	private LinkedList<AStarPath> currentSearchTree;
	private bost7517.AStarGraph graph;
	private int timeSincePlan = 10;
	private boolean checkBaseLocation = true;
	private boolean baseIsLeft = false;
	private boolean baseIsRight = false;
	private Ship flagGuard1;
	private Ship flagGuard2;
	private Position guardPosition1 = new Position(1450,300);
	private Position guardPosition2 = new Position(1300,900);
	public boolean noflagGuard1Base = true;
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
					baseIsLeft = true;
					System.out.println("<OUR BASE> - is LEFT!!!");
				}
				if(base.getTeamName().equalsIgnoreCase(getTeamName()) && base.getPosition().getX() > 800 )
				{
					baseIsRight = true;
					System.out.println("<OUR BASE> - is Right!!!");
				}
			}
			checkBaseLocation = false;
		}
		
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		Ship flagShip;

		// get the flag carrier, if we have one
		flagShip = getFlagCarrier(space, actionableObjects);

		// we don't have a ship carrying a flag, so find the best choice (if it exists)
		if (flagShip == null) 
		{
			flagShip = findHealthiestShipNearFlag(space, actionableObjects);
		}

		// loop through each ship and assign it to either get energy (if needed for health) or
		// resources (as long as it isn't the flagShip)
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;

				AbstractAction action = null;

				if (flagShip != null && ship.equals(flagShip)) {
					if (flagShip.isCarryingFlag())
					{
						Base base = findNearestBase(space, ship);
						action = new MoveToObjectAction(space, ship.getPosition(), base);
						aimingForBase.put(ship.getId(), true);
					} 
					else 
					{
						Flag enemyFlag = getEnemyFlag(space);
						/*
						 * ASTAR
						 */
						if(timeSincePlan >= 20) {
							ship.setCurrentAction(null); //resets current step to null so it is able to update step
							timeSincePlan = 0; // resets times plan back to 0
							currentPath = AStarGraph.getPathTo(ship,  enemyFlag, space); //Will get the current path that a* has chosen
							currentSearchTree = AStarGraph.getSearchTree(); //Returns a search tree 
							pointsToVisit = new LinkedList<Position>(currentPath.getPositions()); // Will contain all the points for a*
						}
						else
						{
							timeSincePlan++;
						}
						
						// Call points to create a new action to move to that object.
						if (pointsToVisit != null)
						{
							if(!pointsToVisit.isEmpty())
							{	
								Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
								action = new BDSMMoveAction(space, ship.getPosition(), newPosition);
								pointsToVisit.poll();//pops the top
							}
						}
						action = new MoveToObjectAction(space, ship.getPosition(), enemyFlag,enemyFlag.getPosition().getTranslationalVelocity());
					}
				} 
				//Will allow if flag guard 1 is collected to.
				//1. Return flag if it has it
				//2. Collect flag with a certain distance.
				//3. go back to its post if flag is not there
				//4. Do noting if all the above is true.
				else if (flagGuard1 != null && ship.equals(flagGuard1))
				{
						Flag enemyFlag = getEnemyFlag(space);
						double flagdistance = space.findShortestDistance(flagGuard1.getPosition(),enemyFlag.getPosition());
						if (flagGuard1.isCarryingFlag())
						{
							Base base = findNearestBase(space, ship);
							action = new MoveToObjectAction(space, ship.getPosition(), base);
							aimingForBase.put(ship.getId(), true);
						} 
						if(flagdistance < 450)
						{
							System.out.println("flag guard 1 grabbing flag");
							action = new MoveToObjectAction(space, flagGuard1.getPosition(), enemyFlag,enemyFlag.getPosition().getTranslationalVelocity());
						}
						else if(flagGuard1.getPosition() != guardPosition1)
						{
							action = new BDSMMoveAction(space,flagGuard1.getPosition(),guardPosition1);
						}
						
				}
				else if (flagGuard2 != null && ship.equals(flagGuard2))
				{
						Flag enemyFlag = getEnemyFlag(space);
						double distance = space.findShortestDistance(flagGuard2.getPosition(),enemyFlag.getPosition());
						if (flagGuard2.isCarryingFlag())
						{
							Base base = findNearestBase(space, ship);
							action = new MoveToObjectAction(space, ship.getPosition(), base);
							aimingForBase.put(ship.getId(), true);
						} 
						if(distance < 75)
						{
							
							action = new MoveToObjectAction(space, ship.getPosition(), enemyFlag,enemyFlag.getPosition().getTranslationalVelocity());
						}
						else if(flagGuard2.getPosition() != guardPosition2)
						{
							action = new BDSMMoveAction(space,flagGuard1.getPosition(),guardPosition2);
						}

						else
							actions.put(actionable.getId(), new DoNothingAction());
						
				}
				else 
				{
					action = getAsteroidCollectorAction(space, ship);
				}

				// save the action for this ship
				actions.put(ship.getId(), action);
			} else if(actionable instanceof Drone) {
				Drone drone = (Drone) actionable;
				AbstractAction action;

				action = drone.getDroneAction(space); //Or make up some action of your own! This just adds the default action back to the drone.
				actions.put(drone.getId(), action);
			} else {
				// bases do nothing
				actions.put(actionable.getId(), new DoNothingAction());
			}
		} 
		return actions;
	}

	/**
	 * Get the flag carrier (if there is one).  Return null if there isn't a current flag carrier
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	private Ship getFlagCarrier(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;

				if (ship.isCarryingFlag()) {
					return ship;
				}
			}
		}
		return null;
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
	 * Finds the ship with the highest health and nearest the flag
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	private Ship findHealthiestShipNearFlag(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		double minDistance = Double.MAX_VALUE;
		double maxHealth = Double.MIN_VALUE;
		int minHealth = 2000;
		Ship bestShip = null;

		// first find the enemy flag
		Flag enemyFlag = getEnemyFlag(space);

		// now find the healthiest ship that has at least the required minimum energy 
		// if no ships meet that criteria, return null
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;

				double dist = space.findShortestDistance(ship.getPosition(), enemyFlag.getPosition());
				if (dist < minDistance && ship.getEnergy() > minHealth) {
					if (ship.getEnergy() > maxHealth) {
						minDistance = dist;
						maxHealth = ship.getEnergy();
						bestShip = ship;
					}
				}
			}
		}

		return bestShip;

	}


	/**
	 * Gets the action for the asteroid collecting ship
	 * @param space
	 * @param ship
	 * @return
	 */
	private AbstractAction getAsteroidCollectorAction(Toroidal2DPhysics space,
			Ship ship) {
		AbstractAction current = ship.getCurrentAction();
		Position currentPosition = ship.getPosition();

		// if the ship has enough resourcesAvailable, take it back to base
		if (ship.getResources().getTotal() > 1000  || ship.getNumCores() > 0) {
			Base base = findNearestBase(space, ship);
			AbstractAction newAction = new BDSMMoveToObjectAction(space, currentPosition, base);
			aimingForBase.put(ship.getId(), true);
			goingForCore.put(ship.getId(), false);
			return newAction;
		}

		// aim for a beacon if there isn't enough energy
		if (ship.getEnergy() < 3000) {
			Beacon beacon = pickNearestBeacon(space, ship);
			AbstractAction newAction = null;
			// if there is no beacon, then just skip a turn
			if (beacon == null) {
				newAction = new DoNothingAction();
			} else {
				newAction = new BDSMMoveToObjectAction(space, currentPosition, beacon);
			}
			aimingForBase.put(ship.getId(), false);
			goingForCore.put(ship.getId(), false);
			return newAction;
		}

		// if there is a nearby core, go get it
		AiCore nearbyCore = pickNearestCore(space, ship, 200);
		if (nearbyCore != null) {
			AbstractAction newAction = new BDSMMoveToObjectAction(space, currentPosition, nearbyCore, nearbyCore.getPosition().getTranslationalVelocity());
			goingForCore.put(ship.getId(), true);
			aimingForBase.put(ship.getId(), false);
			return newAction;
		}


		// did we bounce off the base?
		if (current == null || current.isMovementFinished(space) ||
				(justHitBase.containsKey(ship.getId()) && justHitBase.get(ship.getId()))) {
			aimingForBase.put(ship.getId(), false);
			justHitBase.put(ship.getId(), false);			
			goingForCore.put(ship.getId(), false);
			current = null;
		}

		// otherwise aim for the asteroid
		if (current == null || current.isMovementFinished(space)) {
			aimingForBase.put(ship.getId(), false);
			goingForCore.put(ship.getId(), false);
			Asteroid asteroid = pickHighestValueNearestFreeAsteroid(space, ship);

			AbstractAction newAction = null;

			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
				newAction = new BDSMMoveToObjectAction(space, currentPosition, asteroid, 
						asteroid.getPosition().getTranslationalVelocity());
			}

			return newAction;
		} 

		return ship.getCurrentAction();
	}


	/**
	 * Find the nearest core to this ship that falls within the specified minimum distance
	 * @param space
	 * @param ship
	 * @return
	 */
	private AiCore pickNearestCore(Toroidal2DPhysics space, Ship ship, int minimumDistance) {
		Set<AiCore> cores = space.getCores();

		AiCore closestCore = null;
		double bestDistance = minimumDistance;

		for (AiCore core : cores) {
			double dist = space.findShortestDistance(ship.getPosition(), core.getPosition());
			if (dist < bestDistance) {
				bestDistance = dist;
				closestCore = core;
			}
		}

		return closestCore;
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
		graph = AStarGraph.getInstance(space.getHeight(), space.getWidth(), false);
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
		goingForCore = new HashMap<UUID, Boolean>();
		
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
		double BASE_BUYING_DISTANCE = 400;
		boolean bought_base = false;
		int numBases, numShips;

		// count the number of ships for the base/ship buying algorithm
		numShips = 0;
		for (AbstractActionableObject actionableObject : actionableObjects) 
		{
			if (actionableObject instanceof Ship) {
				numShips++;
			}
		}

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
			Ship bestShip = null;
			double minDistance = Double.MAX_VALUE;
			boolean buyBase = true;
			
			if(flagGuard1 == null)
			{
				for (AbstractActionableObject actionableObject : actionableObjects) 
				{
					if (actionableObject instanceof Ship) 
					{
						Ship ship = (Ship) actionableObject;
						double dist =  space.findShortestDistance(ship.getPosition(), new Position(950,500));
						if(dist < minDistance)
						{
							bestShip = ship;
						}
					}
				}
				flagGuard1=bestShip;
				System.out.println("<DEBUG> - Flag guard assigned");
			}
			
			double guard1ToLocation =  space.findShortestDistance(flagGuard1.getPosition(), guardPosition1);
			
			if(flagGuard1 != null && guard1ToLocation < 150)
			{
				for (AbstractActionableObject actionableObject : actionableObjects) 
				{
					if (actionableObject instanceof Ship) 
					{
						Ship ship = (Ship) actionableObject;
						if(!ship.equals(flagGuard1))
						{
							double dist =  space.findShortestDistance(ship.getPosition(), guardPosition2);
							if(dist < minDistance)
							{
								bestShip = ship;
							}
						}
					}
				}
				flagGuard2=bestShip;
				System.out.println("<DEBUG> - Flag guard 2 assigned");
			}
			
			Set<Base> bases = space.getBases();
			boolean base1Exist = true;
			boolean base2Exist = true;
			double minDistanceX = Double.MAX_VALUE;
			for (Base base : bases) 
			{
				if (base.getTeamName().equalsIgnoreCase(getTeamName()))
				{
					double distance = space.findShortestDistance(guardPosition1, base.getPosition());
					if (distance < minDistanceX) {
						minDistanceX = distance;
					}
				}
			}
			
			if(minDistanceX > 150)
			{
				base1Exist = false;
			}
			
			for (Base base : bases) 
			{
				if (base.getTeamName().equalsIgnoreCase(getTeamName()))
				{
					double distance = space.findShortestDistance(guardPosition2, base.getPosition());
					
					if (distance < 100) {
						base2Exist = false;
					}
				}
			}

			
			if(base1Exist == false)
			{
				for (AbstractActionableObject actionableObject : actionableObjects) 
				{
					if (actionableObject instanceof Ship) 
					{
						Ship ship = (Ship) actionableObject;
						double distance = space.findShortestDistance(ship.getPosition(),guardPosition1);
						if(ship.equals(flagGuard1) && distance < 20)
						{
							purchases.put(flagGuard1.getId(), PurchaseTypes.BASE);
							bought_base = true;
							noflagGuard1Base = false;
							System.out.println("BDSM guardShip1 is buying base!");
						}
						
					}
				}
			}
			
			if(base1Exist && base2Exist == false)
				for (AbstractActionableObject actionableObject : actionableObjects) 
				{
					if (actionableObject instanceof Ship) 
					{
						Ship ship = (Ship) actionableObject;
						double distance = space.findShortestDistance(ship.getPosition(),guardPosition2);
						if(ship.equals(flagGuard2) && distance < 20)
						{
							purchases.put(flagGuard2.getId(), PurchaseTypes.BASE);
							bought_base = true;
							noflagGuard1Base = false;
							System.out.println("BDSM guardShip2 is buying base!");
						}
						
					}
				}
			
			/*
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					Set<Base> bases = space.getBases();
					// how far away is this ship to a base of my team?
					boolean buyBase = true;
					numBases = 0;
					for (Base base : bases) 
					{
						if (base.getTeamName().equalsIgnoreCase(getTeamName())) {
							numBases++;
							double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
							if (distance < BASE_BUYING_DISTANCE) {
								buyBase = false;
							}
						}
					}					
					
					if (buyBase && numBases < numShips) {
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						bought_base = true;
						System.out.println("BDSM is buying a base!");
						break;
					}
				}
			}		*/
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

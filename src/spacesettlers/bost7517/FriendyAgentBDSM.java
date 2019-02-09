package spacesettlers.bost7517;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.ExampleKnowledge;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.AiCore;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Based on the PacifistHeuristicAsteroidCollectorTeamClient written by Dr. McGovern
 * 
 * @author Cameron Bost, Joshua Atkinson
 */
public class FriendyAgentBDSM extends TeamClient {
	private boolean debug = false;
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	
	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;
	
	
	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 1500; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 1500;   // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 350; // #P1 - raised 200 -> 350 
	
	
	/**
	 * State Variables
	 */
	AbstractAction previousAction = null;

	/**
	 * Assigns ships to asteroids and beacons, as described above
	 */
	int stepCount = 0;
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		
		// DEBUG: Prints all ship positions at start of timestep processing
		if(debug) {
			System.out.println("Step: "+stepCount);
			for(Beacon s: space.getBeacons()) {
				System.out.println(s.getPosition()+"-"+s.getId()+": ("+s.getPosition().getX()+","+s.getPosition().getY()+")");
			}
		}
		
		// loop through each ship
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;

				AbstractAction action;
				action = getAsteroidCollectorAction(space, ship);
				actions.put(ship.getId(), action);
				
			} else {
				// it is a base.  Heuristically decide when to use the shield (TODO)
				actions.put(actionable.getId(), new DoNothingAction());
			}
		} 
		return actions;
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
		// update previous action
		previousAction = current;
		
		// Rule 1. If energy is low, go for nearest energy source
		if (ship.getEnergy() < LOW_ENERGY_THRESHOLD) {
			AbstractAction newAction = null;
			// Find energy source
			AbstractObject energyTarget = findNearestEnergySource(space, ship);
			if(energyTarget != null) {
				newAction = new MoveToObjectAction(space, currentPosition, energyTarget);
				if(energyTarget instanceof Base) {
					aimingForBase.put(ship.getId(), true);
				}
				else {
					aimingForBase.put(ship.getId(), false);
				}
			}
			else {
				if(debug) {
					System.out.println("Energy target returned null");
				}
			}
			return newAction;
			/*
			//Beacon beacon = pickNearestBeacon(space, ship);
			AbstractAction newAction = null;
			// Rule 1.1 if there is no beacon -> do nothing
			if (beacon == null) {
				if(debug){
					System.out.println("<Action Declaration> - Low energy, no beacons, doing nothing.");
				}
				newAction = new DoNothingAction();
			}
			// Rule 1.2 if there is a beacon -> go to beacon
			else {
				if(debug){
					System.out.println("<Action Declaration> - Getting Energy "+beacon.getPosition());
				}
				newAction = new MoveToObjectAction(space, currentPosition, beacon);
			return newAction;
			}*/
		}

		// Rule 2. If the ship has enough resources, deposit them
		if (ship.getResources().getTotal() > RESOURCE_THRESHOLD) {
			Base base = findNearestBase(space, ship);
			AbstractAction newAction = new MoveToObjectAction(space, currentPosition, base);
			aimingForBase.put(ship.getId(), true);
			if(debug){
				System.out.println("<Action Declaration> - Deposit (" + ship.getResources().getTotal()+")");
			}
			return newAction;
		}

		// Rule 3. If not doing anything, just finished, or just hit base, then aim for best asteroid
		if (current == null || current.isMovementFinished(space) || 
				(justHitBase.containsKey(ship.getId()) && justHitBase.get(ship.getId()))) {
			// Update model
			justHitBase.put(ship.getId(), false);			
			aimingForBase.put(ship.getId(), false);
			
			// Get best asteroid
			Asteroid asteroid = pickHighestValueNearestFreeAsteroid(space, ship);

			AbstractAction newAction = null;

			/*if (asteroid == null) {
				// there is no asteroid available so collect a beacon
				Beacon beacon = pickNearestBeacon(space, ship);
				// if there is no beacon, then just skip a turn
				if (beacon == null) {
					newAction = new DoNothingAction();
				} else {
					newAction = new MoveToObjectAction(space, currentPosition, beacon);
				}
			} else {
				asteroidToShipMap.put(asteroid.getId(), ship);
				newAction = new MoveToObjectAction(space, currentPosition, asteroid);
			}*/
			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
				newAction = new MoveToObjectAction(space, currentPosition, asteroid, 
						asteroid.getPosition().getTranslationalVelocity());
				if(debug){
					System.out.println("<Action Declaration> - Chasing asteroid");
					System.out.println("<Velocity Check> - "+ship.getPosition().getTranslationalVelocity());
				}
				return newAction;
			}
			if(debug){
				System.out.println("<Action Declaration> - No asteroid found to chase."); 
			}
			return newAction;
		}
		if(debug) {
			System.out.println("<Action Declaration> - Continuing action...");
		}
		return ship.getCurrentAction();
	}
	
	/**
	 * Locates nearest object containing energy. Note that energy sources are bases, beacons, and cores.
	 * 
	 * @param space physics model
	 * @param ship ship currently acting
	 * @return nearest energy source
	 */
	private AbstractObject findNearestEnergySource(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AbstractObject bestObject = null;
		
		Base minBase = findNearestBase(space, ship);
		double minBaseDist = Double.MAX_VALUE;
		if(minBase != null) {
			minBaseDist = space.findShortestDistance(ship.getPosition(), minBase.getPosition());
		}
		if(debug) {
			System.out.println("Min Base: "+ (minBase == null ? "null" : minBase.getId()+", Dist: "+minBaseDist));
		}
		
		AiCore minCore = findNearestAiCore(space, ship);
		double minCoreDist = Double.MAX_VALUE;
		if(minCore != null) {
			minCoreDist = space.findShortestDistance(ship.getPosition(), minCore.getPosition());
		}
		if(debug) {
			System.out.println("Min Core: "+ (minCore == null ? "null" : minCore.getId()+", Dist: "+minCoreDist));
		}
		
		Beacon minBeacon = findNearestBeacon(space, ship);
		double minBeaconDist = Double.MAX_VALUE;
		if(minBeacon != null) {
			minBeaconDist = space.findShortestDistance(ship.getPosition(), minBeacon.getPosition());
		}
		if(debug) {
			System.out.println("Min Beacon: "+ (minBeacon == null ? "null" : minBeacon.getId()+", Dist: "+minBeaconDist));
		}
		
		minDistance = minBaseDist;
		if(minCoreDist < minDistance) {
			minDistance = minCoreDist;
			bestObject = minCore;
		}
		if(minBeaconDist < minDistance) {
			minDistance = minBeaconDist;
			bestObject = minBeacon;
		}
		
		if(debug && bestObject != null) {
			System.out.println("Targetting energy source " + bestObject.getId());
		}
		return bestObject;
	}

	private Asteroid pickNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
        Set<Asteroid> asteroids = space.getAsteroids();
        Asteroid bestAsteroid = null;
        double minDistance = Double.MAX_VALUE;
        for (Asteroid asteroid : asteroids) {
            if (!asteroidToShipMap.containsKey(asteroid.getId())) {
                if (asteroid.isMineable()) {
                    double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
                    if (dist < minDistance) {
                        //System.out.println("Considering asteroid " + asteroid.getId() + " as a best one");
                        bestAsteroid = asteroid;
                        minDistance = dist;
                    }
                }
            }
        }
        return bestAsteroid;
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
	 * Find the AI core nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	private AiCore findNearestAiCore(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AiCore nearestCore = null;

		for (AiCore core : space.getCores()) {
			if (core.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), core.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestCore = core;
				}
			}
		}
		return nearestCore;
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
	private Beacon findNearestBeacon(Toroidal2DPhysics space, Ship ship) {
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
			if (asteroid == null || !asteroid.isAlive() || asteroid.isMoveable()) {
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
				if (ship.getResources().getTotal() == 0 ) {
					// we hit the base (or died, either way, we are not aiming for base now)
					if(debug) {
						System.out.println("<"+shipId.toString()+"> - Hit the base and dropped off resources");
					}
					aimingForBase.put(shipId, false);
					justHitBase.put(shipId, true);
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
		
		XStream xstream = new XStream();
		xstream.alias("ExampleKnowledge", ExampleKnowledge.class);

		try { 
			myKnowledge = (ExampleKnowledge) xstream.fromXML(new File(getKnowledgeFile()));
		} catch (XStreamException e) {
			// if you get an error, handle it other than a null pointer because
			// the error will happen the first time you run
			myKnowledge = new ExampleKnowledge();
		}
	}

	/**
	 * Demonstrates saving out to the xstream file
	 * You can save out other ways too.  This is a human-readable way to examine
	 * the knowledge you have learned.
	 */
	@Override
	public void shutDown(Toroidal2DPhysics space) {
		XStream xstream = new XStream();
		xstream.alias("ExampleKnowledge", ExampleKnowledge.class);

		try { 
			// if you want to compress the file, change FileOuputStream to a GZIPOutputStream
			xstream.toXML(myKnowledge, new FileOutputStream(new File(getKnowledgeFile())));
		} catch (XStreamException e) {
			// if you get an error, handle it somehow as it means your knowledge didn't save
			// the error will happen the first time you run
			myKnowledge = new ExampleKnowledge();
		} catch (FileNotFoundException e) {
			myKnowledge = new ExampleKnowledge();
		}
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
		
		// TODO: Determine if base is impossible to purchase, add heuristic to purchase anyway
		boolean bought_base = false;
		
		if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					Set<Base> bases = space.getBases();

					// how far away is this ship to a base of my team?
					boolean buyBase = true;
					for (Base base : bases) {
						if (base.getTeamName().equalsIgnoreCase(getTeamName())) {
							double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
							if (distance < BASE_BUYING_DISTANCE) {
								buyBase = false;
							}
						}
					}
					if (buyBase) {
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						bought_base = true;
						//System.out.println("Buying a base!!");
						break;
					}
				}
			}		
		} 
		
		// Ship Purchase
		if (bought_base == false && purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				// TODO: What if we chose a base to spawn our ship at for a reason, instead of the first available one?
				if (actionableObject instanceof Base) {
					Base base = (Base) actionableObject;
					purchases.put(base.getId(), PurchaseTypes.SHIP);
					break;
				}
			}
		}
		return purchases;
	}

	/**
	 * Unused in cooperative mode
	 * @param space
	 * @param actionableObjects
	 * @return Empty set
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		return new HashMap<UUID, SpaceSettlersPowerupEnum>();
	}
}

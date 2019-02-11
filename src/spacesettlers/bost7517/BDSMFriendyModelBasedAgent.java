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
import spacesettlers.actions.MoveAction;
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
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * A model-based reflex agent capable of playing spacesettlers, the main project in CS 5013 - Artificial Intelligence.
 * Included rules for this agent are:
 * If no previous path currently being followed, find new path:
 * 	If energy is low, go for energy. 
 * 	Else if ship is full of resources, go to base. 
 * 	Else choose target asteroid.
 * else follow previous path.
 * 
 * @author Cameron Bost
 * @version 0.1
 */
public class BDSMFriendyModelBasedAgent extends TeamClient {
	
	class ShipState{
		/**
		 * Basic Properties
		 */
		UUID target = NO_TARGET;
		boolean aimingForBase = false;
		boolean justHitBase = false;
		/**
		 * Collision Avoidance
		 */
		double energy = 0;
		double energyDiff = 0;
		boolean isRunning = false;
		UUID runningFrom = NO_TARGET;
		AbstractAction previousAction = null;
	}
	
	/**
	 * Dev variables
	 */
	/**Debug mode, for verbose logging*/
	private boolean debug = true;
	/**Used to count number of time-steps something has occurred.*/
	private int continueStepTracker = 0;
	
	/**
	 * Constants
	 */
	/**Energy amount at which ship should seek out energy source*/
	final double LOW_ENERGY_THRESHOLD = 1500; // #P1 - Lowered 2000 -> 1500
	/**Amount of resources at which ship will head home*/
	final double RESOURCE_THRESHOLD = 2000;   // #P1 - Raised 500 -> 2000
	/**Distance a ship should be from a base before purchasing (desired, not required)*/
	final double BASE_BUYING_DISTANCE = 350; // #P1 - raised 200 -> 350 
	/**Max acceleration*/
	final double MAX_ACCELERATION = 15.0;
	/**Placeholder constant for having no target*/
	final UUID NO_TARGET = new UUID(0, 0);
	
	/**
	 * Model Variables (from heuristic)
	 */
	/**Tracks which asteroids are being targeted*/
	private HashMap <UUID, Ship> asteroidToShipMap;

	/**
	 * Model Variables (new)
	 */
	/**Map of ships to their state objects*/
	private HashMap<UUID, ShipState> shipStateMap;

	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;
	
	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// Update observation variables
		for (AbstractObject actionable :  actionableObjects) {
			// Ship actions
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				if(!shipStateMap.containsKey(ship.getId())) {
					shipStateMap.put(ship.getId(), new ShipState());
				}
				ShipState state = shipStateMap.get(ship.getId());
				double prevEnergy = state.energy;
				double currentEnergy = ship.getEnergy();
				double diff = (prevEnergy > 0 ? ship.getEnergy()-prevEnergy : 0);
				state.energy = currentEnergy;
				state.energyDiff = diff;
			}
		}
		
		// Determine actions to be performed by ships and bases
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		for (AbstractObject actionable :  actionableObjects) {
			// Ship actions
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				actions.put(ship.getId(), getBDSMShipAction(space, ship));
			} 
			// Base actions
			else if(actionable instanceof Base){
				Base base = (Base) actionable;
				actions.put(base.getId(), getBDSMBaseAction(space, base));
			}
		}
		return actions;
	}

	/**
	 * Determines the best action for this ship.
	 * 
	 * @param space Physics model
	 * @param ship Ship being observed
	 * @return Action for this ship to take
	 */
	private AbstractAction getBDSMShipAction(Toroidal2DPhysics space, Ship ship) {
		
		if(!shipStateMap.containsKey(ship.getId())) {
			shipStateMap.put(ship.getId(), new ShipState());
		}
		ShipState state = shipStateMap.get(ship.getId());
		
		AbstractAction currentAction = ship.getCurrentAction();
		AbstractAction nextAction = null;
		
		// Rule: If energy is low, go for nearest energy source
		if (ship.getEnergy() < LOW_ENERGY_THRESHOLD) {
			return getNewObtainEnergyAction(space, ship);
		}
		
		// Rule: If the ship has enough resources, deposit them
		if (ship.getResources().getTotal() > RESOURCE_THRESHOLD) {
			if(state.aimingForBase) {
				return ship.getCurrentAction();
			}
			nextAction = getNewDepositResourcesAction(space, ship);
			state.previousAction = nextAction;
			return nextAction;
		}
		
		// Rule: If not doing anything, just finished, or just hit base, then get new target
		if (currentAction == null || currentAction.isMovementFinished(space) || state.justHitBase || !space.getObjectById(state.target).isAlive()) {
			if(debug) {
				continueStepTracker = 0;
			}
			nextAction = getNewPursueTargetAction(space, ship);
			state.previousAction = nextAction;
			return nextAction;
		}
		/**
		 * Default: If we are currently pursuing something and have not just hit the base
		 */
		else {
			if(debug && (++continueStepTracker % 100 == 0)) {
				System.out.println("<Action Continuation> Continuing action..."+continueStepTracker);
			}
			state.previousAction = currentAction;
			return currentAction;
		}
	}

	/**
	 * Obtains a new target for the ship.
	 * 
	 * @param space physics model
	 * @param ship ship to perform action
	 * @return movement action for pursuing a target
	 */
	private AbstractAction getNewPursueTargetAction(Toroidal2DPhysics space, Ship ship) {
		// Update model
		ShipState state = shipStateMap.get(ship.getId());
		state.justHitBase = false;
		state.aimingForBase = false;
		
		UUID currentTarget = state.target;
		
		// Get best asteroid
		Asteroid targetAsteroid = pickHighestValueNearestFreeAsteroid(space, ship);

		AbstractAction newAction = null;
		if (targetAsteroid != null) {
			asteroidToShipMap.put(targetAsteroid.getId(), ship);
			state.target = targetAsteroid.getId();
			newAction = new MoveToObjectAction(space, ship.getPosition(), targetAsteroid, targetAsteroid.getPosition().getTranslationalVelocity());
			if(debug && (currentTarget.equals(NO_TARGET) || !currentTarget.equals(targetAsteroid.getId()))){
				System.out.println("<Action Declaration> - Acquired new target: "+targetAsteroid.getId());
//				System.out.println("<Velocity Check> - "+ship.getPosition().getTranslationalVelocity());
			}
			return newAction;
		}
		else {			
			if(debug){
				System.out.println("<Action Declaration> - No asteroid found to chase."); 
			}
			return new DoNothingAction();
		}
	}

	/**
	 * Determines best action for depositing resources.
	 * 
	 * @param space physics model
	 * @param ship ship performing action
	 * @return Action to deposit resources (typically a movement)
	 */
	private AbstractAction getNewDepositResourcesAction(Toroidal2DPhysics space, Ship ship) {
		ShipState state = shipStateMap.get(ship.getId());
		Base base = AgentUtils.findNearestBase(space, ship);
		AbstractAction newAction = new MoveToObjectAction(space, ship.getPosition(), base);
		state.aimingForBase = true;
		if(debug){
			System.out.println("<Action Declaration> - Deposit (" + ship.getResources().getTotal()+")");
		}
		return newAction;
	}

	/**
	 * Determines the best action for obtaining energy.
	 * 
	 * @param space physics model
	 * @param ship ship performing action
	 * @return action to obtain energy
	 */
	private AbstractAction getNewObtainEnergyAction(Toroidal2DPhysics space, Ship ship) {
		ShipState state = shipStateMap.get(ship.getId());
		AbstractAction newAction = null;
		// Find energy source
		AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
		Position currentPosition = ship.getPosition();
		if(energyTarget != null && !energyTarget.getId().equals(state.target)) {
			state.target = energyTarget.getId();
			newAction = new MoveToObjectAction(space, currentPosition, energyTarget);
			if(energyTarget instanceof Base) {
				state.aimingForBase = true;
			}
			else {
				state.aimingForBase = false;
			}
			if(debug) {
				System.out.println("<Action Declaration> - Chasing energy target: "+energyTarget.getId()+" ("+energyTarget.getClass().getName());
			}
		}
		else {
			if(debug) {
				System.out.println("Energy target returned null");
			}
			newAction = new DoNothingAction();
			state.target = NO_TARGET;
		}
		state.previousAction = newAction;
		return newAction;
	}

	/**
	 * Picks asteroid by highest value, then by lowest distance
	 * @param space physics model
	 * @param ship ship performing action
	 * @return asteroid to pursue
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
						bestAsteroid = asteroid;
						minDistance = dist;
					}
				}
			}
		}
		return bestAsteroid;
	}
	
	/**
	 * Determines the action that a base should take on this time-step.
	 * 
	 * @param space physics model
	 * @param base Base
	 * @return Action for this base to take.
	 */
	private AbstractAction getBDSMBaseAction(Toroidal2DPhysics space, Base base) {
		/**For project 1, bases do nothing*/
		return new DoNothingAction();
	}

	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		ArrayList<Asteroid> finishedAsteroids = new ArrayList<Asteroid>();

		for (UUID asteroidId : asteroidToShipMap.keySet()) {
			Asteroid asteroid = (Asteroid) space.getObjectById(asteroidId);
			if (asteroid == null || !asteroid.isAlive() || asteroid.isMoveable()) {
 				finishedAsteroids.add(asteroid);
			}
		}

		for (Asteroid asteroid : finishedAsteroids) {
			Ship assailant = asteroidToShipMap.get(asteroid.getId());
			ShipState state = shipStateMap.get(assailant.getId());
			asteroidToShipMap.remove(asteroid.getId());
			// Only remove from target map if asteroid is dead
			if(asteroid != null && !asteroid.isAlive()) {
				state.target = NO_TARGET;
			}
		}
		
		/**
		 * Update ship states
		 */
		for (UUID shipId : shipStateMap.keySet()) {
			ShipState state = shipStateMap.get(shipId);
			// Check if ships have just hit the base (i.e. were aiming for it and have 0 resources).
			if (state.aimingForBase) {
				Ship ship = (Ship) space.getObjectById(shipId);
				if (ship.getResources().getTotal() == 0 ) {
					if(debug) {
						System.out.println("<"+shipId.toString()+"> - Hit the base and dropped off resources");
					}
					state.aimingForBase = false;
					state.justHitBase = true;
				}
			}
			// If target is dead, remove objective
			if (!state.target.equals(NO_TARGET) && !space.getObjectById(state.target).isAlive()) {
				state.target = NO_TARGET;
				state.previousAction = null;
			}
		}
	}

	@Override
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, ResourcePile resourcesAvailable,
			PurchaseCosts purchaseCosts) {
		// Return value, will contain at most one base purchase for one ship
		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
		
		
		// TODO: Determine if base is impossible to purchase, add heuristic to purchase anyway
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
						break;
					}
				}
			}		
		} 
		
		return purchases;
	}

	@Override
	public void initialize(Toroidal2DPhysics space) {
		// Init model variables
		asteroidToShipMap = new HashMap<UUID, Ship>();
		shipStateMap = new HashMap<UUID, ShipState>();
		
		// BTW: copied from cooperative heuristic agent
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

	@Override
	public void shutDown(Toroidal2DPhysics space) {
		// BTW: copied from Cooperative heuristic agent
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

	/**
	 * No custom graphics
	 */
	@Override
	public Set<SpacewarGraphics> getGraphics() {
		return null;
	}

	/**
	 * Power-ups are unused by the cooperative bot
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		return new HashMap<UUID, SpaceSettlersPowerupEnum>();
	}
}

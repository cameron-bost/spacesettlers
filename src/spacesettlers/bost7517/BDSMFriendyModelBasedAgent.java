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
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.SpaceSettlersSimulator;
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

	class ShipState {
		/**
		 * Basic Properties
		 */
		UUID target = NO_TARGET;
		boolean aimingForBase = false;
		boolean justHitBase = false;
		int totalResources = 0;
		/**
		 * Collision Avoidance
		 */
		double energy = 0;
		double energyDiff = 0;
		boolean isRunning = false;
		UUID runningFrom = NO_TARGET;
		boolean isDead = false;
	}

	Boolean stopNow = false;
	Boolean actionFinished = false;

	class TimeoutChecker extends Thread {
		@Override
		public void run() {
			while (keepThreadsAlive) {
				for (int i = 0; i < 99 && !actionFinished; i++) {
					try {
						Thread.sleep(SpaceSettlersSimulator.TEAM_ACTION_TIMEOUT / 100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				synchronized (stopNow) {
					stopNow = true;
				}
			}
		}
	}

	/**
	 * Dev variables
	 */
	/** Debug mode, for verbose logging */
	private boolean debug = false;
	/** Used to count number of time-steps something has occurred. */
	private int continueStepTracker = 0;

	/**
	 * Constants
	 */
	/** Energy amount at which ship should seek out energy source */
	final double LOW_ENERGY_THRESHOLD = 1500; // #P1 - Lowered 2000 -> 1500
	/** Amount of resources at which ship will head home */
	final double RESOURCE_THRESHOLD = 2000; // #P1 - Raised 500 -> 2000
	/**
	 * Distance a ship should be from a base before purchasing (desired, not
	 * required)
	 */
	final double BASE_BUYING_DISTANCE = 350; // #P1 - raised 200 -> 350
	/** Max acceleration */
	final double MAX_ACCELERATION = 15.0;
	/** Placeholder constant for having no target */
	final UUID NO_TARGET = new UUID(0, 0);
	/** Minimum acceptable velocity when approaching a mineable asteroid */
	final double MIN_ACCEPTABLE_VELOCITY = 20;

	/**
	 * Threading variables
	 */
	boolean keepThreadsAlive = true;

	/**
	 * Model Variables (from heuristic)
	 */
	/** Tracks which asteroids are being targeted */
	private HashMap<UUID, Ship> asteroidToShipMap;

	/**
	 * Model Variables (new)
	 */
	/** Map of ships to their state objects */
	private HashMap<UUID, ShipState> shipStateMap;

	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;

	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// Update observation variables
		for (AbstractObject actionable : actionableObjects) {
			// Ship actions
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				if (!shipStateMap.containsKey(ship.getId())) {
					shipStateMap.put(ship.getId(), new ShipState());
				}
				ShipState state = shipStateMap.get(ship.getId());
				double prevEnergy = state.energy;
				double currentEnergy = ship.getEnergy();
				double diff = (prevEnergy > 0 ? ship.getEnergy() - prevEnergy : 0);
				state.energy = currentEnergy;
				state.energyDiff = diff;
				state.totalResources = ship.getResources().getTotal();
			}
		}

		// Determine actions to be performed by ships and bases
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		for (AbstractObject actionable : actionableObjects) {
			// Ship actions
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				try {
					actions.put(ship.getId(), getBDSMShipAction(space, ship));
				}
				catch(Exception e) {
					System.out.println(e.getMessage());
				}
			}
			// Base actions
			else if (actionable instanceof Base) {
				Base base = (Base) actionable;
				actions.put(base.getId(), getBDSMBaseAction(space, base));
			}
		}
		return actions;
	}

	/**
	 * Determines the best action for this ship.
	 * Rules 
	 * 1. If ship is currently avoiding an obstacle, check distance and stop running 
	 * if necessary. 
	 * 2. If ship has taken damage (i.e. been struck), check for nearby obstacle and run
	 * from it. 
	 * 3. If energy is low, seek out an energy source. 
	 * 4. If resource count is higher than energy, deposit.
	 * 5. Otherwise, find the highest valued nearest asteroid to chase, comparing the 
	 * other ships' chances of getting the asteroid to its own.
	 * 
	 * @param space Physics model
	 * @param ship  Ship being observed
	 * @return Action for this ship to take
	 */
	private AbstractAction getBDSMShipAction(Toroidal2DPhysics space, Ship ship) {
		stopNow = false;
		/**
		 * Setup ShipState object (if it doesn't exist)
		 */
		if (!shipStateMap.containsKey(ship.getId())) {
			shipStateMap.put(ship.getId(), new ShipState());
		}
		ShipState state = shipStateMap.get(ship.getId());
//		printState(space, ship, state); // DEBUG USE ONLY
		AbstractAction currentAction = ship.getCurrentAction();
		AbstractAction nextAction = null;

		/**
		 * Rule: If running, but object is at least 2 * radius away, continue with
		 * previous task
		 */
		if (state.isRunning) {
			if (!state.runningFrom.equals(NO_TARGET)) {
				AbstractObject runningFromObj = space.getObjectById(state.runningFrom);
				if (runningFromObj != null && runningFromObj.isAlive()) {
					double dangerDistance = space.findShortestDistance(ship.getPosition(),
							runningFromObj.getPosition());
					if (dangerDistance >= runningFromObj.getRadius() * 2) {
						if(debug) {
							System.out.println("<Runaway Update> - Got away from the attacker.");
						}
						state.isRunning = false;
					}
				} else {
					if(debug) {
						System.out.println("<Runaway Update> - runningFrom disappeared!");
					}
					state.isRunning = false;
				}
			} else {
				if(debug) {
					System.out.println("<Runaway Update> - runningFrom was empty!");
				}
				state.isRunning = false;
			}
			// If no longer running, resume previous task.
			if (!state.isRunning) {
				if(debug) {
					System.out.println("<Runaway Update> - Stopped running. Continuing...");
				}
				state.runningFrom = NO_TARGET;
				ship.setCurrentAction(null);
			}
		}
		else {
			/**
			 * Rule: If collided with object, run away
			 */
			AbstractObject collidedWith = getClosestCollideable(space, ship);
			if (collidedWith != null) {
				if (debug) {
					System.out.println("<Collision Report> - Ship "+ship.getId()+" with "+collidedWith.getClass().getName()+": "+collidedWith.getId());
				}
				Position targetPosition = getRunawayPosition(space, ship, collidedWith);
				Vector2D targetVelocity = collidedWith.getPosition().getTranslationalVelocity();
				targetPosition.setTranslationalVelocity(targetVelocity);
				MoveAction runawayAction = new MoveAction(space, ship.getPosition(), targetPosition, targetVelocity);
				if (debug) {
					System.out.println("<Runaway Action> - Running to: "+targetPosition);
				}
				state.isRunning = true;
				state.runningFrom = collidedWith.getId();
				return runawayAction;
			}
		}

		// Rule: If energy is low, go for nearest energy source
		if (ship.getEnergy() < LOW_ENERGY_THRESHOLD) {
			return getNewObtainEnergyAction(space, ship);
		}

		// Rule: If the ship has enough resources, deposit them
		if (ship.getResources().getTotal() > ship.getEnergy() / 2) {
			nextAction = getNewDepositResourcesAction(space, ship);
			return nextAction;
		}

		// Rule: If not doing anything, just finished, or just hit base, then get new
		if (currentAction == null || currentAction.isMovementFinished(space) || state.justHitBase || state.target.equals(NO_TARGET)) {
			if (debug) {
				continueStepTracker = 0;
			}
			nextAction = getNewPursueTargetAction(space, ship);
			return nextAction;
		}
		/**
		 * Default: If we are currently pursuing something and have not just hit the
		 * base
		 */
		else {
			if (debug && (++continueStepTracker % 100 == 0)) {
				System.out.println("<Action Continuation> Continuing action..." + continueStepTracker);
			}
			return currentAction;
		}
	}

	/**
	 * Debug only, for printing all ship state fields.
	 */
	private void printState(Toroidal2DPhysics space, Ship ship, ShipState state) {
		System.out.println("ID: " + ship.getId());
		System.out.println("Last target: " + (state.target == null ? "null" : 
				state.target.equals(NO_TARGET) ? "NONE"
						: state.target + " (" + AgentUtils.getAbstractType(space.getObjectById(state.target)) + ")"));
		System.out.println("Last action: " + AgentUtils.getAbstractType(ship.getCurrentAction()));
		if(state.aimingForBase || state.justHitBase || state.target.equals(NO_TARGET)) {
			System.out.println("aimingForBase: "+state.aimingForBase);
			System.out.println("justHitBase: "+state.justHitBase);
		}
		if(state.target.equals(NO_TARGET)) {
			System.out.println("totalResources: "+state.totalResources);
			System.out.println("energy: "+state.energy);
			System.out.println("energyDiff: "+state.energyDiff);
		}
		if (state.isRunning || state.target.equals(NO_TARGET)) {
			System.out.println("isRunning: " + state.isRunning);
			System.out.println("runningFrom: " + state.runningFrom);
		}
//		System.out.println("isDead: "+state.isDead);
	}

	/**
	 * Finds a position suitable for running away from an object.
	 * 1. Finds a 2D vector perpendicular to the distance vector between the two objects.
	 * 2. Multiplies the vector by 1.5 * radius of the collided object.
	 * @param space physics model
	 * @param running Object attempting to flee
	 * @param attacker Aggressor
	 * @return 2D vector suitable for running away from collidedWith
	 */
	private Position getRunawayPosition(Toroidal2DPhysics space, AbstractObject running, AbstractObject attacker) {
		Vector2D distanceV = space.findShortestDistanceVector(running.getPosition(), attacker.getPosition());
		// Construct runaway unit vector perpendicular to distance vector
		double v2x = 1;
		double v2y = (-1) * v2x * distanceV.getXValue() / distanceV.getYValue();
		double mag = Math.sqrt(1 + Math.pow(v2y, 2));
		v2x /= mag;
		v2y /= mag;
		Vector2D runawayV = new Vector2D(v2x * attacker.getRadius(), v2y * attacker.getRadius());
		// Determine runaway position as current position + runaway vector
		Position runningCurr = running.getPosition();
		Position runawayDestination = new Position(runningCurr.getX() + runawayV.getXValue(),
				runningCurr.getY() + runawayV.getYValue());
		return runawayDestination;
	}

	/**
	 * Detects the closest collideable object to this ship.
	 * 
	 * @param space physics model
	 * @param ship  Ship to check for collisions
	 * @return Object that ship collided with, or null if no collision
	 */
	private AbstractObject getClosestCollideable(Toroidal2DPhysics space, Ship ship) {
		AbstractObject collision = null;
		ShipState state = shipStateMap.get(ship.getId());
		double energyDiff = state.energyDiff;
		// Collision has happened, determine closest object as likely source
		if (energyDiff < -1 * MAX_ACCELERATION) {
			double minDist = Double.MAX_VALUE;
			for (AbstractObject object : space.getAllObjects()) {
				// Ignored collisions include consumable items, ourselves, our base, and dead
				// things
				if (!AgentUtils.isDangerous(ship, object)) {
					continue;
				} else {
					double dist = space.findShortestDistance(object.getPosition(), ship.getPosition());
					if (dist < minDist) {
						minDist = dist;
						collision = object;
					}
				}
			}
			if (debug) {
				String typeName = AgentUtils.getAbstractType(collision);
				System.out.println("<Collision Detection> - Hit ("+typeName+") @ "+minDist);
			}
		}
		return collision;
	}

	/**
	 * Obtains a new target for the ship.
	 * 
	 * @param space physics model
	 * @param ship  ship to perform action
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
			Position shipP = ship.getPosition();
			Position asteroidP = targetAsteroid.getPosition();
			// Set target velocity to be the max of asteroid velocity and velocity of 15
			// (turned to correct direction)
			Vector2D vDirection = space.findShortestDistanceVector(shipP, asteroidP);
			double vXUnit = vDirection.getXValue() / vDirection.getMagnitude();
			double vYUnit = vDirection.getYValue() / vDirection.getMagnitude();
			Vector2D minVelocity = new Vector2D(vXUnit * MIN_ACCEPTABLE_VELOCITY, vYUnit * MIN_ACCEPTABLE_VELOCITY);
			Vector2D targetVelocity = (asteroidP.getTotalTranslationalVelocity() > minVelocity.getMagnitude()
					? asteroidP.getTranslationalVelocity()
					: minVelocity);
			newAction = new MoveToObjectAction(space, ship.getPosition(), targetAsteroid, targetVelocity);
			if (debug && (currentTarget.equals(NO_TARGET) || !currentTarget.equals(targetAsteroid.getId()))) {
				System.out.println("<Action Declaration> - Acquired new target: " + targetAsteroid.getId());
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
	 * @param ship  ship performing action
	 * @return Action to deposit resources (typically a movement)
	 */
	private AbstractAction getNewDepositResourcesAction(Toroidal2DPhysics space, Ship ship) {
		ShipState state = shipStateMap.get(ship.getId());
		Base base = AgentUtils.findNearestBase(space, ship);
		AbstractAction newAction = new MoveToObjectAction(space, ship.getPosition(), base);
		state.aimingForBase = true;
		state.target = base.getId();
		if (debug && !(state.target.equals(base.getId()))) {
			System.out.println("<Action Declaration> - Deposit (" + ship.getResources().getTotal() + ")");
		}
		return newAction;
	}

	/**
	 * Determines the best action for obtaining energy.
	 * 
	 * @param space physics model
	 * @param ship  ship performing action
	 * @return action to obtain energy
	 */
	private AbstractAction getNewObtainEnergyAction(Toroidal2DPhysics space, Ship ship) {
		ShipState state = shipStateMap.get(ship.getId());
		AbstractAction newAction = null;
		// Find energy source
		AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
		Position currentPosition = ship.getPosition();
		if (energyTarget != null) {
			if (!energyTarget.getId().equals(state.target)) {
				state.target = energyTarget.getId();
				newAction = new MoveToObjectAction(space, currentPosition, energyTarget);
				if (energyTarget instanceof Base) {
					state.aimingForBase = true;
				} else {
					state.aimingForBase = false;
				}
				if (debug) {
					System.out.println("<Action Declaration> - Chasing energy target: " + energyTarget.getId() + " ("
							+ AgentUtils.getAbstractType(energyTarget) + ")");
				}
			}
		} else {
			if (debug) {
				System.out.println("<Action Declaration> - Energy target returned null");
			}
			newAction = new DoNothingAction();
			state.target = NO_TARGET;
		}
		return newAction;
	}

	/**
	 * Picks asteroid by highest value, then by lowest distance, but only if no
	 * other ship is targeting it.
	 * 
	 * @param space physics model
	 * @param ship  ship performing action
	 * @return asteroid to pursue
	 */
	private Asteroid pickHighestValueNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
		Set<Asteroid> asteroids = space.getAsteroids();
		int bestMoney = Integer.MIN_VALUE;
		Asteroid bestAsteroid = null;
		double minDistance = Double.MAX_VALUE;

		for (Asteroid asteroid : asteroids) {
			if (stopNow) {
				System.out.println("Interrupted");
				return bestAsteroid;
			}
			if (!asteroidToShipMap.containsKey(asteroid.getId())) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > bestMoney) {
					double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
					if (dist < minDistance) {
						bestMoney = asteroid.getResources().getTotal();
						if (!AgentUtils.targetedByOpponent(space, ship, asteroid)) {
							bestAsteroid = asteroid;
							minDistance = dist;
						}
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
	 * @param base  Base
	 * @return Action for this base to take.
	 */
	private AbstractAction getBDSMBaseAction(Toroidal2DPhysics space, Base base) {
		/** For project 1, bases do nothing */
		return new DoNothingAction();
	}

	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		ArrayList<Asteroid> finishedAsteroids = new ArrayList<Asteroid>();

		for (UUID asteroidId : asteroidToShipMap.keySet()) {
			Asteroid asteroid = (Asteroid) space.getObjectById(asteroidId);
			Ship ship = asteroidToShipMap.get(asteroidId);
			if (asteroid == null || !asteroid.isAlive() || asteroid.isMoveable() 
					|| (ship != null && 
						(!shipStateMap.get(ship.getId()).target.equals(asteroidId))
							|| !ship.isAlive())) {
				finishedAsteroids.add(asteroid);
			}
		}

		for (Asteroid asteroid : finishedAsteroids) {
			Ship assailant = asteroidToShipMap.get(asteroid.getId());
			ShipState state = shipStateMap.get(assailant.getId());
			asteroidToShipMap.remove(asteroid.getId());
			// Only remove from target map if asteroid is dead
			if (asteroid != null && !asteroid.isAlive()) {
				state.target = NO_TARGET;
				if (state.totalResources < assailant.getResources().getTotal()) {
					if(debug) {
						System.out.println("<Action Completed> - Captured target: " + asteroid.getId());
					}
				}
				else {
					if(debug) {
						System.out.println("<Action Failed> - Target lost: "+asteroid.getId());
					}
				}
			}
		}

		/**
		 * Update ship states
		 */
		for (UUID shipId : shipStateMap.keySet()) {
			ShipState state = shipStateMap.get(shipId);
			// Check if ships have just hit the base (i.e. were aiming for it and have 0
			// resources).
			Ship ship = (Ship) space.getObjectById(shipId);
			if (state.aimingForBase) {
				if (ship.getResources().getTotal() == 0) {
					if (debug) {
						System.out.println("<" + shipId.toString() + "> - Hit the base and dropped off resources");
					}
					state.aimingForBase = false;
					state.justHitBase = true;
				}
			}
			if (!ship.isAlive()) {
				if (!state.isDead) {
					if(debug) {
						System.out.println("<Death Report> - Ship " + shipId + " has died!");
					}
				}
				state.isDead = true;
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
		// Init threading variables
		keepThreadsAlive = true;
		actionFinished = false;
		new TimeoutChecker().start();
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
		keepThreadsAlive = false;
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

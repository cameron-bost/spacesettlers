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
	private LinkedList<Position> positionList;
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
					positionList = new LinkedList<Position>();
					positionList.add(new Position(1450,300));
					positionList.add(new Position(1200,900));
					positionList.add(new Position(1450,500));
				}
				if(base.getTeamName().equalsIgnoreCase(getTeamName()) && base.getPosition().getX() > 800 )
				{
					baseIsRight = true;
					System.out.println("<OUR BASE> - is Right!!!");
					
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
				
				case GetResoruces:
					actions.put(s.getId(), GetResoruces(space, s));
					break;
					
				case DoNothing:
					//actions.put(s.getId(), DoNothing(space, s));
					actions.put(s.getId(), Guard(space, s));
					break;
					
				case Guard:
					actions.put(s.getId(), Guard(space, s));
					break;
					
				// Yikes don't want to be here.
				default:
					System.out.println("DiDNT get anything :(");
					break;
				}
			}
		}
		
		return actions;
		
		/**
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
						 *
						if(timeSincePlan >= 20) {
							ship.setCurrentAction(null); //resets current step to null so it is able to update step
							timeSincePlan = 0; // resets times plan back to 0
							currentPath = AStarGraph.getPathTo(flagShip,  enemyFlag, space); //Will get the current path that a* has chosen
							currentSearchTree = AStarGraph.getSearchTree(); //Returns a search tree 
							pointsToVisit = new LinkedList<Position>(currentPath.getPositions()); // Will contain all the points for a*
						}
						else
						{
							timeSincePlan++;
						}
						
						// Call points to create a new action to move to that object.
						if (pointsToVisit != null && flagShip.getCurrentAction() != null)
						{
							if(!pointsToVisit.isEmpty() && flagShip.getCurrentAction().isMovementFinished(space))
							{	
								Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
								action = new BDSMMoveAction(space, ship.getPosition(), newPosition);
								pointsToVisit.poll();//pops the top
							}
							else
							{
								pointsToVisit.poll();
							}
						}
						else
							action = new MoveToObjectAction(space, ship.getPosition(), enemyFlag,enemyFlag.getPosition().getTranslationalVelocity());
					}
				} 
				//Will allow if flag guard 1 is collected to.
				//1. Return flag if it has it
				//2. Collect flag with a certain distance.
				//3. go back to its post if flag is not there
				//4. Do noting if all the above is true.
				else if (flagGuard1 != null && ship.equals(flagGuard1) && !ship.equals(flagShip))
				{
						Flag enemyFlag = getEnemyFlag(space);
						double flagdistance = space.findShortestDistance(flagGuard1.getPosition(),enemyFlag.getPosition());
						double distanceFromSpot = space.findShortestDistance(flagGuard1.getPosition(),guardPosition1);
						
						if (flagGuard1.isCarryingFlag())
						{
							Base base = findNearestBase(space, ship);
							action = new MoveToObjectAction(space, ship.getPosition(), base);
							aimingForBase.put(ship.getId(), true);
						} 
						if(flagdistance < 350)
						{
							action = new MoveToObjectAction(space, flagGuard1.getPosition(), enemyFlag,enemyFlag.getPosition().getTranslationalVelocity());
						}
						
						if(distanceFromSpot > 150)
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
		return actions;*/
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
		if(distanceFromSpot > 100)
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
			/*
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					
					if(guardingPositions.get(ship.getId()) != null)
					{
						purchases.put(ship.getId(), PurchaseTypes.BASE);
					}
					
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
			}*/
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

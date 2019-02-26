package spacesettlers.bost7517;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.bost7517.astar.AStarGraph;
import spacesettlers.bost7517.astar.AStarPath;
import spacesettlers.bost7517.astar.Vertex;
import spacesettlers.clients.ExampleKnowledge;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
import spacesettlers.graphics.TargetGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Based on the PacifistHeuristicAsteroidCollectorTeamClient written by Dr. McGovern
 * 
 * @author Cameron Bost, Joshua Atkinson
 */
public class BDSMFriendyReflexAgent extends TeamClient {
	private boolean debug = false;
	private boolean showMyGraphics = false;
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	LinkedList<Position> listOfPositions;
	AStarGraph StarGraph = null;
	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;
	
	
	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 2000; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 3000;   // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 375; // #P1 - raised 200 -> 350 
	
	
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
		
		if(showMyGraphics)
		{
			
			graphicsToAdd = new ArrayList<SpacewarGraphics>();
			
			//create columns
			Position heightBottom = new Position(0,0);
			Position heightTop = new Position(0,space.getHeight());
				
		    for (int i = 30; i <= space.getWidth(); i = i + 30)
		    {
		    	Vector2D te = new Vector2D(0,space.getHeight());
		    	LineGraphics t= new LineGraphics(heightBottom,heightTop,te);
				heightBottom = new Position(i,0);
				heightTop = new Position(i,space.getHeight());
				t.setLineColor(Color.white);
				graphicsToAdd.add(t);
		    }
		    
			heightBottom = new Position(0,0);
			heightTop = new Position(space.getWidth(),0);
		    
		    for (int i = 30; i <= space.getHeight(); i = i + 30)
		    {
		    	Vector2D te = new Vector2D(space.getWidth(),0);
		    	LineGraphics t= new LineGraphics(heightBottom,heightTop,te);
				heightBottom = new Position(0,i);
				heightTop = new Position(space.getWidth(),i);
				t.setLineColor(Color.white);
				graphicsToAdd.add(t);
		    }
		    // Draw vertex
		    
		    List<Position> vertex = new ArrayList<Position>();
		    
		    for (int i = 0; i < space.getWidth()-30; i = i + 30)
		    {
		    	for(int a = 0; a < space.getHeight(); a = a + 30)
		    	{
		    		Position middle = new Position(i+15,a+15);
		    		vertex.add(middle);
					graphicsToAdd.add(new CircleGraphics(1,Color.white,middle));
		    	}
		    }
		    for(int i = 0 ; vertex.size() > i; i++)
		    	System.out.println(" I " + vertex.get(i));    
		}
		//End of Graphics
		 
		 

		//Create a Star graph
		StarGraph = new AStarGraph(space.getHeight(),space.getWidth(),true);
		boolean replan = false;
		
		//Allows for replan every 100 steps.
		if(space.getCurrentTimestep() % 100 == 0)
		{
			replan = true;
		}
		
		// Rule 1. If energy is low, go for nearest energy source
		if (ship.getEnergy() < LOW_ENERGY_THRESHOLD) {
			AbstractAction newAction = null;
			// Find energy source
			AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
			if(energyTarget != null) {
				/*
				newAction = new BDSMMoveToObjectAction(space, currentPosition, energyTarget);
				if(energyTarget instanceof Base) {
					aimingForBase.put(ship.getId(), true);
				}
				else {
					aimingForBase.put(ship.getId(), false);
				}
				*/
				if(listOfPositions == null)//makes sure that the list isn't null
				{
					listOfPositions = new LinkedList<Position>(); //creates an empty linked list
				}
				if(listOfPositions.isEmpty() )//makes sure that the first item is not null
				{
					listOfPositions = new LinkedList<Position>(); //creates a empty list if was not previously created
					AStarPath aStarPath = StarGraph.getPathTo(ship, energyTarget, space); // this creates the astar path for the astar graph
					listOfPositions = aStarPath.getPositions(); // list will hold the list of positions of the aStar Path
				}

				while(!listOfPositions.isEmpty() && ship.getCurrentAction() == null) // checks to make sure that the list is not empty
				{
					if(listOfPositions.getFirst() != null ) //checks to make sure that the first item itself is not null
					{
						newAction = new BDSMMoveToObjectAction(space, currentPosition, listOfPositions.poll(), energyTarget); // creates a new action
						aimingForBase.put(ship.getId(), true);
						return newAction;
					}
				}
				
				//deal with if a star does not get properly set
				newAction = new BDSMMoveToObjectAction(space, currentPosition, energyTarget);
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
			stepCount++;
			return newAction;
		}

		// Rule 2. If the ship has enough resources, deposit them
		if (ship.getResources().getTotal() > RESOURCE_THRESHOLD) {
			Base base = AgentUtils.findNearestBase(space, ship);
			AbstractAction newAction = null;
			
			if(listOfPositions == null)//makes sure that the list isn't null
			{
				listOfPositions = new LinkedList<Position>(); //creates an empty linked list
			}
			if(listOfPositions.isEmpty() )//makes sure that the first item is not null
			{
				listOfPositions = new LinkedList<Position>(); //creates a empty list if was not previously created
				AStarPath aStarPath = StarGraph.getPathTo(ship, base, space); // this creates the astar path for the astar graph
				listOfPositions = aStarPath.getPositions(); // list will hold the list of positions of the aStar Path
			}

			while(!listOfPositions.isEmpty() && ship.getCurrentAction() == null) // checks to make sure that the list is not empty
			{
				if(listOfPositions.getFirst() != null ) //checks to make sure that the first item itself is not null
				{
					newAction = new BDSMMoveToObjectAction(space, currentPosition, listOfPositions.poll(), base); // creates a new action
					aimingForBase.put(ship.getId(), true);
					return newAction;
				}
			}
			
			// if astars is not being implemented will have to run another localsearch.
			newAction = new BDSMMoveToObjectAction(space, currentPosition, base);
			aimingForBase.put(ship.getId(), true);
			
			if(debug){
				System.out.println("<Action Declaration> - Deposit (" + ship.getResources().getTotal()+")");
			}
			stepCount++;
			return newAction;
		}

		// Rule 3. If not doing anything, just finished, or just hit base, then aim for best asteroid
		if (current == null || current.isMovementFinished(space) || 
				(justHitBase.containsKey(ship.getId()) && justHitBase.get(ship.getId()))) 
		{
			// Update model
			justHitBase.put(ship.getId(), false);			
			aimingForBase.put(ship.getId(), false);
			
			// Get best asteroid
			Asteroid asteroid = pickHighestValueNearestFreeAsteroid(space, ship);
			
			if(showMyGraphics)
			{
				//CREATE A LINE From the ship to the current target asteroid.
				graphicsToAdd.add(new StarGraphics(3, this.getTeamColor(), listOfPositions.getFirst()));
				LineGraphics line = new LineGraphics(ship.getPosition(), listOfPositions.getFirst(), 
				space.findShortestDistanceVector(ship.getPosition(), listOfPositions.getFirst()));
				line.setLineColor(this.getTeamColor());
				graphicsToAdd.add(line);
			}
			
			//Re Runs AStarPath to get updated path towards an asteroid.
			if((space.getCurrentTimestep() % 100 == 0 || replan == true) && asteroid != null) // allows us to replan astar on every 50th time step
			{
				listOfPositions = new LinkedList<Position>(); 
				AStarPath aStarPath = StarGraph.getPathTo(ship, asteroid, space); // this creates the astar path for the astar graph
				listOfPositions = aStarPath.getPositions(); // list will hold the list of positions of the aStar Path
			}
				
			AbstractAction newAction = null;
			
			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
				
				if(debug)
				{
					System.out.println("<Action Declaration> - Chasing asteroid");
					System.out.println("<Velocity Check> - "+ship.getPosition().getTranslationalVelocity());
				}
				
				if(listOfPositions == null ) // checks to make sure the linked list is not null
				{
					listOfPositions = new LinkedList<Position>();  //creates a empty linked list of positions.
				}
				if(!listOfPositions.isEmpty()) // checks to make sure that the list is not empty
				{
					if(listOfPositions.getFirst() != null ) //checks to make sure that the first item itself is not null
					{
						//Will create a new action in which the action is the first item(vertex) in the linked list.
						newAction = new BDSMMoveToObjectAction(space, currentPosition, listOfPositions.poll() ,asteroid,asteroid.getPosition().getTranslationalVelocity()); // creates a new action
						
						//System.out.println("<LIST OF POSITIONS AFTER - " + listOfPositions.getFirst());
						//Vector2D t = new Vector2D(listOfPositions.getFirst().getX(),listOfPositions.getFirst().getY());
						//newAction = new BDSMMoveAction(space,currentPosition,listOfPositions.poll(),t);
						
						return newAction;
					}
				} 

				// if astars is not being implemented will have to run another localsearch.
				newAction = new BDSMMoveToObjectAction(space, currentPosition,asteroid, asteroid.getPosition().getTranslationalVelocity());

						
				return newAction;
			}
		}
		
		
		if(debug) {
			System.out.println("<Action Declaration> - Continuing action...");
		}
		stepCount++;
		
		return ship.getCurrentAction();
	}
	
	
	
	/**
	 * This function will find the closest valued asteroid. It does not determine by the amount that the asteroid is worth.
	 * 
	 * @return
	 */
	private Asteroid pickNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
		/*
		 * this will gather all the resources that are currently available on the map and stores them in variable to use. This will allow us to search each
		 * asteroid individually.
		 */
        Set<Asteroid> asteroids = space.getAsteroids();
        Asteroid bestAsteroid = null;
        double minDistance = Double.MAX_VALUE; //declares value for min distance.
        for (Asteroid asteroid : asteroids) {
            if (!asteroidToShipMap.containsKey(asteroid.getId())) {
                if (asteroid.isMineable()) { // determines if the asteroid is mineable
                    double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());// finds the distance between the asteroid and the ship.
                    if (dist < minDistance) { // Will determine if the new distance is less then the previous distance.
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
	 * This function will find the highest valued asteroid. It will determine the best value target by its distance and value.
	 * 
	 * @return
	 */
	private Asteroid pickHighestValueNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
		/*
		 * this will gather all the resources that are currently available on the map and stores them in variable to use. This will allow us to search each
		 * asteroid individually.
		 */
		Set<Asteroid> asteroids = space.getAsteroids();
		int bestMoney = Integer.MIN_VALUE;
		Asteroid bestAsteroid = null;
		double minDistance = Double.MAX_VALUE;
		for (Asteroid asteroid : asteroids) { // This will cycle each asteroid
			if (!asteroidToShipMap.containsKey(asteroid.getId())) { 
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > bestMoney) { // checks to make sure that we are only searching mineable asteroids and the asteroids is higher then a previous asteroid.
					double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition()); // finds the distance between the asteroid and the ship.
					if (dist < minDistance) { // Will determine if the new distance is less then the previous distance.
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
		listOfPositions = null;
		if(showMyGraphics)
		{
			System.out.println("<<INIT GRID MAPPING>>");
			graphicsToAdd = new ArrayList<SpacewarGraphics>();
			Position heightBottom = new Position(0,30);
			Position heightTop = new Position(space.getHeight(),30);
				
		    for (int i = 30; i < 1000; i = i + 30)
		    {
		    	LineGraphics t= new LineGraphics(heightTop,heightBottom,space.findShortestDistanceVector(heightBottom, heightTop));;
				heightBottom = new Position(0,i);
				heightTop = new Position(space.getHeight(),i);
				t.setLineColor(Color.white);
				graphicsToAdd.add(t);
		    }
		    
		}
		
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
		if(showMyGraphics)
		{
			HashSet<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();
			graphics.addAll(graphicsToAdd);
			graphicsToAdd.clear();
			return graphics;
		}
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

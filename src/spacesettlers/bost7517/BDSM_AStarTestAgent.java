package spacesettlers.bost7517;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.bost7517.AStarPath;
import spacesettlers.clients.ExampleKnowledge;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
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
public class BDSM_AStarTestAgent extends TeamClient {
	private boolean debug = false;
	private boolean showMyGraphics = false;
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	
	private AStarPath currentPath = null;
	private GBFSPath currentPathGBFS = null;
	
	private LinkedList<AStarPath> currentSearchTree;
	
	private AStarGraph graph;
	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;
	
	private int timeSincePlan = 20;
	
	/**Data writer for statistical output*/
	private BufferedWriter dataOut;
	
	private final String FILE_HEADER = "minCost,astarCost,gbfsCost";
	
	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 1500; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 2500;   // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 350; // #P1 - raised 200 -> 350 
	
	static final int GRID_SIZE = AStarGraph.GRID_SIZE;
	
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
//			System.out.println("<<INIT GRID MAPPING>> -- " + space.getHeight());
			graphicsToAdd = new ArrayList<SpacewarGraphics>();
			
			//create columns
			Position heightBottom = new Position(0,0);
			Position heightTop = new Position(0,space.getHeight());
				
		    for (int i = GRID_SIZE; i <= space.getWidth(); i = i + GRID_SIZE)
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
		    
		    for (int i = GRID_SIZE; i <= space.getHeight(); i = i + GRID_SIZE)
		    {
		    	Vector2D te = new Vector2D(space.getWidth(),0);
		    	LineGraphics t= new LineGraphics(heightBottom,heightTop,te);
				heightBottom = new Position(0,i);
				heightTop = new Position(space.getWidth(),i);
				t.setLineColor(Color.white);
				graphicsToAdd.add(t);
		    }
		    
		    drawSearchTree(space);
		}
		
		// Rule 1. If energy is low, go for nearest energy source
		if (ship.getEnergy() < LOW_ENERGY_THRESHOLD) {
			AbstractAction newAction = null;
			// Find energy source
			AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
			if(energyTarget != null) {
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
			AbstractAction newAction = new BDSMMoveToObjectAction(space, currentPosition, base);
			aimingForBase.put(ship.getId(), true);
			if(debug){
				System.out.println("<Action Declaration> - Deposit (" + ship.getResources().getTotal()+")");
			}
			stepCount++;
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
			if(showMyGraphics) {
				//CREATE A LINE From the ship to the current target asteroid.
				graphicsToAdd.add(new StarGraphics(3, this.getTeamColor(), asteroid.getPosition()));
				LineGraphics line = new LineGraphics(ship.getPosition(), asteroid.getPosition(), 
						space.findShortestDistanceVector(ship.getPosition(), asteroid.getPosition()));
				
				line.setLineColor(this.getTeamColor());
				graphicsToAdd.add(line);
				//Create N number of objects;
				/*
				for(int i = 0; i < 20 ;i++)
				{
					
					double shipxValue = ship.getPosition().getX();
					double asteroidxValue = asteroid.getPosition().getX();
					double shipyValue = ship.getPosition().getY();
					double asteroidyValue = asteroid.getPosition().getY();
					
					//double randomX = ThreadLocalRandom.current().nextDouble(shipxValue, asteroidxValue);
					//double randomY = ThreadLocalRandom.current().nextDouble(shipyValue, asteroidyValue);
					
					Random rand = new Random();
					double randomxValue = shipxValue + (asteroidxValue - shipxValue) * rand.nextDouble();
					double randomyValue = shipyValue + (asteroidyValue - shipyValue) * rand.nextDouble();
					Position middle = new Position (randomxValue,randomyValue);
					graphicsToAdd.add(new CircleGraphics(Color.WHITE,middle));
				}*/
			}
			AbstractAction newAction = null;
			
			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
				
				if(timeSincePlan >= 20) {
					timeSincePlan = 0;
					currentPath = graph.getPathTo(ship,  asteroid, space);
					currentPathGBFS = graph.getPathToGBFS(ship,  asteroid, space);
					// Export data
					exportData((int)space.findShortestDistance(ship.getPosition(), asteroid.getPosition()), currentPath.getTotalCost(), currentPathGBFS.getTotalCost());
				}
				else {
					timeSincePlan++;
				}
				//newAction = new MoveAction(space,currentPosition,asteroid.getPosition());
				newAction = new BDSMMoveToObjectAction(space, currentPosition, asteroid, 
						asteroid.getPosition().getTranslationalVelocity());
				
				if(debug)
				{
					System.out.println("<Action Declaration> - Chasing asteroid");
					System.out.println("<Velocity Check> - "+ship.getPosition().getTranslationalVelocity());
				}
				stepCount++;
				return newAction;
			}
		}
		if(debug) {
			System.out.println("<Action Declaration> - Continuing action...");
		}
		stepCount++;
		return ship.getCurrentAction();
	}
	
	private void exportData(int min_cost, int astar_path_cost, int gbfs_path_cost) {
		// Export data
		try {
			String dataOutStr = String.format("%d,%d,%d", min_cost,astar_path_cost,gbfs_path_cost);
			dataOut.write(dataOutStr);
			dataOut.newLine();
			System.out.println("<SearchTest.exportStats> - Wrote data to file: "+dataOutStr);
		}
		catch(IOException e) {
			System.out.println("<SearchTest.exportStats> - Error while writing data to file: "+e.getMessage());
		}
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
		graph = AStarGraph.getInstance(space.getHeight(), space.getWidth(), true);
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
		if(showMyGraphics)
		{
//			System.out.println("<<INIT GRID MAPPING>>");
			graphicsToAdd = new ArrayList<SpacewarGraphics>();
			Position heightBottom = new Position(0,GRID_SIZE);
			Position heightTop = new Position(space.getHeight(),GRID_SIZE);
				
		    for (int i = GRID_SIZE; i < 1000; i = i + GRID_SIZE)
		    {
		    	LineGraphics t= new LineGraphics(heightTop,heightBottom,space.findShortestDistanceVector(heightBottom, heightTop));;
				heightBottom = new Position(0,i);
				heightTop = new Position(space.getHeight(),i);
				t.setLineColor(Color.white);
				graphicsToAdd.add(t);
		    }
		    
		}
		
		try {
			dataOut = new BufferedWriter(new FileWriter("search_compare_data.txt"));
			System.out.println("<SearchTest.INIT> - Opened output stream");
			dataOut.write(FILE_HEADER);
			dataOut.newLine();
			System.out.println("<SearchTest.INIT> - Wrote file header");
		} catch (IOException e) {
			System.out.println("<SearchTest.INIT> - Error while opening output stream: "+e.getMessage());
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
			dataOut.close();
			System.out.println("<A*.shutdown> - Closed file stream");
		}
		catch(IOException e) {
			System.out.println("<A*.shutdown> - Error encountered while closing output stream: "+e.getMessage());
		}
		
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
	
	void drawSearchTree(Toroidal2DPhysics space) {
		if(currentSearchTree != null) {
			HashSet<LineGraphics> drawnPositions = new HashSet<>();
			for(AStarPath path: currentSearchTree) {
				Position prevPosition = null;
				for(Position p: path.getPositions()) {
					if(prevPosition != null) {
						LineGraphics nextLine = new LineGraphics(prevPosition, p, space.findShortestDistanceVector(prevPosition, p));
						drawnPositions.add(nextLine);
					}
					prevPosition = p;
				}
			}
			for(LineGraphics g: drawnPositions) {
				graphicsToAdd.add(g);
			}
		}
		
		if(currentPath != null) {
			HashSet<LineGraphics> drawnPositions = new HashSet<>();

			Position prevPosition = null;
			for(Position p: currentPath.getPositions()) {
				if(prevPosition != null) {
					LineGraphics nextLine = new LineGraphics(prevPosition, p, space.findShortestDistanceVector(prevPosition, p));
					drawnPositions.add(nextLine);
				}
				prevPosition = p;
			}
			for(LineGraphics g: drawnPositions) {
				g.setLineColor(Color.GREEN);
				graphicsToAdd.add(g);
			}
			
		}
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

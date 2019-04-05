package spacesettlers.bost7517;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.ExampleKnowledge;
import spacesettlers.clients.ImmutableTeamInfo;
import spacesettlers.clients.TeamClient;
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
	private final int K_KMEANS = 3;
	private static final int MAX_ITERATIONS_KMEANS = 20;
	private boolean debug = false;
	private boolean showMyGraphics = true;
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	HashMap <UUID, Boolean> justHitBase;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	private LinkedList<Position> pointsToVisit;
	private AStarPath currentPath = null;
	private LinkedList<AStarPath> currentSearchTree;
	
	private spacesettlers.bost7517.AStarGraph graph;
	/**
	 * Example knowledge used to show how to load in/save out to files for learning
	 */
	ExampleKnowledge myKnowledge;
	
	private int timeSincePlan = 10;
	
	private static final File KMEANS_OUT_FILE = new File("kmeans_fitness_continuous.csv");
	
	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 2750; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 2000;   // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 400; // #P1 - raised 200 -> 350 
	
	static final int GRID_SIZE = spacesettlers.bost7517.AStarGraph.GRID_SIZE;
	
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
		//This will display graphics if enabled.
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
				t.setLineColor(Color.gray);
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
				t.setLineColor(Color.gray);
				graphicsToAdd.add(t);
		    }
		    
		    drawSearchTree(space);
		    drawBlockedGrids();
		}
		// Rule 1. If energy is low, go for nearest energy source

		if ((ship.getEnergy() < LOW_ENERGY_THRESHOLD)) {
			AbstractAction newAction = null;
			AbstractObject energyTarget = AgentUtils.findNearestEnergySource(space, ship);
			
	
			if(current != null)
			{
				
				if(energyTarget != null) {
					//Re-plans every 20 timesteps.
					if(timeSincePlan >= 10) {
						current = null; //resets current stepp to null so it is able to update step
						timeSincePlan = 0; // resets times plan back to 0
						currentPath = AStarGraph.getPathTo(ship,  energyTarget, space); //Wil get the current path that a* has chosen
						currentSearchTree = AStarGraph.getSearchTree(); //Returns a search tree 
						pointsToVisit = new LinkedList<Position>(currentPath.getPositions()); // Will contain all the points for a*
					}
					else
					{
						timeSincePlan++;
					}
					if(current != null && (ship.getEnergy() > LOW_ENERGY_THRESHOLD)) // Want to make sure not to interrupt an a* move that has not finished yet.
					{
						if(!current.isMovementFinished(space)) //checks if the object has stopped moving
						{
							return current;
						}
					}
					// Call points to create a new action to move to that object.
					if (pointsToVisit != null)
					{
						if(!pointsToVisit.isEmpty())
						{	
							Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
							newAction = new BDSMMoveAction(space, currentPosition, newPosition);
							//Will display graphics if set to true.
							if(showMyGraphics)
							{
							//LINE!!!
								graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
								LineGraphics line = new LineGraphics(currentPosition, newPosition, 
										space.findShortestDistanceVector(currentPosition, newPosition));
								line.setLineColor(Color.RED);
								graphicsToAdd.add(line);
							}
							
							pointsToVisit.poll();//pops the top
							return newAction;
						}
					}
					
					//Planning currently need to run local search
					newAction = new BDSMMoveToObjectAction(space, currentPosition, energyTarget);
					
					if(energyTarget instanceof Base) {
						aimingForBase.put(ship.getId(), true);
					}
					else {
						
						aimingForBase.put(ship.getId(), false);
						}
					return newAction;
				}
			}
			else {
				if(debug) {
					System.out.println("Energy target returned null");
				}
				//Continues to next rule
			}
			
		}

		// Rule 2. If the ship has enough resources, deposit them
		if (ship.getResources().getTotal() > RESOURCE_THRESHOLD) {
			AbstractAction newAction = null;
			Base base = AgentUtils.findNearestBase(space, ship);
			
			// If time step is greater then 20 will rerun planning.
			if(timeSincePlan >= 10) {
				current = null;
				timeSincePlan = 0;
				currentPath = AStarGraph.getPathTo(ship,  base, space);
				currentSearchTree = AStarGraph.getSearchTree();
				pointsToVisit = new LinkedList<Position>(currentPath.getPositions());
			}
			else
			{
				timeSincePlan++;
			}
			
			//Checks that the previous A* action is completed.
			if(current != null)
			{
				if(!current.isMovementFinished(space))
				{
					aimingForBase.put(ship.getId(), true);
					return current;
				}
			}
			if (pointsToVisit != null)
			{
				if(!pointsToVisit.isEmpty())
				{	
					Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
					newAction = new BDSMMoveAction(space, currentPosition, newPosition);
					//This will display graphics if they are enabled.
					if(showMyGraphics)
					{
					//LINE!!!
						graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
						LineGraphics line = new LineGraphics(currentPosition, newPosition, 
								space.findShortestDistanceVector(currentPosition, newPosition));
						line.setLineColor(Color.RED);
						graphicsToAdd.add(line);
					}
					
					pointsToVisit.poll();//pops the top
					aimingForBase.put(ship.getId(), true);
					return newAction;
				}
			}
			
			//Run a Local search for base
			newAction = new BDSMMoveToObjectAction(space, currentPosition, base); // Will run a local search for base
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
			Asteroid asteroid = pickHighestValueKMeansAsteroid(space, ship);
			AbstractAction newAction = null;
			
			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
				//Re-plans every 10 steps.
				if(timeSincePlan >= 10) {
					current = null;
					timeSincePlan = 0;
					currentPath = AStarGraph.getPathTo(ship,  asteroid, space);
					currentSearchTree = AStarGraph.getSearchTree();
					pointsToVisit = new LinkedList<Position>(currentPath.getPositions());
				}
				else
				{
					timeSincePlan++;
				}
				//Checks to make sure that the current A* is move is finished.
				if(current != null)
				{
					if(!current.isMovementFinished(space))
					{
						return current;
					}
				}
				//Will create actions for A* points.
				if (pointsToVisit != null)
				{
					if(!pointsToVisit.isEmpty())
					{	
						//Will assign a Position variable with the positions.
						Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
						//Create the action to move to the A* position.
						newAction = new BDSMMoveAction(space, currentPosition, newPosition, asteroid);
						//This will displayed graphics if true.
						if(showMyGraphics)
						{
							graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
							LineGraphics line = new LineGraphics(currentPosition, newPosition, 
									space.findShortestDistanceVector(currentPosition, newPosition));
							line.setLineColor(Color.RED);
							graphicsToAdd.add(line);
						}
						
						pointsToVisit.poll();//pops the top
						return newAction;
					}
				}
				
				// Runs a local search
				newAction = new BDSMMoveToObjectAction(space, currentPosition, asteroid, asteroid.getPosition().getTranslationalVelocity());			
				
				return newAction;
			}
		}
		if(debug) {
			System.out.println("<Action Declaration> - Continuing action...");
		}
		stepCount++;
		return ship.getCurrentAction();
	}
	
	
	
	private void drawBlockedGrids() {
		if(showMyGraphics) {
			for(Vertex v: graph.getBlockedVertices()) {
				SpacewarGraphics g = new TargetGraphics(15, AStarGraph.getCentralCoordinate(v));
				graphicsToAdd.add(g);
			}
		}
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
		return bestAsteroid;
	}
	
	/**
	 * Performs K-Means clustering on all mineable asteroids,
	 * then measures the value of each cluster to determine the best option.
	 * 
	 * @param space physics model
	 * @param ship ship seeking asteroid
	 * @return highest valued asteroid in highest valued cluster
	 */
	private Asteroid pickHighestValueKMeansAsteroid(Toroidal2DPhysics space, Ship ship) {
		PriorityQueue<BDSM_KMeansAsteroidCluster> pQueue = new PriorityQueue<>();
		// For 2 -> K
		for(int i = 2; i <= K_KMEANS; i++) {
			pQueue.addAll(kMeans(space, ship, i));
		}
		BDSM_KMeansAsteroidCluster targetCluster = pQueue.poll();
		if(AgentUtils.DEBUG) {
			System.out.println("Chosen target cluster: "+targetCluster.getCentroid());
			System.exit(-1);
		}
		return targetCluster.getBestAsteroid();
	}
	

	private Collection<BDSM_KMeansAsteroidCluster> kMeans(Toroidal2DPhysics space, Ship ship, int k) {
		ArrayList<BDSM_KMeansAsteroidCluster> clusters = new ArrayList<>(k);
		/**Shuffle asteroid set*/
		Set<Asteroid> asteroidsSet = space.getAsteroids();
		Asteroid[] asteroids = asteroidsSet.toArray(new Asteroid[asteroidsSet.size()]);
		for(int i = asteroids.length-1; i >= 1; i--) {
			int swapIdx = random.nextInt(i+1);
			Asteroid t = asteroids[swapIdx];
			asteroids[swapIdx] = asteroids[i];
			asteroids[i] = t;
		}
		/**Create clusters on k random asteroids*/
		for(int i = 0; i < k; i++) {
			clusters.add(new BDSM_KMeansAsteroidCluster(asteroids[i], ship));
		}
		
		// Iterate until movement stops
		HashMap<BDSM_KMeansAsteroidCluster, Boolean> dupCheck = new HashMap<>();
		HashMap<UUID, BDSM_KMeansAsteroidCluster> astClstMap = new HashMap<>();
		boolean moveHappened = true;
		int iterCount = 0;
		do {
			moveHappened = false;
			// Reset centroid values
			for(BDSM_KMeansAsteroidCluster c: clusters) {
				c.resetCentroid(space);
			}
			
			// Check for duplicate clusters, break if they are all duplicate
			boolean dupFound = true;
			for(BDSM_KMeansAsteroidCluster c: clusters) {
				dupFound &= dupCheck.containsKey(c);
				dupCheck.putIfAbsent(c, true);
			}
			// Dup found -> stop
			if(dupFound) {
				if(AgentUtils.DEBUG) {
					System.out.println("***DUPLICATE ITERATION FOUND***");
					for(BDSM_KMeansAsteroidCluster c: clusters) {
						c.printReport();
					}
				}
			}
			// No dup found -> continue
			else {
				// Clear clusters
				for(BDSM_KMeansAsteroidCluster c: clusters) {
					c.clear();
				}
				
				// Place each asteroid in correct cluster
				for(Asteroid a: asteroids) {
					BDSM_KMeansAsteroidCluster oldCluster = astClstMap.get(a.getId());
					double minDistance = Double.MAX_VALUE;
					BDSM_KMeansAsteroidCluster minCluster = null;
					// Check distance to each cluster to find minimum
					for(BDSM_KMeansAsteroidCluster cluster: clusters) {
						double dist = space.findShortestDistance(a.getPosition(), cluster.getCentroid());
						if(dist < minDistance) {
							minDistance = dist;
							minCluster = cluster;
						}
					}
					// Add to cluster
					minCluster.add(a);
					astClstMap.put(a.getId(), minCluster);
					
					// Indicate if asteroid changed clusters
					if(minCluster != oldCluster) {
						moveHappened = true;
					}
				}
				
				// Iteration report
				if(AgentUtils.DEBUG) {
					System.out.println("KMEANS ITERATION "+iterCount++);
					for(BDSM_KMeansAsteroidCluster c: clusters) {
						c.printReport();
					}
				}
			}
		} while(moveHappened && iterCount < MAX_ITERATIONS_KMEANS);
		return clusters;
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
		
		// Output fitness every 10 time-steps
		if(timeSincePlan % 50 == 0) {
			// Get current score
			double score = 0;
			for(ImmutableTeamInfo ti: space.getTeamInfo()) {
				if(ti.getTeamName().equals(this.getTeamName())) {
					score = ti.getScore();
					break;
				}
			}
			// KMeans data export
			try(BufferedWriter fOut = new BufferedWriter(new FileWriter(KMEANS_OUT_FILE, KMEANS_OUT_FILE.exists()))){
				fOut.write(Double.toString(score));
				fOut.newLine();
			} catch (IOException e) {
				System.err.println("Failed to output to file: "+e.getMessage());
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

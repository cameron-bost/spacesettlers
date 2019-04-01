package spacesettlers.bost7517;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
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
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.TargetGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Demonstrates one idea on implementing Genetic Algorithms/Evolutionary Computation within the space settlers framework.
 * 
 * @author amy
 *
 */

public class AtkiGAClient extends TeamClient {
	
	private boolean showMyGraphics = true;
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	
	/**
	 * The current policy for the team
	 */
	private AtkiGAChromosome currentPolicy;
	
	/**
	 * The current population (either being built or being evaluated)
	 */
	private AtkiGAPopulation population;
	
	/**
	 * How many steps each policy is evaluated for before moving to the next one
	 */
	private int evaluationSteps = 2000;
	
	/**
	 * How large of a population to evaluate
	 */
	private int populationSize = 25;
	
	/**
	 * Current step
	 */
	private int steps = 0;
	/**
	 * AStar
	 */
	private AStarGraph graph = null;
	static final int ASTAR_GRID_SIZE = AStarGraph.GRID_SIZE;
	
	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 4750; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 2000;   // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 400; // #P1 - raised 200 -> 350 
	
	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// make a hash map of actions to return
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();

		// loop through each ship and send it the current policy from the chromosome.  If the chromosome
		// hasn't seen a state before, it will pick an abstract action (you should make more interesting actions!
		// this agent chooses only between doNothing and moveToNearestAsteroid)
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				if(showMyGraphics)
				{
					showGraphics(space);
				}

				AbstractAction action = ship.getCurrentAction();
				
				//Will choose policy 1. to collect energy shield
				
				if ((ship.getEnergy() < LOW_ENERGY_THRESHOLD)) 
				{
					AtkiGAState currentState = new AtkiGAState(space, ship);	
					action = currentPolicy.getCurrentAction(space, ship, currentState, 1);				
				}
				
				//Will choose policy 2. to return to base.
				
				else if (ship.getResources().getTotal() > RESOURCE_THRESHOLD) 
				{
					AtkiGAState currentState = new AtkiGAState(space, ship);	
					action = currentPolicy.getCurrentAction(space, ship, currentState, 2);	
				}
				
				//Will choose policy 3. to collect NEAREST resource.
				
				else if (ship.getCurrentAction() == null || ship.getCurrentAction().isMovementFinished(space))
				{		
					AtkiGAState currentState = new AtkiGAState(space, ship);			
					action = currentPolicy.getCurrentAction(space, ship, currentState, 3);
				} 		

				actions.put(ship.getId(), action);
			} 
			/*else {
				// it is a base.  Heuristically decide when to use the shield (TODO)
				actions.put(actionable.getId(), new DoNothingAction());
			}*/
		}
		System.out.println("actions are " + actions);
		return actions;

	}
	
	void showGraphics(Toroidal2DPhysics space) {
		graphicsToAdd = new ArrayList<SpacewarGraphics>();
		
		//create columns
		Position heightBottom = new Position(0,0);
		Position heightTop = new Position(0,space.getHeight());
			
	    for (int i = ASTAR_GRID_SIZE; i <= space.getWidth(); i = i + ASTAR_GRID_SIZE)
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
	    
	    for (int i = ASTAR_GRID_SIZE; i <= space.getHeight(); i = i + ASTAR_GRID_SIZE)
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
	
	void drawSearchTree(Toroidal2DPhysics space) {
		LinkedList<AStarPath> currentSearchTree = currentPolicy.getCurrentSearchTree();
		AStarPath currentPath = currentPolicy.getCurrentPath();
		if (currentSearchTree != null) {
			HashSet<LineGraphics> drawnPositions = new HashSet<>();
			for (AStarPath path : currentSearchTree) {
				Position prevPosition = null;
				for (Position p : path.getPositions()) {
					if (prevPosition != null) {
						LineGraphics nextLine = new LineGraphics(prevPosition, p,
								space.findShortestDistanceVector(prevPosition, p));
						drawnPositions.add(nextLine);
					}
					prevPosition = p;
				}
			}
			for (LineGraphics g : drawnPositions) {
				 graphicsToAdd.add(g);
			}
		}

		if (currentPath != null) {
			HashSet<LineGraphics> drawnPositions = new HashSet<>();

			Position prevPosition = null;
			for (Position p : currentPath.getPositions()) {
				if (prevPosition != null) {
					LineGraphics nextLine = new LineGraphics(prevPosition, p,
							space.findShortestDistanceVector(prevPosition, p));
					drawnPositions.add(nextLine);
				}
				prevPosition = p;
			}
			for (LineGraphics g : drawnPositions) {
				g.setLineColor(Color.GREEN);
				// graphicsToAdd.add(g);
			}

		}
	}
	
	private void drawBlockedGrids() {
		if(showMyGraphics) {
			for(Vertex v: graph.getBlockedVertices()) {
				SpacewarGraphics g = new TargetGraphics(15, graph.getCentralCoordinate(v));
				graphicsToAdd.add(g);
			}
		}
	}

	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		// increment the step counter
		steps++;

		// if the step counter is modulo evaluationSteps, then evaluate this member and move to the next one
		if (steps % evaluationSteps == 0) {
			// note that this method currently scores every policy as zero as this is part of 
			// what the student has to do
			population.evaluateFitnessForCurrentMember(space);

			// move to the next member of the population
			currentPolicy = population.getNextMember();

			if (population.isGenerationFinished()) {
				// note that this is also an empty method that a student needs to fill in
				population.makeNextGeneration();
				
				currentPolicy = population.getNextMember();
			}
			
		}
		
	}

	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// TODO Auto-generated method stub
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
						System.out.println("Buying a base!!");
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
	 * Initialize the population by either reading it from the file or making a new one from scratch
	 * 
	 * @param space
	 */
	@Override
	public void initialize(Toroidal2DPhysics space) {
		XStream xstream = new XStream();
		xstream.alias("ExampleGAPopulation", AtkiGAPopulation.class);
		graph = AStarGraph.getInstance(space.getHeight(), space.getWidth(), true);

		// try to load the population from the existing saved file.  If that failes, start from scratch
		try { 
			population = (AtkiGAPopulation) xstream.fromXML(new File(getKnowledgeFile()));
		} catch (XStreamException e) {
			// if you get an error, handle it other than a null pointer because
			// the error will happen the first time you run
			System.out.println("No existing population found - starting a new one from scratch");
			population = new AtkiGAPopulation(populationSize, graph, random);
		}
		currentPolicy = population.getFirstMember();
	}

	@Override
	public void shutDown(Toroidal2DPhysics space) {
		XStream xstream = new XStream();
		xstream.alias("ExampleGAPopulation", AtkiGAPopulation.class);

		try { 
			// if you want to compress the file, change FileOuputStream to a GZIPOutputStream
			xstream.toXML(population, new FileOutputStream(new File(getKnowledgeFile())));
		} catch (XStreamException e) {
			// if you get an error, handle it somehow as it means your knowledge didn't save
			System.out.println("Can't save knowledge file in shutdown ");
			System.out.println(e.getMessage());
		} catch (FileNotFoundException e) {
			// file is missing so start from scratch (but tell the user)
			System.out.println("Can't save knowledge file in shutdown ");
			System.out.println(e.getMessage());
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
	
	

}

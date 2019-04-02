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
 * Heavily inspired by clients.examples.ExampleGAClient, written by Amy McGovern.
 * 
 * This client is a learning agent capable of maintaining functionalities from 
 * previous versions while adding the ability to optimize some specific numeric
 * values (genes) via a genetic algorithm.
 * 
 * @author Joshua Atkinson, Cameron Bost
 * @version 0.3
 *
 */

public class AtkiGAClient extends TeamClient {
	/**
	 * Set of graphics to be displayed. Generated during each timestep.
	 */
	private ArrayList<SpacewarGraphics> graphicsToAdd;
	/**
	 * Radius of graphic used for blocked grids
	 */
	static final int BLOCKED_GRID_GRAPHIC_RADIUS = 15;
	
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
	static final int EVALUATION_STEPS = 2000;
	
	/**
	 * How large of a population to evaluate
	 */
	private int populationSize = 25;
	
	/**
	 * Current step
	 */
	private int steps = 0;
	/**
	 * AStar graph object
	 */
	private AStarGraph graph = null;
	/**
	 * Local variable for AStar graph grid size (pixels)
	 */
	static final int ASTAR_GRID_SIZE = AStarGraph.GRID_SIZE;
	
	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// Return value, contains an action for every actionable object associated with this team.
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();

		// For each object associated with this team (ship(s), base(s))
		for (AbstractObject actionable :  actionableObjects) {
			// Decide action for ship
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				// Default action is ship's current action
				AbstractAction action = ship.getCurrentAction();
				
				// Determine policy number (default 0 - no policy)
				int policyNumber = 0;
				
				// Policy 1: if energy is low, target energy beacon
				if ((ship.getEnergy() < AgentUtils.LOW_ENERGY_THRESHOLD)) {
					policyNumber = 1;			
				}
				
				// Policy 2: if on-board resources are high, target base 
				else if (ship.getResources().getTotal() > AgentUtils.RESOURCE_THRESHOLD) {
					policyNumber = 2;	
				}
				
				// Policy 3: if current action is done (or null), target nearest asteroid
				else if (ship.getCurrentAction() == null || ship.getCurrentAction().isMovementFinished(space)) {
					policyNumber = 3;
				}
				
				// If a policy was selected, get corresponding action
				if(policyNumber != 0) {
					AtkiGAState currentState = new AtkiGAState(space, ship);		
					action = currentPolicy.getCurrentAction(space, ship, currentState, policyNumber);
				}
				
				// Commit action for this ship.
				actions.put(ship.getId(), action);
			} 
			else {
				// TODO: object is Base, need logic here
				actions.put(actionable.getId(), new DoNothingAction());
			}
		}
<<<<<<< HEAD
=======
		
		// Show graphics (if indicated)
		if(AgentUtils.SHOW_GRAPHICS) {
			showGraphics(space);
		}
		
>>>>>>> 1674e9253409737357371c1d6a0a53bc87219e6a
		return actions;
	}

	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		/**
		 * Note: Method unchanged from ExampleGAClient
		 */
		// increment the step counter
		steps++;

		// if the step counter is modulo evaluationSteps, then evaluate this member and move to the next one
		if (steps % EVALUATION_STEPS == 0) {
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

	/**
	 * Initialize the population by either reading it from the file or making a new one from scratch
	 * 
	 * @param space
	 */
	@Override
	public void initialize(Toroidal2DPhysics space) {
		XStream xstream = new XStream();
		xstream.alias("ExampleGAPopulation", AtkiGAPopulation.class);
		graph = AStarGraph.getInstance(space.getHeight(), space.getWidth(), AgentUtils.DEBUG);

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

	/******************
	 * Unused methods *
	 ******************/

	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		// TODO Currently unused
		return null;
	}

	@Override
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {
		/**
		 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
		 * far away from the existing bases.
		 */
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
							if (distance < AgentUtils.BASE_BUYING_DISTANCE) {
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
	
	/********************
	 * Graphics methods *
	 ********************/
	
	@Override
	public Set<SpacewarGraphics> getGraphics() {
		// If graphics are to be drawn, return them.
		if(AgentUtils.SHOW_GRAPHICS)
		{
			HashSet<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();
			graphics.addAll(graphicsToAdd);
			graphicsToAdd.clear();
			return graphics;
		}
		return null;
	}
	
	/**
	 * Shows graphics for this client.
	 * 
	 * @param space physics model
	 */
	private void showGraphics(Toroidal2DPhysics space) {
		graphicsToAdd = new ArrayList<SpacewarGraphics>();
		drawGrid(space);
	    drawSearchTree(space);
	    drawBlockedGrids();
	}
	
	/**
	 * Draws grid used by A* search algorithm.
	 * 
	 * @param space physics model
	 */
	private void drawGrid(Toroidal2DPhysics space) {
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
	}

	/**
	 * Draws search tree from most recent A* search.
	 * 
	 * @param space physics model
	 */
	private void drawSearchTree(Toroidal2DPhysics space) {
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
				graphicsToAdd.add(g);
			}

		}
	}
	
	/**
	 * Draws all grids in A* graph that are blocked by obstacles.
	 */
	private void drawBlockedGrids() {
		if(AgentUtils.SHOW_GRAPHICS) {
			for(Vertex v: graph.getBlockedVertices()) {
				SpacewarGraphics g = new TargetGraphics(BLOCKED_GRID_GRAPHIC_RADIUS, graph.getCentralCoordinate(v));
				graphicsToAdd.add(g);
			}
		}
	}

}

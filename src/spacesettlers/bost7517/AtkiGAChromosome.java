package spacesettlers.bost7517;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;

import spacesettlers.actions.AbstractAction;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Heavily inspired by clients.examples.ExampleGAChromosome. The 
 * chromosome represents an individual in the population and contains 
 * the policy and knowledge base for that individual.
 * 
 * @author Joshua Atkinson, Cameron Bost
 *
 */
public class AtkiGAChromosome {
	private HashMap<AtkiGAState, AbstractAction> policy;
	HashMap<UUID, Ship> asteroidToShipMap;
	private int timeSincePlan = 10;
	private LinkedList<AStarPath> currentSearchTree;
	private LinkedList<Position> pointsToVisit;
	boolean debug = false;
	private AStarGraph graph;
	private AStarPath currentPath;
	HashMap<UUID, Boolean> aimingForBase;
	HashMap<UUID, Boolean> justHitBase;

	/**
	 * Final Variables
	 */
	final double LOW_ENERGY_THRESHOLD = 2750; // #P1 - Lowered 2000 -> 1500
	final double RESOURCE_THRESHOLD = 1000; // #P1 - Raised 500 -> 2000
	final double BASE_BUYING_DISTANCE = 400; // #P1 - raised 200 -> 350

	public AtkiGAChromosome(AStarGraph _graph) {

		policy = new HashMap<AtkiGAState, AbstractAction>();
		asteroidToShipMap = new HashMap<UUID, Ship>();
		graph = _graph;
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
	}

	/**
	 * Returns either the action currently specified by the policy or randomly
	 * selects one if this is a new state
	 * 
	 * @param currentState
	 * @return
	 */
	public AbstractAction getCurrentAction(Toroidal2DPhysics space, Ship myShip, AtkiGAState currentState,
			int policyNumber) {
		drawSearchTree(space);
		if (!policy.containsKey(currentState)) {
			/*
			 * ################### ## Original code ## ###################
			 * 
			 * 
			 * // randomly chose to either do nothing or go to the nearest // asteroid. Note
			 * this needs to be changed in a real agent as it won't learn // much here!
			 * 
			 * 
			 * if (rand.nextBoolean()) { policy.put(currentState, new DoNothingAction()); }
			 * else { //System.out.println("Moving to nearestMineable Asteroid " +
			 * myShip.getPosition() + " nearest " +
			 * currentState.getNearestMineableAsteroid().getPosition());
			 * policy.put(currentState, new MoveToObjectAction(space, myShip.getPosition(),
			 * currentState.getNearestMineableAsteroid())); }
			 * 
			 * 
			 * ############## ## NEW CODE ## ##############
			 * 
			 */
			AbstractAction current = myShip.getCurrentAction();

			if (policyNumber == 1) {
				if (current != null) {
					if (currentState.getEnergySource() != null) {
						// Re-plans every 20 timesteps.
						if (timeSincePlan >= 10) {
							current = null; // resets current stepp to null so it is able to update step
							timeSincePlan = 0; // resets times plan back to 0
							//Will get the current path that a* has chosen
							currentPath = graph.getPathTo(myShip, currentState.getEnergySource(), space);
							currentSearchTree = graph.getSearchTree(); // Returns a search tree
							pointsToVisit = new LinkedList<Position>(currentPath.getPositions()); // Will contain all
																									// the points for a*
						} else {
							timeSincePlan++;
						}

						if (current != null && (myShip.getEnergy() > LOW_ENERGY_THRESHOLD)) // Want to make sure not to
																							// interrupt an a* move that
																							// has not finished yet.
						{
							if (!current.isMovementFinished(space)) // checks if the object has stopped moving
							{
								return current;
							}
						}
						// Call points to create a new action to move to that object.
						if (pointsToVisit != null) {
							if (!pointsToVisit.isEmpty()) {
								Position newPosition = new Position(pointsToVisit.getFirst().getX(),
										pointsToVisit.getFirst().getY());
								policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition));
								//Will display graphics if set to true.
								/*
								if(showMyGraphics)
								{
									graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
									LineGraphics line = new LineGraphics(currentPosition, newPosition, 
											space.findShortestDistanceVector(currentPosition, newPosition));
									line.setLineColor(Color.RED);
									graphicsToAdd.add(line);
								}*/
									
								pointsToVisit.poll();//pops the top
							}
						}

						// Planning currently need to run local search
						policy.put(currentState, new BDSMMoveToObjectAction(space, myShip.getPosition(),
								currentState.getEnergySource()));
					}
				}
			}

			if (policyNumber == 2) {
				if (timeSincePlan >= 10) {
					current = null;
					timeSincePlan = 0;
					if (currentState.getEnergySource() != null)
						currentPath = graph.getPathTo(myShip, currentState.getEnergySource(), space);
					currentSearchTree = graph.getSearchTree();
					pointsToVisit = new LinkedList<Position>(currentPath.getPositions());
				} else {
					timeSincePlan++;
				}

				// Checks that the previous A* action is completed.
				if (current != null) {
					if (!current.isMovementFinished(space)) {
						aimingForBase.put(myShip.getId(), true);
						return current;
					}
				}
				if (pointsToVisit != null) {
					if (!pointsToVisit.isEmpty()) {
						Position newPosition = new Position(pointsToVisit.getFirst().getX(),
								pointsToVisit.getFirst().getY());
						policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition));
						// This will display graphics if they are enabled.
						/*
						 * if(showMyGraphics)
						{
						//LINE!!!
							graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
							LineGraphics line = new LineGraphics(currentPosition, newPosition, 
									space.findShortestDistanceVector(currentPosition, newPosition));
							line.setLineColor(Color.RED);
							graphicsToAdd.add(line);
						}
						*/
						pointsToVisit.poll();//pops the top
						aimingForBase.put(myShip.getId(), true);
					}
				}

				// Run a Local search for base
				policy.put(currentState,
						new BDSMMoveToObjectAction(space, myShip.getPosition(), currentState.getBase()));
				aimingForBase.put(myShip.getId(), true);

				if (debug) {
					System.out.println("<Action Declaration> - Deposit (" + myShip.getResources().getTotal() + ")");
				}
			}
			if (policyNumber == 3) {
				if (currentState.nearestMineableAsteroid != null) {
					System.out.println("<DEBUG> - calling policy inside of policy 3");
					asteroidToShipMap.put(currentState.nearestMineableAsteroid.getId(), myShip);
					// Re-plans every 10 steps.
					if (timeSincePlan >= 10) {
						current = null;
						timeSincePlan = 0;
						currentPath = graph.getPathTo(myShip, currentState.nearestMineableAsteroid, space);
						currentSearchTree = graph.getSearchTree();
						pointsToVisit = new LinkedList<Position>(currentPath.getPositions());
					} else {
						timeSincePlan++;
					}
					// Checks to make sure that the current A* is move is finished.
					if (current != null) {
						if (!current.isMovementFinished(space)) {
							return current;
						}
					}
					System.out.println("HERE");
					//Will create actions for A* points.
					if (pointsToVisit != null)
					{
						if(!pointsToVisit.isEmpty())
						{	
							//Will assign a Position variable with the positions.
							Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
							//Create the action to move to the A* position.
							//newAction = new BDSMMoveAction(space, myShip.getPosition(), newPosition, currentState.nearestMineableAsteroid);
							policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition, currentState.nearestMineableAsteroid));
							//This will displayed graphics if true.
							/*if(showMyGraphics)
							{
								graphicsToAdd.add(new StarGraphics(3, Color.RED, newPosition));
								LineGraphics line = new LineGraphics(currentPosition, newPosition, 
										space.findShortestDistanceVector(currentPosition, newPosition));
								line.setLineColor(Color.RED);
								graphicsToAdd.add(line);
							}*/
							
							pointsToVisit.poll();//pops the top
						}
					}

					policy.put(currentState,
							new BDSMMoveToObjectAction(space, myShip.getPosition(),
									currentState.nearestMineableAsteroid,
									currentState.nearestMineableAsteroid.getPosition().getTranslationalVelocity()));
				}
			}
		}
		return policy.get(currentState);
	}

	void drawSearchTree(Toroidal2DPhysics space) {
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
				// graphicsToAdd.add(g);
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

	/**
	 * Update time step for plan.
	 */
	public void currentPolicyUpdateTime() {
		timeSincePlan = timeSincePlan + 1;
	}
}

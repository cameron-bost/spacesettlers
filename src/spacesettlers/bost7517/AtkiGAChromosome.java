package spacesettlers.bost7517;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

import spacesettlers.actions.AbstractAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Heavily inspired by clients.examples.ExampleGAChromosome. The 
 * chromosome represents an individual in the population and contains 
 * the policy and knowledge base for that individual.
 * 
 * @author Joshua Atkinson, Cameron Bost
 * @version 0.3
 */
public class AtkiGAChromosome {
	
	/**
	 * Maps states to actions for easy lookup.
	 */
	@XStreamOmitField
	private HashMap<AtkiGAState, AbstractAction> policy;
	
	/**
	 * Tracks asteroids being targeted, indicates which ship.
	 */
	@XStreamOmitField
	private HashMap<UUID, Ship> asteroidToShipMap;
	
	/**
	 * Number of time-steps since last planning routine.
	 */
	@XStreamOmitField
	private int timeSincePlan = AgentUtils.PLAN_INTERVAL;
	
	/**
	 * Last search tree produced by A* search
	 */
	@XStreamOmitField
	private LinkedList<AStarPath> currentSearchTree;
	
	/**
	 * Current planned route, stored as sequence of positions.
	 */
	@XStreamOmitField
	private LinkedList<Position> pointsToVisit;
	
	/**
	 * Current path being followed
	 */
	@XStreamOmitField
	private AStarPath currentPath;
	
	/**
	 * Given in default reflex agent: tracks whether a ship is aiming for base
	 */
	@XStreamOmitField
	private HashMap<UUID, Boolean> aimingForBase;
	/**
	 * Given in default agent: tracks if a ship has just struck the base
	 * TODO check if base collision is still handled correctly (probably not)
	 */
	@XStreamOmitField
	private HashMap<UUID, Boolean> justHitBase;
	
	/**
	 * Current action being followed by ship
	 * TODO get rid of this
	 */
	
	@XStreamOmitField
	private AbstractAction currentAction;
	
	private double fitnessValue = 1;

	/**
	 * Value that is being optimized.
	 */
	
	/**
	 * Gene 1: Optimal total distance from ship to asteroid to base.
	 */
	private int optimalDistance;
	private static final double mStep_optimalDistance = 0.15;
	private static final int upperBound_optimalDistance = 15000;
	
	/**
	 * Gene 2: Low energy amount
	 */
	private int lowEnergyThreshold = 3000;
	private static final double mStep_lowEnergyThreshold = 0.15;
	private static final int upperBound_lowEnergyThreshold = 5000;
	
	/**
	 * Gene 2: Resource Capacity Before Base
	 */
	private int resourceThreshold;
	private static final double mStep_resourceThreshold = 0.15;
	private static final int upperBound_resourceThreshold = 2000;
	
	/**
	 * Chromosome constructor, global graph object is required argument.
	 * @param _random 
	 * 
	 * @param _graph Global A* graph object
	 */
	public AtkiGAChromosome(Random _random) {
		initFields();
		setRandomGeneValues(_random);
	}
	
	private void setRandomGeneValues(Random random) {
		optimalDistance = 10;
		lowEnergyThreshold = (random.nextInt(upperBound_lowEnergyThreshold-2000)+2000);
		resourceThreshold = (random.nextInt(upperBound_resourceThreshold)+1);
		if(AgentUtils.DEBUG) {
			System.out.println("Creating new chromosome w/:"
					+ "\n\toptimalDistance="+optimalDistance
					+ "\n\tlowEnergyThreshold="+lowEnergyThreshold
					+ "\n\tresourceThreshold="+resourceThreshold);
		}
	}

	private AtkiGAChromosome(int _optimalDistance, int _lowEnergyThreshold, int _resourceThreshold) {
		initFields();
		
		// Set initial gene values
		optimalDistance = _optimalDistance;
		lowEnergyThreshold = _lowEnergyThreshold;
		resourceThreshold = _resourceThreshold;
	}

	/**
	 * Determines current action by consulting policy based on current state.
	 * 
	 * @param space physics model
	 * @param myShip ship for which action is being determined
	 * @param currentState current state
	 * @param policyNumber policy number being employed
	 * @return Action for ship to perform during this time-step.
	 */
	public AbstractAction getCurrentAction(Toroidal2DPhysics space, Ship myShip) {
		// If new game, some variables will be null, so we reset them.
		if(policy == null) {
			if(AgentUtils.DEBUG) {
				System.out.println("<Chromosome> - Initializing fields.");
			}
			initFields();
		}
		
		// Init state
		AtkiGAState currentState = new AtkiGAState(space, myShip, optimalDistance);
		
		// If policy does not contain this state, determine correct action then add to policy
		if (!policy.containsKey(currentState)) {
			if(AgentUtils.DEBUG) {
				System.out.println("<Chromosome> - Policy does not contain state.");
			}
			// Default action is currentAction
			currentAction = myShip.getCurrentAction();
//			policy.put(currentState, currentAction);
			// Policy 1: if energy is low, target energy beacon
			if ((myShip.getEnergy() < lowEnergyThreshold)) {
				if(AgentUtils.DEBUG) {
					System.out.println("<Chromosome> - Policy 1: Energy is low, getting energy.");
				}
				if (currentState.getEnergySource() != null) {
					checkForPlan(space, myShip, currentState.getEnergySource());

					// Want to make sure not to interrupt an a* move that has not finished yet.
					if (currentAction != null && !currentAction.isMovementFinished(space)) // checks if the object has stopped moving
					{
						if(AgentUtils.DEBUG) {
							System.out.println("<Chromosome> - A* is not finished yet, continuing action...");
						}
						return currentAction;
					}
					// Call points to create a new action to move to that object.
					if (pointsToVisit != null) {
						if (!pointsToVisit.isEmpty()) {
							if(AgentUtils.DEBUG) {
								System.out.println("<Chromosome> - Adding next position in path to ship.");
							}
							Position newPosition = new Position(pointsToVisit.getFirst().getX(),
									pointsToVisit.getFirst().getY());
							policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition));
								
							pointsToVisit.poll();//pops the top
						}
					}

					// Planning currently need to run local search
					policy.put(currentState, new BDSMMoveToObjectAction(space, myShip.getPosition(),
							currentState.getEnergySource()));
				}
			}

			// Policy 2: if on-board resources are high, target base 
			else if (myShip.getResources().getTotal() > resourceThreshold) {
				if(AgentUtils.DEBUG) {
					System.out.println("<Chromosome> - Policy 2: We hefty on $$$, going home.");
				}
				checkForPlan(space, myShip, currentState.getBase());

				// Checks that the previous A* action is completed.
				if (currentAction != null) {
					if (!currentAction.isMovementFinished(space)) {
						if(AgentUtils.DEBUG) {
							System.out.println("<Chromosome> - A* is not finished yet, continuing action...");
						}
						aimingForBase.put(myShip.getId(), true);
						return currentAction;
					}
				}
				if (pointsToVisit != null) {
					if (!pointsToVisit.isEmpty()) {
						if(AgentUtils.DEBUG) {
							System.out.println("<Chromosome> - Adding next position in path to ship.");
						}
						Position newPosition = new Position(pointsToVisit.getFirst().getX(),
								pointsToVisit.getFirst().getY());
						policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition));
						pointsToVisit.poll();//pops the top
						aimingForBase.put(myShip.getId(), true);
					}
				}

				// Run a Local search for base
				policy.put(currentState,
						new BDSMMoveToObjectAction(space, myShip.getPosition(), currentState.getBase()));
				aimingForBase.put(myShip.getId(), true);

				if (AgentUtils.DEBUG) {
					System.out.println("<Action Declaration> - Deposit (" + myShip.getResources().getTotal() + ")");
				}
			}
			
			// Policy 3: if current action is done (or null), target nearest asteroid
			else if (myShip.getCurrentAction() == null || myShip.getCurrentAction().isMovementFinished(space)) {
				if(AgentUtils.DEBUG) {
					System.out.println("<Chromosome> - Policy 3: previous action is not yet finished");
				}
				if (currentState.getBestMineableAsteroid() != null) {
					asteroidToShipMap.put(currentState.getBestMineableAsteroid().getId(), myShip);
					checkForPlan(space, myShip, currentState.getBestMineableAsteroid());
					// Checks to make sure that the current A* is move is finished.
					if (currentAction != null) {
						if (!currentAction.isMovementFinished(space)) {
							return currentAction;
						}
					}
					// Will create actions for A* points.
					if (pointsToVisit != null)
					{
						if(!pointsToVisit.isEmpty())
						{	
							//Will assign a Position variable with the positions.
							Position newPosition = new Position(pointsToVisit.getFirst().getX(),pointsToVisit.getFirst().getY());
							//Create the action to move to the A* position.
							policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition, currentState.getBestMineableAsteroid()));
							
							pointsToVisit.poll();//pops the top
						}
					}

					policy.put(currentState,
							new BDSMMoveToObjectAction(space, myShip.getPosition(),
									currentState.getBestMineableAsteroid(),
									currentState.getBestMineableAsteroid().getPosition().getTranslationalVelocity()));
				}
			}
		}
		return policy.get(currentState);
	}
	/**
	 * Re-initilazie policys needed values.
	 * This will take in a graph 
	 * @param graph
	 */
	public void initFields()
	{
		policy = new HashMap<AtkiGAState, AbstractAction>();
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
	}
	
	void checkForPlan(Toroidal2DPhysics space, Ship myShip, AbstractObject target) {
		if (timeSincePlan >= AgentUtils.PLAN_INTERVAL) {
			currentAction = null;
			timeSincePlan = 0;
			currentPath = AStarGraph.getPathTo(myShip, target, space);
			currentSearchTree = AStarGraph.getSearchTree();
			pointsToVisit = currentPath.getPositions();
		} else {
			timeSincePlan++;
		}
	}
	
	/**
	 * Accessor method used in client class to retrieve A* search tree.
	 * 
	 * @return Most recent tree generated by A* search.
	 */
	public LinkedList<AStarPath> getCurrentSearchTree(){
		return currentSearchTree;
	}

	/**
	 * Accessor method for path being followed by agent.
	 * @return Current path being followed by agent.
	 */
	public AStarPath getCurrentPath() {
		return currentPath;
	}

	/**
	 * Update time step for plan.
	 * Note: unchanged from ExampleGAChromosome
	 */
	public void currentPolicyUpdateTime() {
		timeSincePlan = timeSincePlan + 1;
	}

	/**
	 * Mutates this chromosome. Each gene has a 50% chance of mutating,
	 * with equal weight on increase/decrease mutation.
	 */
	public void mutate(Random random) {
		// Mutate optimal distance
		double r = random.nextDouble();
		optimalDistance *= (double)(1.15 + (r <= 0.5 ? r <= 0.25 ? -1 : 1 : 0) * mStep_optimalDistance);
		if(optimalDistance > upperBound_optimalDistance) {
			optimalDistance = upperBound_optimalDistance;
		}
		
		// Mutate low energy threshold
		r = random.nextDouble();
		lowEnergyThreshold *= (double)(1.0+(r <= 0.5 ? r <= 0.25 ? -1 : 1 : 0) * mStep_lowEnergyThreshold);
		if(lowEnergyThreshold > upperBound_lowEnergyThreshold) {
			lowEnergyThreshold = (random.nextInt(upperBound_lowEnergyThreshold-3000)+3000);
		}
		
		// Mutate resource threshold
		r = random.nextDouble();
		resourceThreshold *= (double)(1.0+(r <= 0.5 ? r <= 0.25 ? -1 : 1 : 0) * mStep_resourceThreshold);
		if(resourceThreshold > upperBound_resourceThreshold) {

			resourceThreshold = (random.nextInt(upperBound_resourceThreshold)+1);

		}
	}

	/**
	 * Performs proportional crossover based 
	 * on parent fitness values.
	 * 
	 * @param p1 First parent
	 * @param p2 Second parent
	 * @param random Random number generator
	 * @return Child of the two parents
	 */
	public static AtkiGAChromosome doCrossover(AtkiGAChromosome p1, AtkiGAChromosome p2, Random random) {
		double prob = p1.fitnessValue / (p1.fitnessValue + p2.fitnessValue);
		int optimalDistance = (random.nextDouble() <= prob ? p1.optimalDistance : p2.optimalDistance);
		int lowEnergyThreshold = (random.nextDouble() <= prob ? p1.lowEnergyThreshold : p2.lowEnergyThreshold);
		int resourceThreshold = (random.nextDouble() <= prob ? p1.resourceThreshold : p2.resourceThreshold);
		return new AtkiGAChromosome(optimalDistance, lowEnergyThreshold, resourceThreshold);
	}
	
	public void setFitness(double _fitness) {
		fitnessValue = _fitness;
	}
}

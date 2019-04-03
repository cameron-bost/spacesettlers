package spacesettlers.bost7517;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
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
	 * A* Graph, used for path-finding
	 */
	@XStreamOmitField
	private AStarGraph graph;
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

	/**
	 * Value that is being optimized.
	 */
	
	/**
	 * this value is TBD
	 */
	private double optimalDistance;
	
	/**
	 * Chromosome constructor, global graph object is required argument.
	 * 
	 * @param _graph Global A* graph object
	 */
	public AtkiGAChromosome(AStarGraph _graph) {
		graph = _graph;
		policy = new HashMap<AtkiGAState, AbstractAction>();
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
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
	public AbstractAction getCurrentAction(Toroidal2DPhysics space, Ship myShip, AtkiGAState currentState,
			int policyNumber) {
		// If policy does not contain this state, determine correct action then add to policy

		if (!policy.containsKey(currentState)) {
			if(AgentUtils.DEBUG) {
				System.out.println("<Chromosome.getAction> - calling policy #"+policyNumber);
			}
			
			// Default action is currentAction
			currentAction = myShip.getCurrentAction();
			
			// Policy 1: Go to energy source
			if (policyNumber == 1) {
				if (currentState.getEnergySource() != null) {
					checkForPlan(space, myShip, currentState.getEnergySource());

					// Want to make sure not to interrupt an a* move that has not finished yet.
					if (currentAction != null && !currentAction.isMovementFinished(space)) // checks if the object has stopped moving
					{
						return currentAction;
					}
					// Call points to create a new action to move to that object.
					if (pointsToVisit != null) {
						if (!pointsToVisit.isEmpty()) {
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

			// Policy 2: Go to base (dump resources)
			if (policyNumber == 2) {
				checkForPlan(space, myShip, currentState.getBase());

				// Checks that the previous A* action is completed.
				if (currentAction != null) {
					if (!currentAction.isMovementFinished(space)) {
						aimingForBase.put(myShip.getId(), true);
						return currentAction;
					}
				}
				if (pointsToVisit != null) {
					if (!pointsToVisit.isEmpty()) {
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
			
			// Policy 3: Go to best asteroid
			if (policyNumber == 3) {
				
				if (currentState.getNearestMineableAsteroid() != null) {
					asteroidToShipMap.put(currentState.getNearestMineableAsteroid().getId(), myShip);
					checkForPlan(space, myShip, currentState.getNearestMineableAsteroid());
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
							policy.put(currentState, new BDSMMoveAction(space, myShip.getPosition(), newPosition, currentState.getNearestMineableAsteroid()));
							
							pointsToVisit.poll();//pops the top
						}
					}

					policy.put(currentState,
							new BDSMMoveToObjectAction(space, myShip.getPosition(),
									currentState.getNearestMineableAsteroid(),
									currentState.getNearestMineableAsteroid().getPosition().getTranslationalVelocity()));
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
	public void setPolicy(AStarGraph graph)
	{
		policy = new HashMap<AtkiGAState, AbstractAction>();
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		justHitBase = new HashMap<UUID, Boolean>();
		this.graph = graph;
	}
	
	void checkForPlan(Toroidal2DPhysics space, Ship myShip, AbstractObject target) {
		if (timeSincePlan >= AgentUtils.PLAN_INTERVAL) {
			currentAction = null;
			timeSincePlan = 0;
			currentPath = graph.getPathTo(myShip, target, space);
			currentSearchTree = graph.getSearchTree();
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
}

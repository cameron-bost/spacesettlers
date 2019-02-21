package spacesettlers.bost7517.astar;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

import spacesettlers.bost7517.AgentUtils;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Represents a gridded graph of the SpaceSettlersSimulator environment 
 * used for AStar path finding.
 * 
 * @author Cameron Bost
 * @version 0.2
 */
public class AStarGraph {
	
	/**Vertices represented as 2D grid*/
	private Vertex[][] vMtx;
	
	/**Width/Height of vertex matrix*/
	private int mtxCols, mtxRows;
	
	/**Size of one grid unit*/
	public static final int GRID_SIZE = Asteroid.MAX_ASTEROID_RADIUS*2;
	
	/**Indicates whether to perform debug processes*/
	private boolean debug;
	
	/**Search tree from most recent plan*/
	private LinkedList<AStarPath> searchTree;
	
	/**
	 * Constructor for graph. Window parameters and asteroid radius are required.
	 * 
	 * @param windowHeight height of simulated environment
	 * @param windowWidth width of simulated environment
	 * @param _debug indicates debug status
	 */
	public AStarGraph(int windowHeight, int windowWidth, boolean _debug){
		mtxCols = windowWidth / GRID_SIZE;
		mtxRows = windowHeight / GRID_SIZE;
		debug = _debug;
		searchTree = new LinkedList<>();
		initMatrix();
	}
	
	/**
	 * Initializes the vertex matrix, connects edges
	 */
	private void initMatrix() {
		// Init vertex matrix
		vMtx = new Vertex[mtxRows][mtxCols];
		for(int row = 0; row < mtxRows; row++) 
			for(int col = 0; col < mtxCols; col++) 
				vMtx[row][col] = new Vertex(col, row);
			
		
		// For each vertex, connect the four cardinal directions
		// TODO: Can we do all 8 cardinal directions?
		for(int row = 0; row < mtxRows; row++) {
			for(int col = 0; col < mtxCols; col++) {
				Vertex thisVertex = vMtx[row][col];
				int up = (row == 0 ? mtxRows - 1 : row - 1);
				thisVertex.addEdge(vMtx[up][col]);
				int down = (row+1) % mtxRows;
				thisVertex.addEdge(vMtx[down][col]);
				int left = (col == 0 ? mtxCols - 1 : col - 1);
				thisVertex.addEdge(vMtx[row][left]);
				int right = (col+1) % mtxCols;
				thisVertex.addEdge(vMtx[row][right]);
			}
		}
	}
	
	/**
	 * Calculates a sequence of positions to travel to in order for the ship to reach the target position.
	 * 
	 * @param ship The ship checking for a path
	 * @param target Target object
	 * @param space {@link Toroidal2DPhysics} model
	 * @return AStarPath object containing sequence of positions to travel to for optimal 
	 */
	public AStarPath getPathTo(Ship ship, AbstractObject target, Toroidal2DPhysics space) {
		// Debug: clear previous search tree
		if(debug) {
			searchTree.clear();
		}
		// Clear heuristic values
		clearHeuristics();
		// Get vertex containing ship, mark as start
		Vertex vShip = getVertex(ship.getPosition());
		vShip.markStart();
		// Get vertex containing target, mark as target
		Vertex vTarget = getVertex(target.getPosition());
		vTarget.markEnd();
		
		// Set heuristic values
		setHeuristics(space, ship, target);
		
		// Init priority queue
		PriorityQueue<AStarPath> q = new PriorityQueue<>();
		
		// Add start vertex to queue
		q.add(AStarPath.makePath(this, vShip));
		
		// While queue is not empty and found is false
		boolean found = false;
		AStarPath bestSoFar = null;
		while(!found && !q.isEmpty()) {
			// Get next value from queue
			AStarPath thisPath = q.poll();
			// Debug: add path to search tree
			if(debug) {
				searchTree.add(thisPath);
			}
			if(thisPath.getCurrentVertex().isEnd()) {
				bestSoFar = thisPath;
				break;
			}
			// For each child, create a new path that moves to it, add to queue
			for(Vertex child: thisPath.getCurrentVertex().getEdges()) {
				AStarPath childPath = AStarPath.duplicatePath(this, thisPath);
				childPath.addVertex(child);
				q.add(childPath);
			}
		}
		q.clear();
		// Cleanup
		vTarget.markNotEnd();
		vShip.markNotStart();
		return bestSoFar;
	}
	
	/**
	 * Resets all heuristic values to 0
	 */
	private void clearHeuristics() {
		for(int r = 0; r < mtxRows; r++) {
			for(int c = 0; c < mtxCols; c++) {
				vMtx[r][c].clearHValue();
			}
		}
	}

	/**
	 * Computes heuristic values for all vertices, marking vertices 
	 * with obstacles in them with max heuristic values.
	 * 
	 * @param space Toroidal2DPhysics model for the game
	 * @param ship Ship checking for path
	 * @param targetPosition Target's position
	 */
	private void setHeuristics(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		// Load all current obstructions (i.e. objects that are dangerous to the ship)
		Set<AbstractObject> obstructions = new HashSet<>();
		for(AbstractObject obj: space.getAllObjects()) {
			// If object is viewed as an obstacle and is NOT the target, it is an obstruction
			if(AgentUtils.isObstacle(ship, obj) && !obj.getId().equals(target.getId())) {
				obstructions.add(obj);
			}
		}
		
		// Set heuristic values for all vertices
		for(int row = 0; row < mtxRows; row++) {
			for(int col = 0; col < mtxCols; col++) {
				// Get vertex at this position
				Vertex v = vMtx[row][col];
				Position posV = getCentralCoordinate(v);

				// Set heuristic value
				double heuristicValue = 0.0;
				// If target node, 
				if(v.isEnd()) {
					v.setHValue(0.0);
				}
				if(gridIsClearOfObstacles(space, obstructions, posV)) {
					// If no obstacles in this grid, set heuristic to distance between central point and the target
					heuristicValue = space.findShortestDistance(posV, target.getPosition());
				}
				// If obstruction exists, set H value to max integer value
				else {
					heuristicValue = Double.MAX_VALUE;
				}
				v.setHValue(heuristicValue);
			}
		}
		obstructions.clear();
	}
	
	/**
	 * Checks if a vertex contains any obstacles by performing
	 * two vertical sweeps with radius GRID_SIZE/4. Small pockets are 
	 * left unchecked, but are too small to contain obstacles.
	 * 
	 * @param space physics model for the game
	 * @param obstructions set of obstructions to check for
	 * @param pc central coordinate of vertex to check for obstacles
	 * @return whether this vertex is clear of obstacles (true -> no obstacles)
	 */
	private boolean gridIsClearOfObstacles(Toroidal2DPhysics space, Set<AbstractObject> obstructions, Position pc) {
		int quarterG = GRID_SIZE/4;
		Position pi1 = new Position(pc.getX()+quarterG, pc.getY() - quarterG);
		Position pf1 = new Position(pc.getX()+quarterG, pc.getY() + quarterG);
		Position pi2 = new Position(pc.getX()-quarterG, pc.getY() - quarterG);
		Position pf2 = new Position(pc.getX()-quarterG, pc.getY() + quarterG);
		return space.isPathClearOfObstructions(pi1, pf1, obstructions, quarterG)
				&& space.isPathClearOfObstructions(pi2, pf2, obstructions, quarterG);
	}
	
	/**
	 * Gets the central coordinate of a vertex as a Position object
	 * @param v Vertex to find central coordinate of
	 * @return Position object representing the center of a vertex grid square
	 */
	Position getCentralCoordinate(Vertex v) {
		return new Position(v.getMtxColumn()*GRID_SIZE + (GRID_SIZE/2), v.getMtxRow()*GRID_SIZE + (GRID_SIZE/2));
	}
	
	/**
	 * Gets the corresponding vertex for some position on the screen.
	 * 
	 * @param p position to get vertex for
	 * @return Vertex containing the position
	 */
	Vertex getVertex(Position p) {
		int row = (int) (p.getY() / mtxRows);
		int col = (int) (p.getX() / mtxCols);
		return vMtx[row][col];
	}

	/**
	 * Gets the most recently planned search tree
	 * @return Search tree from most recent call to getPathTo
	 */
	public LinkedList<AStarPath> getSearchTree(){
		return searchTree;
	}
}

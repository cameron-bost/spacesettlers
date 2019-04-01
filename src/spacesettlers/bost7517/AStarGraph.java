package spacesettlers.bost7517;

import java.util.HashMap;
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
 * V0.3 - Converted to singleton.
 * 
 * @author Cameron Bost
 * @version 0.3
 */
public class AStarGraph {
	
	/**Singleton instance*/
	private static AStarGraph _graph = null;
	
	/**
	 * Retrieves instance of graph class. If instance has not been 
	 * initialized, parameter values are used to create it.
	 * 
	 * @param windowHeight Height of window
	 * @param windowWidth Width of window
	 * @param _debug Debug flag
	 * @return Singleton instance of AStarGraph
	 */
	public static AStarGraph getInstance(int windowHeight, int windowWidth, boolean _debug) {
		if(_graph == null) {
			_graph = new AStarGraph(windowHeight, windowWidth, _debug);
		}
		return _graph;
	}
	
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
	private LinkedList<GBFSPath> searchTreeGBFS;
	
	/**Vertices marked as obstacles*/
	private LinkedList<Vertex> blockedGrids;
	
	/**
	 * Constructor for graph. Window parameters and asteroid radius are required.
	 * 
	 * @param windowHeight height of simulated environment
	 * @param windowWidth width of simulated environment
	 * @param _debug indicates debug status
	 */
	private AStarGraph(int windowHeight, int windowWidth, boolean _debug){
		mtxCols = windowWidth / GRID_SIZE;
		mtxRows = windowHeight / GRID_SIZE;
		debug = _debug;
		searchTree = new LinkedList<>();
		searchTreeGBFS = new LinkedList<>();
		blockedGrids = new LinkedList<>();
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
		if(debug) {
			System.out.println("<AStarGraph.getPathTo> - Setting heuristics...");
		}
		setHeuristics(space, ship, target);
		if(debug) {
			System.out.println("<AStarGraph.getPathTo> - Finished setting heuristics...");
		}
		// Init priority queue
		PriorityQueue<AStarPath> fringeQ = new PriorityQueue<>();
		HashMap<Vertex, Boolean> closed = new HashMap<>();
		// Add start vertex to queue
		fringeQ.add(AStarPath.makePath(this, vShip));
		// While queue is not empty and found is false
		boolean found = false;
		AStarPath bestSoFar = null;
		int debugDepthCount = 0;
		int lowestCostSoFar = Integer.MAX_VALUE;
		while(!found && !fringeQ.isEmpty()) {
			// Get next value from queue
			AStarPath thisPath = fringeQ.poll();
			if(debug){
				lowestCostSoFar = Math.min(thisPath.getQueueValue(), lowestCostSoFar);
			}
			if(debug && debugDepthCount++ % 1000 == 0) {
//				debugDepthCount++;
				// Debug print code
//				System.out.println("*****************************");
//				System.out.println("AStarGraph.getPathTo> - At option #"+debugDepthCount+", cost="+thisPath.getQueueValue());
//				System.out.println("Matrix size: rows="+mtxRows+",cols="+mtxCols+",searchTreeSize="+searchTree.size());
//				System.out.println("Ship: ("+vShip.getMtxColumn()+","+vShip.getMtxRow()+"), Target: ("+vTarget.getMtxColumn()+","+vTarget.getMtxRow()+")");
//				thisPath.print();
//				System.out.println("Lowest cost so far: "+lowestCostSoFar);
//				System.out.println("*****************************");
				if(debugDepthCount > 200000) {
					return null;
				}
			}
			// Debug: add path to search tree
			if(debug) {
				searchTree.add(thisPath);
			}
			
			// If vertex is not already closed
			if(!closed.containsKey(thisPath.getCurrentVertex())) {
				closed.put(thisPath.getCurrentVertex(), true);
				// Check for goal
				if(thisPath.getCurrentVertex().isEnd() || thisPath.getCurrentVertex().getHValue() == 0) {
					if(debug) {
						System.out.println("<AStarGraph.getPathTo> - FOUND OPTIMAL PATH");
						thisPath.print();
					}
					bestSoFar = thisPath;
					found = true;
					break;
				}
				// For each child, create a new path that moves to it, add to queue
				for(Vertex child: thisPath.getCurrentVertex().getEdges()) {
					// Only add child if it is not already closed
					if(!closed.containsKey(child)) {
						AStarPath childPath = AStarPath.duplicatePath(this, thisPath);
						childPath.addVertex(child);
						fringeQ.add(childPath);
					}
				}
			}
		}
		fringeQ.clear();
		closed.clear();
		// Cleanup
		vTarget.markNotEnd();
		vShip.markNotStart();
		return bestSoFar;
	}
	
	public GBFSPath getPathToGBFS(Ship ship, AbstractObject target, Toroidal2DPhysics space) {
		// Debug: clear previous search tree
		if(debug) {
			searchTreeGBFS.clear();
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
		if(debug) {
			System.out.println("<AStarGraph.getPathToGBFS> - Setting heuristics...");
		}
		setHeuristics(space, ship, target);
		if(debug) {
			System.out.println("<AStarGraph.getPathToGBFS> - Finished setting heuristics...");
		}
		// Init priority queue
		PriorityQueue<GBFSPath> frontierQ = new PriorityQueue<>();
		HashMap<Vertex, Boolean> explored = new HashMap<>();
		// Add start vertex to queue
		frontierQ.add(GBFSPath.makePath(this, vShip));
		// While queue is not empty and found is false
		boolean found = false;
		GBFSPath bestSoFar = null;
		int debugDepthCount = 0;
		int lowestCostSoFar = Integer.MAX_VALUE;
		while(!found && !frontierQ.isEmpty()) {
			// Get next value from queue
			GBFSPath thisPath = frontierQ.poll();
			if(debug){
				lowestCostSoFar = Math.min(thisPath.getQueueValue(), lowestCostSoFar);
			}
			if(debug && debugDepthCount++ % 1000 == 0) {
//						debugDepthCount++;
				// Debug print code
//						System.out.println("*****************************");
//						System.out.println("AStarGraph.getPathTo> - At option #"+debugDepthCount+", cost="+thisPath.getQueueValue());
//						System.out.println("Matrix size: rows="+mtxRows+",cols="+mtxCols+",searchTreeSize="+searchTree.size());
//						System.out.println("Ship: ("+vShip.getMtxColumn()+","+vShip.getMtxRow()+"), Target: ("+vTarget.getMtxColumn()+","+vTarget.getMtxRow()+")");
//						thisPath.print();
//						System.out.println("Lowest cost so far: "+lowestCostSoFar);
//						System.out.println("*****************************");
				if(debugDepthCount > 200000) {
					return null;
				}
			}
			// Debug: add path to search tree
			if(debug) {
				searchTreeGBFS.add(thisPath);
			}
			
			// If vertex is not already closed
			if(!explored.containsKey(thisPath.getCurrentVertex())) {
				explored.put(thisPath.getCurrentVertex(), true);
				// Check for goal
				if(thisPath.getCurrentVertex().isEnd() || thisPath.getCurrentVertex().getHValue() == 0) {
					if(debug) {
						System.out.println("<AStarGraph.getPathTo> - FOUND OPTIMAL PATH");
						thisPath.print();
					}
					bestSoFar = thisPath;
					found = true;
					break;
				}
				// For each child, create a new path that moves to it, add to queue
				for(Vertex child: thisPath.getCurrentVertex().getEdges()) {
					// Only add child if it is not already closed
					if(!explored.containsKey(child)) {
						GBFSPath childPath = GBFSPath.duplicatePath(this, thisPath);
						childPath.addVertex(child);
						frontierQ.add(childPath);
					}
				}
			}
		}
		frontierQ.clear();
		explored.clear();
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
		int blockedCount = 0;
		blockedGrids = new LinkedList<>();
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
					blockedCount++;
					heuristicValue = Double.MAX_VALUE;
					blockedGrids.add(v);
				}
				v.setHValue(heuristicValue);
			}
		}
		obstructions.clear();
		if(debug){
			System.out.println("<AStarGraph.setHeuristics> - Blocked "+blockedCount+" grids due to obstructions");
		}
	}
	
	/**
	 * Checks if a vertex contains any obstacles.
	 * Code inspired by Toroidal2DPhysics.isLocationFree()
	 * 
	 * @param space physics model for the game
	 * @param obstructions set of obstructions to check for
	 * @param pc central coordinate of vertex to check for obstacles
	 * @return whether this vertex is clear of obstacles (true -> no obstacles)
	 */
	private boolean gridIsClearOfObstacles(Toroidal2DPhysics space, Set<AbstractObject> obstructions, Position pc) {
		int radius = GRID_SIZE*3/4;
		for (AbstractObject object : obstructions) {
			// fixed bug where it only checked radius and not diameter
			if (space.findShortestDistanceVector(object.getPosition(), pc)
					.getMagnitude() <= (radius + (2 * object.getRadius()))) {
				return false;
			}
		}
		return true;
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
		int row = Math.min((int)p.getY() / GRID_SIZE, mtxRows - 1);
		int col = Math.min((int)p.getX() / GRID_SIZE, mtxCols - 1);
		try {
			return vMtx[row][col];
		}
		catch(ArrayIndexOutOfBoundsException e) {
			System.out.println("Failed to get vertex for position: "+p);
			System.out.println("Row:"+row+",Col:"+col);
			System.out.println("Matrix: ["+mtxRows+"]["+mtxCols+"] @ "+GRID_SIZE);
//			System.exit(-1);
			return null;
		}
	}

	/**
	 * Gets the most recently planned search tree
	 * @return Search tree from most recent call to getPathTo
	 */
	public LinkedList<AStarPath> getSearchTree(){
		return searchTree;
	}
	
	public LinkedList<GBFSPath> getSearchTreeGBFS(){
		return searchTreeGBFS;
	}

	public LinkedList<Vertex> getBlockedVertices() {
		return blockedGrids;
	}
}
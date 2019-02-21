package spacesettlers.bost7517.astar;

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
	private int gridUnitSize;
	
	
	/**
	 * Constructor for graph. Window parameters and asteroid radius are required.
	 * 
	 * @param windowHeight height of simulated environment
	 * @param windowWidth width of simulated environment
	 * @param asteroidRadius max asteroid radius
	 */
	public AStarGraph(int windowHeight, int windowWidth, int asteroidRadius){
		int gridSize = asteroidRadius * 2;
		mtxCols = windowWidth / gridSize;
		mtxRows = windowHeight / gridSize;
		gridUnitSize = gridSize;
		
		// Init vertex matrix
		vMtx = new Vertex[mtxRows][mtxCols];
		for(int row = 0; row < mtxRows; row++) 
			for(int col = 0; col < mtxCols; col++) 
				vMtx[row][col] = new Vertex(col, row);
			
		
		// TODO: For each vertex, connect the four cardinal directions
		// TODO: Can we do all 8 cardinal directions?
		for(int row = 0; row < mtxRows; row++) {
			for(int col = 0; col < mtxCols; col++) {
			}
		}
	}
	
	/**
	 * Calculates a sequence of positions to travel to in order for the ship to reach the target position.
	 * 
	 * @param shipPosition Ship's current position
	 * @param targetPosition Target's current position
	 * @param space {@link Toroidal2DPhysics} model
	 * @return AStarPath object containing sequence of positions to travel to for optimal 
	 */
	public AStarPath getPathTo(Position shipPosition, Position targetPosition, Toroidal2DPhysics space) {
		return null;
	}
	
	void addEdge(Vertex v1, Vertex v2) {
		// TODO: Implement
	}
	
	/**
	 * Gets the central coordinate of a vertex as a Position object
	 * @param v Vertex to find central coordinate of
	 * @return Position object representing the center of a vertex grid square
	 */
	Position getCentralCoordinate(Vertex v) {
		return new Position(v.getMtxColumn()*gridUnitSize + (gridUnitSize/2), v.getMtxRow()*gridUnitSize + (gridUnitSize/2));
	}

}

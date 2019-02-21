package spacesettlers.bost7517.astar;

import java.util.LinkedList;

import spacesettlers.utilities.Position;
/**
 * Represents a path traveled by the AStar algorithm. Returned by AStarGraph.getPathTo()
 * 
 * @author Cameron Bost
 * @version 0.2
 */
public class AStarPath {
	/**List of vertices traveled to*/
	private LinkedList<Vertex> vertices = new LinkedList<>();
	
	/**Total cost of the path*/
	private int totalCost = 0;
	
	/**
	 * No constructor arguments
	 */
	public AStarPath() {
		totalCost = 0;
		vertices = new LinkedList<>();
	}
	
	/**
	 * Get the total cost of this path
	 * @return total cost of the path
	 */
	public int getTotalCost() {
		return totalCost;
	}
	
	/**
	 * Adds a vertex to this path
	 * @param v Vertex to add to path
	 */
	void addVertex(Vertex v) {
		vertices.add(v);
	}
	
	/**
	 * Get the list of positions to travel to in this path
	 * @return List of positions to travel to in this path
	 */
	public LinkedList<Position> getPositions() {
		return null;
	}
}

package spacesettlers.bost7517.astar;

import java.util.LinkedList;

import spacesettlers.utilities.Position;
/**
 * Represents a path traveled by the AStar algorithm. Returned by AStarGraph.getPathTo()
 * 
 * @author Cameron Bost
 * @version 0.2
 */
public class AStarPath implements Comparable<AStarPath>{
	/**List of vertices traveled to*/
	private LinkedList<Vertex> vertices = new LinkedList<>();
	
	/**Total cost of the path*/
	private int totalCost = 0;
	
	/**
	 * No constructor arguments
	 */
	private AStarPath() {
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
	 * Adds a vertex to this path and updates cost
	 * 
	 * @param v Vertex to add to path
	 */
	void addVertex(Vertex v) {
		if(!vertices.contains(v)) {
			vertices.add(v);
			totalCost += AStarGraph.GRID_SIZE;
		}
	}
	
	/**
	 * Get the list of positions to travel to in this path
	 * @return List of positions to travel to in this path
	 */
	public LinkedList<Position> getPositions() {
		// TODO: Set as positions
		return null;
	}
	
	Vertex getCurrentVertex() {
		return vertices.getLast();
	}

	@Override
	public int compareTo(AStarPath otherPath) {
		double value = totalCost + vertices.getLast().getHValue();
		double otherValue = otherPath.totalCost + otherPath.vertices.getLast().getHValue();
		return Double.compare(value, otherValue);
	}
	
	/**
	 * Creates a path object from an initial vertex
	 * @param v initial vertex
	 * @return Path object with v as initial vertex
	 */
	static AStarPath makePath(Vertex v) {
		AStarPath ret = new AStarPath();
		ret.addVertex(v);
		return ret;
	}
	
	/**
	 * Factory method to create a duplicate of an existing path
	 * @param p path to duplicate
	 * @return duplicate of parameter path
	 */
	static AStarPath duplicatePath(AStarPath p) {
		AStarPath ret = new AStarPath();
		for(Vertex v: p.vertices) {
			ret.addVertex(v);
		}
		return ret;
	}
}

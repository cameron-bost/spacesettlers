package spacesettlers.bost7517;

import java.util.LinkedList;

import spacesettlers.utilities.Position;

public class GBFSPath implements Comparable<GBFSPath>{
	/**List of vertices traveled to*/
	private LinkedList<Vertex> vertices = new LinkedList<>();
	
	/**Total cost of the path*/
	private int totalCost = 0;
		
	/**
	 * No constructor arguments
	 */
	private GBFSPath() {
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
	void addVertex(Vertex v){
		vertices.add(v);
		totalCost += AStarGraph.GRID_SIZE;
	}
	
	/**
	 * Get the list of positions to travel to in this path
	 * @return List of positions to travel to in this path
	 */
	public LinkedList<Position> getPositions() {
		LinkedList<Position> ret = new LinkedList<>();
		for(Vertex v: vertices) {
			ret.add(AStarGraph.getCentralCoordinate(v));
		}
		return ret;
	}
	
	Vertex getCurrentVertex() {
		return vertices.getLast();
	}
	
	int getQueueValue() {
		return (int) vertices.getLast().getHValue();
	}

	@Override
	public int compareTo(GBFSPath otherPath) {
		double value = vertices.getLast().getHValue();
		double otherValue = otherPath.vertices.getLast().getHValue();
		return Double.compare(value, otherValue);
	}
	
	/**
	 * Creates a path object from an initial vertex
	 * @param g graph object being used
	 * @param v initial vertex
	 * @return Path object with v as initial vertex
	 */
	static GBFSPath makePath(Vertex v) {
		GBFSPath ret = new GBFSPath();
		ret.addVertex(v);
		return ret;
	}
	
	/**
	 * Factory method to create a duplicate of an existing path
	 * @param g graph object being used
	 * @param p path to duplicate
	 * @return duplicate of parameter path
	 */
	static GBFSPath duplicatePath(GBFSPath p) {
		GBFSPath ret = new GBFSPath();
		for(Vertex v: p.vertices) {
			ret.addVertex(v);
		}
		return ret;
	}

	public void print() {
		boolean first = true;
		for(Vertex v: vertices) {
			if(first) first = false;
			else System.out.print(" -> ");
			System.out.print("("+v.getMtxColumn()+","+v.getMtxRow()+")");
		}
		System.out.println();
	}

	public boolean contains(Vertex child) {
		return vertices.contains(child);
	}
}

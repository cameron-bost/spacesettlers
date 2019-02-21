package spacesettlers.bost7517.astar;

import java.util.LinkedList;
import java.util.List;

/**
 * Vertex object used in graph class
 * 
 * @author Cameron Bost
 * @version 0.2
 */
public class Vertex {

	/**Heuristic value of this vertex to some destination*/
	private double hValue;
	/**Indicates if this vertex contains the ship's central pixel*/
	private boolean isStart;
	/**Indicates if this vertex contains the target's central pixel*/
	private boolean isEnd;
	/**Location in the graph matrix*/
	private int mtxColumn, mtxRow;
	/**List of connected vertices*/
	private List<Vertex> connectedVertices;
	
	/**Tracks vertices this vertex is adjacent to*/
	
	/**
	 * Constructor, coordinates in matrix must be explicit
	 * 
	 * @param _mtxColumn Column in graph matrix
	 * @param _mtxRow Row in graph matrix
	 */
	public Vertex(int _mtxColumn, int _mtxRow) {
		mtxColumn = _mtxColumn;
		mtxRow = _mtxRow;
		hValue = 0.0;
		isStart = false;
		isEnd = false;
		connectedVertices = new LinkedList<>();
	}
	
	/**
	 * Get the heuristic value for this vertex
	 * @return heuristic value for the vertex
	 */
	public double getHValue() {
		return hValue;
	}
	
	/**
	 * Indicates if this vertex has the ship in it
	 * @return whether the ship is in this vertex
	 */
	public boolean isStart() {
		return isStart;
	}
	
	/**
	 * Indicates if this vertex has the target in it
	 * @return whether the target is in this vertex
	 */
	public boolean isEnd() {
		return isEnd;
	}
	
	/**
	 * Gets the column index of this vertex
	 * @return Column index in graph matrix
	 */
	public int getMtxColumn() {
		return mtxColumn;
	}
	
	/**
	 * Gets the row index of this vertex
	 * @return Row index in graph matrix
	 */
	public int getMtxRow() {
		return mtxRow;
	}
	
	/**
	 * Gets the set of vertices connected to this one
	 * @return Set of vertices this node is connected to.
	 */
	List<Vertex> getEdges(){
		return connectedVertices;
	}
}

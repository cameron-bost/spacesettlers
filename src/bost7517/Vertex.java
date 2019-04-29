package bost7517;

import java.util.LinkedList;
import java.util.List;

import bost7517.Vertex;

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
	
	/**
	 * Allows graph class to add vertex to edge list
	 * @param v Vertex to add to edges
	 */
	void addEdge(Vertex v) {
		connectedVertices.add(v);
	}

	/**
	 * Marks this vertex as the start vertex
	 */
	void markStart() {
		isStart = true;
	}

	/**
	 * Marks this vertex as not the start verte
	 */
	void markNotStart() {
		isStart = false;
	}

	/**
	 * Marks this vertex as the target vertex
	 */
	void markEnd() {
		isEnd = true;
	}

	/**
	 * Marks this vertex as not the target vertex
	 */
	void markNotEnd() {
		isEnd = false;
	}

	/**
	 * Sets the heuristic value for this node to 0
	 */
	void clearHValue() {
		hValue = 0;
	}
	
	/**
	 * Sets the heuristic value for this vertex to the specified value
	 * @param _hValue value to set heuristic to
	 */
	void setHValue(double _hValue) {
		hValue = _hValue;
	}
}

package bost7517.oldprojects;

import java.util.HashMap;
import java.util.UUID;

import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * Used in K-Means to represent a group (cluster) of asteroids.
 * 
 * @author Cameron Bost
 *
 */
public class BDSM_KMeansAsteroidCluster implements Comparable<BDSM_KMeansAsteroidCluster>{
	private double sumX, sumY;
	private int totalResources;
	private HashMap<UUID, Asteroid> asteroidLookupMap;
	private Position centroid, shipPosition;
	private double distanceToShip;
	
	BDSM_KMeansAsteroidCluster(Asteroid a, Ship _ship){
		asteroidLookupMap = new HashMap<>();
		sumX = 0;
		sumY = 0;
		totalResources = 0;
		centroid = null;
		add(a);
		shipPosition = _ship.getPosition();
		distanceToShip = 0;
	}
	
	void add(Asteroid a) {
		asteroidLookupMap.put(a.getId(), a);
		sumX += a.getPosition().getX();
		sumY += a.getPosition().getY();
		totalResources += a.getResources().getTotal();
	}
	
	void clear() {
		asteroidLookupMap.clear();
		
		// Clear accumulator variables
		sumX = 0;
		sumY = 0;
		totalResources = 0;
	}
	
	void resetCentroid(Toroidal2DPhysics space) {
		double numAsts = asteroidLookupMap.size();
		double centX = sumX / numAsts, centY = sumY / numAsts;
		
		// Update class fields
		centroid = new Position(centX, centY);
		distanceToShip = space.findShortestDistance(centroid, shipPosition);
	}
	
	Position getCentroid() {
		return centroid;
	}
	
	public void printReport() {
		System.out.println("\t"+centroid+" -> "+asteroidLookupMap.size());
	}
	
	@Override
	public int hashCode() {
		if(centroid == null) {
			return super.hashCode();
		}
		return Integer.hashCode(Double.hashCode(centroid.getX()) + Double.hashCode(centroid.getY()));
	}

	public Asteroid getBestAsteroid() {
		Asteroid bestA = null;
		int bestValue = -1;
		for(Asteroid a: asteroidLookupMap.values()) {
			if(bestValue == -1 || a.getResources().getTotal() > bestValue) {
				bestValue = a.getResources().getTotal();
				bestA = a;
			}
		}
		return bestA;
	}

	@Override
	public int compareTo(BDSM_KMeansAsteroidCluster o) {
		// Note: intentionally swapped compare order to give decreasing ordering
		return Double.compare(((double)o.totalResources / o.distanceToShip), ((double)totalResources / distanceToShip));
	}
}

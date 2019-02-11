package spacesettlers.bost7517;

import java.util.Set;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.AiCore;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Contains utility methods used by multiple agents, to encourage code reuse
 * @author Cameron Bost, Josh Atkinson
 * @version 0.1
 */
public class AgentUtils {
	
	public static final boolean debug = false;
	
	/**
	 * Locates nearest object containing energy. Note that energy sources are bases, beacons, and cores.
	 * 
	 * @param space physics model
	 * @param ship ship currently acting
	 * @return nearest energy source
	 */
	static AbstractObject findNearestEnergySource(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AbstractObject bestObject = null;
		
		Base minBase = findNearestBase(space, ship);
		double minBaseDist = Double.MAX_VALUE;
		if(minBase != null) {
			minBaseDist = space.findShortestDistance(ship.getPosition(), minBase.getPosition());
		}
		if(debug) {
			System.out.println("Min Base: "+ (minBase == null ? "null" : minBase.getId()+", Dist: "+minBaseDist));
		}
		
		AiCore minCore = findNearestAiCore(space, ship);
		double minCoreDist = Double.MAX_VALUE;
		if(minCore != null) {
			minCoreDist = space.findShortestDistance(ship.getPosition(), minCore.getPosition());
		}
		if(debug) {
			System.out.println("Min Core: "+ (minCore == null ? "null" : minCore.getId()+", Dist: "+minCoreDist));
		}
		
		Beacon minBeacon = findNearestBeacon(space, ship);
		double minBeaconDist = Double.MAX_VALUE;
		if(minBeacon != null) {
			minBeaconDist = space.findShortestDistance(ship.getPosition(), minBeacon.getPosition());
		}
		if(debug) {
			System.out.println("Min Beacon: "+ (minBeacon == null ? "null" : minBeacon.getId()+", Dist: "+minBeaconDist));
		}
		
		minDistance = minBaseDist;
		if(minCoreDist < minDistance) {
			minDistance = minCoreDist;
			bestObject = minCore;
		}
		if(minBeaconDist < minDistance) {
			minDistance = minBeaconDist;
			bestObject = minBeacon;
		}
		
		if(bestObject != null) {
			if(debug) {
				System.out.println("Targetting energy source " + bestObject.getId());
			}
		}
		return bestObject;
	}


	/**
	 * Find the base for this team nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	static Base findNearestBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		Base nearestBase = null;

		for (Base base : space.getBases()) {
			if (base.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestBase = base;
				}
			}
		}
		return nearestBase;
	}
	
	/**
	 * Find the AI core nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	static AiCore findNearestAiCore(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		AiCore nearestCore = null;

		for (AiCore core : space.getCores()) {
			if (core.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), core.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestCore = core;
				}
			}
		}
		return nearestCore;
	}


	/**
	 * Find the nearest beacon to this ship
	 * @param space
	 * @param ship
	 * @return
	 */
	static Beacon findNearestBeacon(Toroidal2DPhysics space, Ship ship) {
		// get the current beacons
		Set<Beacon> beacons = space.getBeacons();

		Beacon closestBeacon = null;
		double bestDistance = Double.POSITIVE_INFINITY;

		for (Beacon beacon : beacons) {
			double dist = space.findShortestDistance(ship.getPosition(), beacon.getPosition());
			if (dist < bestDistance) {
				bestDistance = dist;
				closestBeacon = beacon;
			}
		}

		return closestBeacon;
	}

}

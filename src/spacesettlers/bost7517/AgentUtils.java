package spacesettlers.bost7517;

import java.util.Set;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.AiCore;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Contains utility methods used by multiple agents, to encourage code reuse
 * 
 * @author Cameron Bost
 * @version 0.3
 */
public class AgentUtils {
	
	static final boolean DEBUG = false;
	
	static final boolean SHOW_GRAPHICS = false;
	
	static final double MAX_VELOCITY = Toroidal2DPhysics.MAX_TRANSLATIONAL_VELOCITY;
	
	/**
	 * A* constants
	 */
	static final int PLAN_INTERVAL = 10;
	
	/**
	 * Constants used in policy
	 */
	static final double LOW_ENERGY_THRESHOLD = 2750;
	static final double RESOURCE_THRESHOLD = 2000;
	static final double BASE_BUYING_DISTANCE = 400;

	/**
	 * Distance at which a projection is considered targeting
	 */
	public static final double MIN_DISTANCE_PROJECTION_SAFETY = 20;
	
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
		if(DEBUG) {
			System.out.println("Min Base: "+ (minBase == null ? "null" : minBase.getId()+", Dist: "+minBaseDist));
		}
		
		AiCore minCore = findNearestAiCore(space, ship);
		double minCoreDist = Double.MAX_VALUE;
		if(minCore != null) {
			minCoreDist = space.findShortestDistance(ship.getPosition(), minCore.getPosition());
		}
		if(DEBUG) {
			System.out.println("Min Core: "+ (minCore == null ? "null" : minCore.getId()+", Dist: "+minCoreDist));
		}
		
		Beacon minBeacon = findNearestBeacon(space, ship);
		double minBeaconDist = Double.MAX_VALUE;
		if(minBeacon != null) {
			minBeaconDist = space.findShortestDistance(ship.getPosition(), minBeacon.getPosition());
		}
		if(DEBUG) {
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
			if(DEBUG) {
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
	
	static String getAbstractType(Object object) {
		String objType = object.getClass().getName();
		String[] components = objType.split("\\.");
		return components[components.length-1];
	}
	
	/**
	 * Checks if an object is likely being targeted by an opponent by projecting enemy velocity vectors.
	 * @param space
	 * @param ship
	 * @param asteroid
	 * @return
	 */
	static boolean targetedByOpponent(Toroidal2DPhysics space, Ship ship, Asteroid asteroid) {
		for(Ship enemy: space.getShips()) {
			if(!enemy.getTeamName().equals(ship.getTeamName())) {
				Position pE = enemy.getPosition();
				Vector2D vE = pE.getTranslationalVelocity();
				Position pD = asteroid.getPosition();
				// Check Y projection (enemy)
				double tY = (pD.getX() - pE.getX()) / vE.getXValue();
				double pY = vE.getYValue() * tY + pE.getY() % space.getHeight();
				Position projY = new Position(pD.getX(), pY);
				// Check X projection (enemy)
				double tX = (pD.getY() - pE.getY()) / vE.getYValue();
				double pX = vE.getXValue() * tX + pE.getX() % space.getWidth();
				Position projX = new Position(pX, pD.getY());
				double minProjDistance = Math.min(space.findShortestDistance(pD, projX), space.findShortestDistance(pD, projY));
				// Is the opponent on track to take this item?
				if(minProjDistance < MIN_DISTANCE_PROJECTION_SAFETY) {
					// Are they closer than me?
					if(space.findShortestDistance(pD, pE) < space.findShortestDistance(ship.getPosition(), pD)) {
						// Will they get there before me?
						double minProjTime = Math.min(tX, tY);
						Position pS = ship.getPosition();
						Vector2D vS = pS.getTranslationalVelocity();
						double tXS = (pD.getY() - pS.getY()) / vS.getYValue();
						double tYS = (pD.getX() - pS.getX()) / vS.getXValue();
						double minShipTime = Math.min(tXS, tYS);
						if(minProjTime < minShipTime) {
							// Abandon
							if(DEBUG) {
								System.out.println("<Opponent Targetting Inference> - "+enemy.getTeamName()+": "+minProjDistance);
							}
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Large boolean statement determining whether or not the object is a threat to the agent.
	 * Considers whether or not the object is the ship itself, or dead, or of a collectible type
	 * @param ship Current ship object
	 * @param object Object being considered
	 * @return Whether or not object is a threat
	 */
	static boolean isDangerous(Ship ship, AbstractObject object) {
		return !(object.getId().equals(ship.getId()) || !object.isAlive() 
				|| object instanceof Missile || object instanceof Beacon
				|| object instanceof AiCore 
				|| (object instanceof Asteroid && ((Asteroid) object).isMineable())
				|| (object instanceof Base && ((Base) object).getTeamName().equals(ship.getTeamName())));
	}

	/**
	 * Determines if an object is viewed as an obstacle by the ship
	 * @param ship Ship checking for obstacle
	 * @param object Object that might be an obstacle
	 * @return Whether object is an obstacle
	 */
	public static boolean isObstacle(Ship ship, AbstractObject object) {
		return !object.getId().equals(ship.getId()) 
				&& (
					object instanceof Missile 
					|| object instanceof Ship
					|| (object instanceof Asteroid && !((Asteroid) object).isMineable())
					|| object instanceof Base
				);
	}


	public static Asteroid getBestAsteroidWtOptDist(Toroidal2DPhysics space, Ship myShip, double optDist) {
		Asteroid nearestMineableAsteroid = null;
		Set<Asteroid> asteroids = space.getAsteroids();
		int lowestDistance = Integer.MAX_VALUE;
		Asteroid bestAsteroid = null;
		 
//		/**For each base belonging to this team.*/
//		for (Base base: space.getBases()) {
//			if(base.getTeamName().equals(myShip.getTeamName())) {
//				/**For each mineable asteroid*/
//				for (Asteroid asteroid : asteroids) {
//					if (asteroid.isMineable()) {
//						int distance = (int) (space.findShortestDistance(myShip.getPosition(), asteroid.getPosition())
//								+ space.findShortestDistance(asteroid.getPosition(), base.getPosition()));
//						int diff = Math.abs((int)distance - (int)optDist);
//						if (diff < lowestDistance) {
//							lowestDistance = distance;
//							nearestMineableAsteroid = asteroid;
//						}
//					}
//				}
//			}
//		}
		
		double bestMoney = Integer.MIN_VALUE;
		double minDistance = Double.MAX_VALUE;
		for (Asteroid asteroid : asteroids) { // This will cycle each asteroid
			double dist = space.findShortestDistance(asteroid.getPosition(), myShip.getPosition());
				if (asteroid.isMineable() && dist < optDist && dist< minDistance) { // checks to make sure that we are only searching mineable asteroids and the asteroids is higher then a previous asteroid.
					//double dist = space.findShortestDistance(asteroid.getPosition(), myShip.getPosition()); // finds the distance between the asteroid and the ship.
					if (asteroid.getResources().getTotal() > bestMoney) { // Will determine if the new distance is less then the previous distance.
						bestMoney = asteroid.getResources().getTotal();
						//System.out.println("Considering asteroid " + asteroid.getId() + " as a best one");
						bestAsteroid = asteroid;
						minDistance = dist;
					}
			}
		}
		return bestAsteroid;
	}
}

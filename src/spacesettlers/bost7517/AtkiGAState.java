package spacesettlers.bost7517;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * Heavily inspired by clients.examples.ExampleGAState.
 *  
 * @author Joshua Atkinson, Cameron Bost
 * @version 0.3
 *
 */
public class AtkiGAState {
	
	private int lowestDistance;
	@XStreamOmitField
	private Asteroid nearestMineableAsteroid;
	@XStreamOmitField
	private AbstractObject energyTarget;
	@XStreamOmitField
	private Base base;
	
	public AtkiGAState(Toroidal2DPhysics space, Ship myShip, double optDist) 
	{
		updateState(space, myShip, optDist);
	}
	
	/**
	 * Update the distance to the nearest mineable asteroid,energy target
	 * 
	 * @param space Physics model for game.
	 * @param myShip Ship of concern.
	 * @param optDist 
	 */
	public void updateState(Toroidal2DPhysics space, Ship myShip, double optDist) {
		nearestMineableAsteroid = AgentUtils.getBestAsteroidWtOptDist(space, myShip, optDist);
		energyTarget = AgentUtils.findNearestEnergySource(space, myShip);
		base = AgentUtils.findNearestBase(space, myShip);
	}

	/**
	 * Return Energy Target
	 * @return
	 */
	public AbstractObject getBase() {
		return base;
	}
	
	/**
	 * Return Energy Target
	 * @return
	 */
	public AbstractObject getEnergySource() {
		return energyTarget;
	}
	
	/**
	 * Return the nearest asteroid (used for actions)
	 * 
	 * @return
	 */
	public Asteroid getBestMineableAsteroid() {
		return nearestMineableAsteroid;
	}


	/**
	 * Generated by eclipse - make sure you update this when you update the state (just use eclipse to regenerate it)
	 */
	@Override
	public int hashCode() {
		return Integer.hashCode(Integer.hashCode(lowestDistance) 
				+ (nearestMineableAsteroid == null ? 0 : nearestMineableAsteroid.getId().hashCode())
				+ (energyTarget == null ? 0 : energyTarget.getId().hashCode())
				+ (base == null ? 0 : base.getId().hashCode()));
	}
<<<<<<< HEAD
	/**
	 * Will re-determine the asteroid based on the optimalDistance Variable.
	 * @param optimalDistance
	 * @param space
	 * @param myShip
	 */
	public void changeDistance(int optimalDistance,Toroidal2DPhysics space,Ship myShip) 
	{
		int bestMoney = Integer.MIN_VALUE;
		Set<Asteroid> asteroids = space.getAsteroids();
		distanceToNearestMineableAsteroid = optimalDistance;
		double distance;

		for (Asteroid asteroid : asteroids) {
			if (asteroid.isMineable()) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > bestMoney) {
					distance = space.findShortestDistance(myShip.getPosition(), asteroid.getPosition());
					if (distance < distanceToNearestMineableAsteroid) 
					{
						bestMoney = asteroid.getResources().getTotal();
						distanceToNearestMineableAsteroid = distance;
						nearestMineableAsteroid = asteroid;
					}
				}
			}
		}
	}



=======
>>>>>>> c84b1d3cc0624fb89a866697518bb93545726dc9
}

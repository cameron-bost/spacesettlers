package bost7517;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
/**
 * 
 * @author Bost-Atkinson Digital Space Mining (BDSM), LLC
 * @version 0.4
 */
public class BDSM_P4_Relations {
	
	/**Max distance for two objects to be considered "near" each other.*/
	static final double NEAR_THRESHOLD = 50.0;

	/**
	 * Relation to indicate if an object is alive.
	 * 
	 * @param object to check living-ness of
	 * @return whether object is alive
	 */
	public static boolean isAlive(AbstractActionableObject obj) {
		return obj.isAlive();
	}
	
	/**
	 * Determines if two objects are close to each other.
	 * 
	 * @param space physics model
	 * @param obj1 first object
	 * @param obj2 second object
	 * @return whether the two objects are close together
	 */
	public static boolean isNear(Toroidal2DPhysics space, AbstractActionableObject obj1, AbstractActionableObject obj2) {
		return isAlive(obj1) && isAlive(obj2) && space.findShortestDistance(obj1.getPosition(), obj2.getPosition()) <= NEAR_THRESHOLD;
	}
	
	/**
	 * Checks if ship is currently guarding a base.
	 * 
	 * @param space physics model
	 * @param s ship doing guarding
	 * @param b base being guarded
	 * @return whether s is guarding b
	 */
	public static boolean isGuarding(Toroidal2DPhysics space, Ship s, Base b) {
		return isAlive(s) && isAlive(b) && isNear(space, s, b);
	}
}

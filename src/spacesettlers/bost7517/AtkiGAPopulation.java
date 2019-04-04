package spacesettlers.bost7517;

import java.util.LinkedList;
import java.util.Random;

import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * A population of genetic algorithm candidates. This class exists 
 * to store the population, advance generations, and offer access 
 * to GA data like fitness values.
 * 
 * @author Joshua Atkinson, Cameron Bost
 * @version 0.3
 *
 */
public class AtkiGAPopulation {
	
	/**Population as array of chromosomes*/
	private AtkiGAChromosome[] population;
	
	/**Current number of members in the population*/
	private int currentPopulationIndex;
	
	/**Data structure for fitness score of every chromosome*/
	private double[] fitnessScores;
	
	/**Shared Random object*/
	private Random random;
	
	/**Fixed population size.*/
	private int populationSize;
	
	/**Chance of mutation (out of 1.0)*/
	private static double pMutation = 0.10;
	
	private double currentScore;

	/**
	 * Make a new empty population
	 */
	public AtkiGAPopulation(int populationSize, Random _random) {
		super();
		this.populationSize = populationSize;
		random = _random;
		// start at member zero
		currentPopulationIndex = 0;
		
		// make an empty population
		population = new AtkiGAChromosome[populationSize];
		
		for (int i = 0; i < populationSize; i++) {
			population[i] = new AtkiGAChromosome(_random);
		}
		
		// make space for the fitness scores
		fitnessScores = new double[populationSize];
	}

	/**
	 * Score is updated by the client class
	 * 
	 * @param space
	 */
	public void evaluateFitnessForCurrentMember(Toroidal2DPhysics space) {
		fitnessScores[currentPopulationIndex] = currentScore;
	}

	/**
	 * Return true if we have reached the end of this generation and false otherwise
	 * 
	 * @return
	 */
	public boolean isGenerationFinished() {
		if (currentPopulationIndex == populationSize) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Return the next member of the population (handles wrapping around by going
	 * back to the start but then the assumption is that you will reset with crossover/selection/mutation
	 * 
	 * @return
	 */
	public AtkiGAChromosome getNextMember() {
		currentPopulationIndex++;
		return population[currentPopulationIndex % populationSize];
	}

	/**
	 * Does crossover, selection, and mutation using our current population.
	 */
	public void makeNextGeneration() {
		AtkiGAChromosome[] newPopulation = new AtkiGAChromosome[populationSize];

		// Selection
		population = doSelection(population);
		
		// Shuffle population before continuing
		shufflePopulation();
		// Crossover, choose 2 random parents for each new chromosome.
		for(int idx = 0; idx < populationSize; idx++) {
			int p1 = random.nextInt(population.length);
			int p2 = random.nextInt(population.length);
			newPopulation[idx] = AtkiGAChromosome.doCrossover(population[p1], population[p2], random);
		}
		
		// Mutation
		for(int idx = 0; idx < populationSize; idx++) {
			if (random.nextDouble() <= pMutation) {
				newPopulation[idx].mutate(random);
			}
		}
		population = newPopulation;
		// Reset population counter
		currentPopulationIndex = 0;
	}
	
	/**
	 * Shuffles population array, leaving any null values at end of array
	 */
	void shufflePopulation() {
		int nullIdx = currentPopulationIndex;
		for(int i = currentPopulationIndex; i >= 1; i--) {
			// Move null items (should be on the end).
			if(population[i] == null) {
				if(nullIdx != i) {
					swapChromosomes(i, nullIdx--);
				}
				continue;
			}
			else {
				// destination index <= random location from [0, i]
				int destIdx = random.nextInt(i+1);
				// Swap [i] with [destination index]
				swapChromosomes(i, destIdx);
			}
		}
	}
	
	void swapChromosomes(int idxA, int idxB) {
		AtkiGAChromosome t = population[idxA];
		population[idxA] = population[idxB];
		population[idxB] = t;
	}
	
	/**
	 * Performs selection on population, removes individuals that 
	 * are not selected. Removed individuals are represented as 
	 * "null" in the population data structure.
	 * 
	 * @param population current population
	 * @return Population after selection
	 */
	private AtkiGAChromosome[] doSelection(AtkiGAChromosome[] population){
		/**
		 * Tournament
		 * - Select 3 individuals, only best moves on
		 */
		int tournamentSize = 3;
		LinkedList<AtkiGAChromosome> ret = new LinkedList<>();
		shufflePopulation();
		for(int i = 2; i < population.length; i+=tournamentSize) {
			// Get best chromosome for this tournament
			AtkiGAChromosome bestOne = null;
			double bestFitness = Double.MIN_VALUE;
			for(int j = 0; j < tournamentSize; j++) {
				int chIdx = i - j;
				if(fitnessScores[chIdx] > bestFitness) {
					bestFitness = fitnessScores[chIdx];
					bestOne = population[chIdx];
				}
			}
			ret.add(bestOne);
		}
		return ret.toArray(new AtkiGAChromosome[ret.size()]);
	}

	/**
	 * Return the first member of the popualtion
	 * @return
	 */
	public AtkiGAChromosome getFirstMember() {
		return population[0];
	}
	/**
	 * return any member of population.
	 * @param i
	 * @return
	 */
	public AtkiGAChromosome getMember(int i) {
		return population[i];
	}
	
	/**
	 * returns the current population size.
	 * @return
	 */
	public int getCurrentPopulation(){
		return population.length;
	}

	public void updateScore(double d) {
		this.currentScore = d;
	}

	public AtkiGAChromosome getCurrentMember() {
		currentPopulationIndex %= populationSize;
		return population[currentPopulationIndex];
	}
	
}
	

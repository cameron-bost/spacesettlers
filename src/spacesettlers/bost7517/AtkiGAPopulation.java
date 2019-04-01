package spacesettlers.bost7517;

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
	private AtkiGAChromosome[] population;
	
	private int currentPopulationCounter;
	
	private double[] fitnessScores;
	
	/**Shared Random object*/
	private Random random;
	/**Chance of mutation (out of 1.0)*/
	private static double pMutation = 0.10;
	/**Chance of crossover (out of 1.0)*/
	private static double pCrossover = 0.10;

	/**
	 * Make a new empty population
	 */
	public AtkiGAPopulation(int populationSize, AStarGraph _graph, Random _random) {
		super();
		
		random = _random;
		
		// start at member zero
		currentPopulationCounter = 0;
		
		// make an empty population
		population = new AtkiGAChromosome[populationSize];
		
		for (int i = 0; i < populationSize; i++) {
			population[i] = new AtkiGAChromosome(_graph);
		}
		
		// make space for the fitness scores
		fitnessScores = new double[populationSize];
	}

	/**
	 * Currently scores all members as zero (the student must implement this!)
	 * 
	 * @param space
	 */
	public void evaluateFitnessForCurrentMember(Toroidal2DPhysics space) {
		fitnessScores[currentPopulationCounter] = 0;
	}

	/**
	 * Return true if we have reached the end of this generation and false otherwise
	 * 
	 * @return
	 */
	public boolean isGenerationFinished() {
		if (currentPopulationCounter == population.length) {
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
		currentPopulationCounter++;
		
		return population[currentPopulationCounter % population.length];
	}

	/**
	 * Does crossover, selection, and mutation using our current population.
	 */
	public void makeNextGeneration() {
		// Selection
		population = doSelection(population);
		
		// Shuffle population before continuing
		shufflePopulation();
		
		// Crossover
		for(int idx = 1; idx < currentPopulationCounter; idx++) {
			if (random.nextDouble() <= pCrossover) {
				/**
				 * TODO: This crossover implementation is incorrect. 
				 * Should add child to list, not replace one of the parents.
				 */
				population[idx] = doCrossover(population[idx], population[idx-1]);
			}
		}
		
		// Mutation
		for(int idx = 0; idx < currentPopulationCounter; idx++) {
			if (random.nextDouble() <= pMutation) {
				population[idx] = doMutate(population[idx]);
			}
		}
		
		// Reset population counter
		currentPopulationCounter = 0;
	}
	
	/**
	 * Shuffles population array, leaving any null values at end of array
	 */
	void shufflePopulation() {
		int nullIdx = currentPopulationCounter;
		for(int i = currentPopulationCounter; i >= 1; i--) {
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
	 * Performs crossover on two chromosomes, returns new chromosome.
	 *  
	 * @param p1 Parent 1
	 * @param p2 Parent 2
	 * @return Crossover child from parameter values
	 */
	private static AtkiGAChromosome doCrossover(AtkiGAChromosome p1, AtkiGAChromosome p2) {
		// TODO: implement: gene is average of p1,p2
		return null;
	}
	
	/**
	 * Performs mutation on chromosome, returns new chromosome.
	 * 
	 * @param p Chromosome to mutate
	 * @return Mutated chromosome
	 */
	private static AtkiGAChromosome doMutate(AtkiGAChromosome p) {
		// TODO: set step amount, mutation chance
		return p;
	}
	
	/**
	 * Performs selection on population, removes individuals that 
	 * are not selected. Removed individuals are represented as 
	 * "null" in the population data structure.
	 * 
	 * @param population current population
	 * @return Population after selection
	 */
	private static AtkiGAChromosome[] doSelection(AtkiGAChromosome[] population){
		// TODO: determine/implement selection method (e.g. roulette, tournament)
		return population;
	}

	/**
	 * Return the first member of the popualtion
	 * @return
	 */
	public AtkiGAChromosome getFirstMember() {
		return population[0];
	}
}
	


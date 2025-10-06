package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.Logger;
import Ic2ExpReactorPlanner.Reactor;
import Ic2ExpReactorPlanner.ReactorSimulator;
import Ic2ExpReactorPlanner.SimulationData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

public class EvolutionEngine {
    private final GAConfig config;
    private final Random random;

    public EvolutionEngine(GAConfig config) {
        this.config = config;
        this.random = new Random();
    }

    public EvolutionEngine(GAConfig config, long seed) {
        this.config = config;
        this.random = new Random(seed);
    }

    public ArrayList<EvaluatedGenome> Run() {
        return Run(false);
    }

    public ArrayList<EvaluatedGenome> Run(boolean verbose) {
        long globalStartTime = System.nanoTime();
        double overallBestFitness = -1;

        ArrayList<EvaluatedGenome> population = new ArrayList<>();
        int generation = 0;
        boolean exploratoryPhase = true;
        ReactorSimulator simulator = new ReactorSimulator();

        printVerbose(verbose, "Evolution settings: max generation = %d; phase length = %d; pop. size = %d; alpha count = %d; tournament size = %d", this.config.evolution.maxGeneration, this.config.evolution.phaseLengthGenerations, this.config.evolution.populationSize, this.config.evolution.alphaCount, this.config.evolution.tournamentSizeK);
        printVerbose(verbose, "Starting evolution...");

        // population initialization (all random for now)
        for (int i = 0; i < this.config.evolution.populationSize; i++) {
            ReactorGenome newGenome = ReactorGenome.randomGenome(this.config, this.random);
            population.add(new EvaluatedGenome(newGenome));
        }

        printVerbose(verbose, "Initial population of %d candidates created", population.size(), this.config.evolution.maxGeneration);

        while (generation < this.config.evolution.maxGeneration) {
            long generationStartTime = System.nanoTime();
            double bestFitnessInGeneration = -1;

            // alternate between exploratory phases and refinement phases
            String phaseName = "exploratory";
            if (generation % this.config.evolution.phaseLengthGenerations == 0) {
                if (generation > 0)
                    exploratoryPhase = !exploratoryPhase;
                if (!exploratoryPhase)
                    phaseName = "refinement";
                printVerbose(verbose, "starting %s phase", phaseName);
            }

            assert !population.isEmpty() : "Population list cannot be empty.";

            // evaluate each genome by running the simulation and analyzing the data with EvaluateGenomeFitness
            for (EvaluatedGenome currentGenome : population) {
                if (currentGenome.fitness < 0) {
                    Reactor currentReactor = currentGenome.genome.toReactor();
                    simulator.resetState();
                    SimulationData data = simulator.runSimulation(currentReactor);

                    // store that fitness in EvaluatedGenome
                    double fitness = EvaluateGenomeFitness(data, currentGenome.genome);
                    currentGenome.setFitness(fitness);
                }
            }

            // find the alpha
            EvaluatedGenome alpha = population.get(0);
            for (EvaluatedGenome candidate : population) {
                if (candidate.getFitness() > alpha.getFitness()) {
                    alpha = candidate;
                }
            }

            bestFitnessInGeneration = alpha.getFitness();

            if (bestFitnessInGeneration > overallBestFitness) {
                overallBestFitness = bestFitnessInGeneration;
            }

            // don't need a new population for the last generation
            if (generation < this.config.evolution.maxGeneration - 1) {

                // create the new generation
                ArrayList<EvaluatedGenome> newPopulation = new ArrayList<>();

                // find the alphas (if more than one) and add them to newPopulation
                if (this.config.evolution.alphaCount == 1) {
                    newPopulation.add(new EvaluatedGenome(alpha.getGenome().copy(), alpha.getFitness()));
                } else {
                    population.sort(Comparator.comparingDouble(EvaluatedGenome::getFitness).reversed());
                    for (int i = 0; i < config.evolution.alphaCount; i++) {
                        newPopulation.add(new EvaluatedGenome(population.get(i).getGenome().copy(), population.get(i).getFitness()));
                    }
                }

                // fill the rest of the population with the tournament selection breeding
                int tournamentCount = this.config.evolution.populationSize - newPopulation.size();
                for (int i = 0; i < tournamentCount; i++) {
                    ArrayList<EvaluatedGenome> tournamentSelection = new ArrayList<>();
                    // tournament selection process for parentA
                    for (int k = 0; k < this.config.evolution.tournamentSizeK; k++) {
                        tournamentSelection.add(population.get(this.random.nextInt(population.size())));
                    }

                    assert !tournamentSelection.isEmpty() : "Tournament competitor list cannot be empty.";

                    // grab tournament A winner
                    EvaluatedGenome parentA = tournamentSelection.get(0);
                    for (EvaluatedGenome candidate : tournamentSelection) {
                        if (candidate.getFitness() > parentA.getFitness()) {
                            parentA = candidate;
                        }
                    }

                    tournamentSelection = new ArrayList<>();
                    // tournament selection process for parentB
                    for (int k = 0; k < this.config.evolution.tournamentSizeK; k++) {
                        tournamentSelection.add(population.get(this.random.nextInt(population.size())));
                    }

                    assert !tournamentSelection.isEmpty() : "Tournament competitor list cannot be empty.";

                    // grab tournament B winner
                    EvaluatedGenome parentB = tournamentSelection.get(0);
                    for (EvaluatedGenome candidate : tournamentSelection) {
                        if (candidate.getFitness() > parentB.getFitness()) {
                            parentB = candidate;
                        }
                    }

                    // breeding phase
                    ReactorGenome childGenome = ReactorGenome.crossBreed(this.config, parentA.getGenome(), parentB.getGenome(), this.random);

                    // mutation phase
                    GAConfig.PhaseProbabilities mutationProbabilities = exploratoryPhase ? this.config.mutation.exploration : this.config.mutation.refinement;
                    childGenome.tryMutation(this.config, mutationProbabilities, this.random);

                    newPopulation.add(new EvaluatedGenome(childGenome));
                }

                // replace population with newPopulation
                population = newPopulation;
            }

            long generationEndTime = System.nanoTime();
            double generationElapsedTimeMS = (generationEndTime - generationStartTime) / 1e6;
            printVerbose(verbose, "Generation %d best fitness: %.2f, took %.2fms", generation, bestFitnessInGeneration, generationElapsedTimeMS);
            generation++;
        }

        long globalEndTime = System.nanoTime();
        double globalElapsedTimeMS = (globalEndTime - globalStartTime) / 1e6;
        printVerbose(verbose, "Evolution process finished! Best fitness: %.2f, took %.2fms", overallBestFitness, globalElapsedTimeMS);
        return population;
    }

    private double EvaluateGenomeFitness(SimulationData data, ReactorGenome genome) {
        double fitness = 0.0;

        if (data.maxTemp > 0)
            return 0.0;

        // base fitness is average EU/t for the duration
        fitness += data.avgEUoutput;

        if (data.firstComponentBrokenTime < Integer.MAX_VALUE)
            fitness *= this.config.fitness.componentBrokenPenalty;

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe modify by fuel efficiency (EU/t per rod)
        // maybe more reactor analysis for more modifiers

        return fitness;
    }

    private void printVerbose(boolean verbose, String message, Object... args) {
        if (verbose)
            Logger.log(message, args);
    }

    public static class EvaluatedGenome {
        private ReactorGenome genome;
        private double fitness;

        public EvaluatedGenome(ReactorGenome genome) {
            this.genome = genome;
            this.fitness = -1;
        }

        public EvaluatedGenome(ReactorGenome genome, double fitness) {
            this.genome = genome;
            this.fitness = fitness;
        }

        public ReactorGenome getGenome() {
            return this.genome;
        }

        public void setGenome(ReactorGenome genome) {
            this.genome = genome;
        }

        public double getFitness() {
            return this.fitness;
        }

        public void setFitness(double fitness) {
            this.fitness = fitness;
        }
    }
}

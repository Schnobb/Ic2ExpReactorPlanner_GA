package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.Logger;
import Ic2ExpReactorPlanner.Reactor;
import Ic2ExpReactorPlanner.ReactorSimulator;
import Ic2ExpReactorPlanner.SimulationData;

import java.util.*;
import java.util.concurrent.*;

public class EvolutionEngine {
    private final GAConfig config;
    private final Random random;

    private final ExecutorService executor;
    private final ThreadLocal<ReactorSimulator> simulatorThreadLocal;
    private final int coreCount;

    public EvolutionEngine(GAConfig config) {
        this(config, new Random().nextLong());
    }

    public EvolutionEngine(GAConfig config, long seed) {
        this.config = config;
        this.random = new Random(seed);

        this.coreCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(this.coreCount);
        this.simulatorThreadLocal = ThreadLocal.withInitial(ReactorSimulator::new);
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

        ArrayList<Future<Double>> fitnessFutures = new ArrayList<>(population.size());

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
//            for (EvaluatedGenome currentGenome : population) {
//                if (currentGenome.fitness < 0) {
//                    Reactor currentReactor = currentGenome.genome.toReactor();
//                    simulator.resetState();
//                    SimulationData data = simulator.runSimulation(currentReactor);
//
//                    // store that fitness in EvaluatedGenome
//                    double fitness = EvaluateGenomeFitness(data, currentGenome.genome);
//                    currentGenome.setFitness(fitness);
//                }
//            }
            fitnessFutures.clear();

            for (int i = 0; i < population.size(); i++) {
                EvaluatedGenome currentGenome = population.get(i);

                final ReactorGenome genomeForThread = currentGenome.getGenome().copy();
//                final double knownFitness = currentGenome.getFitness();

                Callable<Double> task = () -> {
//                    if (knownFitness >= 0)
//                        return knownFitness;

                    ReactorSimulator threadSimulator = this.simulatorThreadLocal.get();
                    threadSimulator.resetState();

                    Reactor currentReactor = genomeForThread.toReactor();
                    SimulationData data = threadSimulator.runSimulation(currentReactor);

                    return EvaluateGenomeFitness(data, genomeForThread);
                };

                fitnessFutures.add(executor.submit(task));
            }

            try {
                for (int i = 0; i < population.size(); i++) {
                    population.get(i).setFitness(fitnessFutures.get(i).get());
                }
            } catch (Exception e) {
                Logger.log(Logger.LogLevel.ERROR, "A simulation thread failed: %s", e.getCause() != null ? e.getCause().toString() : e.toString());
                e.printStackTrace();
            }

            int stableCount = 0;
            for (EvaluatedGenome genome : population) {
                if (genome.getFitness() > 0) stableCount++;

            }

            Logger.log(Logger.LogLevel.DEBUG, "Initial stable designs: %d/%d (%.1f%%)", stableCount, population.size(), 100.0 * stableCount / population.size());

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

            Set<Integer> uniqueDesigns = new HashSet<>();
            for (EvaluatedGenome genome : population) {
                uniqueDesigns.add(genome.getGenome().hashCode());
            }

            double diversityRatio = (double) uniqueDesigns.size() / (double) population.size();
            Logger.log(Logger.LogLevel.DEBUG, "Diversity: %d unique genomes (%.2f%%)", uniqueDesigns.size(), diversityRatio * 100);

            // TODO: parametrize those
            if (diversityRatio < 0.2 && generation > 20) {
                // TODO: also parametrize this
                int injectCount = population.size() / 4;
                Logger.log(Logger.LogLevel.DEBUG, "LOW DIVERSITY. Injecting %d random designs", injectCount);

                population.sort(Comparator.comparingDouble(EvaluatedGenome::getFitness).reversed());

                // replace bottom 25%
                for (int i = population.size() - injectCount; i < population.size(); i++) {
                    population.set(i, new EvaluatedGenome(ReactorGenome.randomGenome(config, random)));
                }
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
            printVerbose(verbose, "Generation %d best fitness: %.2f, took %.2fms. Alpha: %s", generation, bestFitnessInGeneration, generationElapsedTimeMS, alpha.getGenome().getERPCode());
            generation++;
        }

        long globalEndTime = System.nanoTime();
        double globalElapsedTimeMS = (globalEndTime - globalStartTime) / 1e6;
        printVerbose(verbose, "Evolution process finished! Best fitness: %.2f, took %.2fms", overallBestFitness, globalElapsedTimeMS);

        // executor cleanup
        try {
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

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

        int fuelRodCount = 0;
        for (int componentId : genome.getReactorLayout()) {
            if (componentId == ReactorGenome.FUEL_VALUE) {
                fuelRodCount++;
            }
        }

        if (fuelRodCount > 0) {
            double fuelEfficiency = data.avgEUoutput / (double) fuelRodCount;
            fitness += fuelEfficiency * config.fitness.fuelEfficiencyWeight;
        }

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe modify by fuel efficiency (EU/t per rod)
        // maybe further penalize reactors that accumulate too much heat in their component (as a % of the heat capacity of the component)

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

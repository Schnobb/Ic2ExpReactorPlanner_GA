package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.*;
import Ic2ExpReactorPlanner.components.ReactorItem;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;

public class EvolutionEngine {
    private final GAConfig config;
    private final Random random;
    private final ArrayList<ReactorGenome> startingPopulation;

    private final ExecutorService executor;
    private final ThreadLocal<ReactorSimulator> simulatorThreadLocal;

    public EvolutionEngine(GAConfig config) {
        this(config, new Random().nextLong());
    }

    public EvolutionEngine(GAConfig config, long seed) {
        this.config = config;
        this.random = new Random(seed);
        startingPopulation = new ArrayList<>();

        int coreCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(coreCount);
        this.simulatorThreadLocal = ThreadLocal.withInitial(ReactorSimulator::new);
    }

    public void preSeedGen0(ArrayList<ReactorGenome> startingPopulation) {
        this.startingPopulation.addAll(startingPopulation);
    }

    public ArrayList<EvaluatedGenome> run() {
        return run(false);
    }

    public ArrayList<EvaluatedGenome> run(boolean verbose) {
        ArrayList<EvaluatedGenome> evaluatedPopulation = null;

        double overallBestFitness = -1;
        int generation = 0;
        boolean exploratoryPhase = true;

        long globalStartTime = System.nanoTime();

        printVerbose(verbose, "Evolution settings: %s", this.config.evolution.toString());
        printVerbose(verbose, "Starting evolution...");

        ArrayList<ReactorGenome> population = initializePopulation(this.config, this.startingPopulation, this.random);
        printVerbose(verbose, "Initial population of %d candidates created. Seeded with %d pre-configured reactors.", population.size(), this.startingPopulation.size());

        while (generation < this.config.evolution.maxGeneration) {
            long generationStartTime = System.nanoTime();
            double bestFitnessInGeneration = -1;

            // Alternate between exploratory phases and refinement phases
            // TODO: decouple this stuff and handle it in its own bubble
            String phaseName = "exploratory";
            if (generation % this.config.evolution.phaseLengthGenerations == 0) {
                if (generation > 0)
                    exploratoryPhase = !exploratoryPhase;
                if (!exploratoryPhase)
                    phaseName = "refinement";
                printVerbose(verbose, "starting %s phase", phaseName);
            }

            assert !population.isEmpty() : "Population list cannot be empty.";

            // Run simulation and gather the SimulationData from each run
            evaluatedPopulation = simulatePopulation(population, this.simulatorThreadLocal, this.executor);

            // Actual fitness computation and alpha identification
            // TODO: refactor this into a analyzeGeneration returning a GenerationAnalysis class or something
            int stableCount = 0;
            double totalFitness = 0;
            EvaluatedGenome alpha = evaluatedPopulation.get(0);
            for (EvaluatedGenome evaluatedGenome : evaluatedPopulation) {
                double fitness = evaluateGenomeFitness(evaluatedGenome);
                evaluatedGenome.setFitness(evaluateGenomeFitness(evaluatedGenome));
                totalFitness += fitness;

                if (evaluatedGenome.getFitness() > 0) stableCount++;
                if (evaluatedGenome.getFitness() > alpha.getFitness()) alpha = evaluatedGenome;
            }

            Logger.log(Logger.LogLevel.DEBUG, "Stable designs in generation %d: %d/%d (%.1f%%)", generation, stableCount, evaluatedPopulation.size(), 100.0 * stableCount / evaluatedPopulation.size());

            bestFitnessInGeneration = alpha.getFitness();

            if (bestFitnessInGeneration > overallBestFitness) {
                overallBestFitness = bestFitnessInGeneration;
            }

            Set<Integer> uniqueDesigns = new HashSet<>();
            for (EvaluatedGenome genome : evaluatedPopulation) {
                uniqueDesigns.add(genome.getGenome().hashCode());
            }

            double diversityRatio = (double) uniqueDesigns.size() / (double) evaluatedPopulation.size();
            Logger.log(Logger.LogLevel.DEBUG, "Diversity in generation %d: %d unique genomes (%.2f%%)", generation, uniqueDesigns.size(), diversityRatio * 100);

            int randomGenomesInjectCount = 0;
            if (diversityRatio < this.config.evolution.lowDiversityThreshold) {
                randomGenomesInjectCount = (int) Math.floor((double) evaluatedPopulation.size() * this.config.evolution.lowDiversityCullingRatio);
                Logger.log(Logger.LogLevel.DEBUG, "LOW DIVERSITY IN GENERATION %d. Injecting %d random designs into next generation", generation, randomGenomesInjectCount);
            }

            // Don't need a new population for the last generation
            if (generation < this.config.evolution.maxGeneration - 1) {
                // TODO: refactor all this into a breedNextGeneration() function

                // Create the new generation
                ArrayList<ReactorGenome> newPopulation = new ArrayList<>();

                // Find the alphas (if more than one) and add them to newPopulation
                // TODO: refactor alpha handling stuff
                if (this.config.evolution.alphaCount == 1) {
                    newPopulation.add(alpha.getGenome().copy());
                } else {
                    evaluatedPopulation.sort(Comparator.comparingDouble(EvaluatedGenome::getFitness).reversed());
                    for (int i = 0; i < config.evolution.alphaCount; i++) {
                        newPopulation.add(evaluatedPopulation.get(i).getGenome().copy());
                    }
                }

                // Fill the rest of the population with the tournament selection breeding
                // TODO: refactor all this new population stuff
                int tournamentCount = this.config.evolution.populationSize - newPopulation.size() - randomGenomesInjectCount;
                ReactorGenome.MutationStatTracker statTracker = new ReactorGenome.MutationStatTracker();

                // TODO: create a selectParentViaTournament() function and call it once for each new parent
                for (int i = 0; i < tournamentCount; i++) {
                    ArrayList<EvaluatedGenome> tournamentSelection = new ArrayList<>();
                    // tournament selection process for parentA
                    for (int k = 0; k < this.config.evolution.tournamentSizeK; k++) {
                        tournamentSelection.add(evaluatedPopulation.get(this.random.nextInt(evaluatedPopulation.size())));
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
                        tournamentSelection.add(evaluatedPopulation.get(this.random.nextInt(evaluatedPopulation.size())));
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
                    childGenome.tryMutation(this.config, mutationProbabilities, this.random, statTracker);

                    newPopulation.add(childGenome);
                }

                // Inject random genomes into new population to spike diversity if diversity is too low
                for (int i = 0; i < randomGenomesInjectCount; i++) {
                    newPopulation.add(ReactorGenome.randomGenome(this.config, this.random));
                }

                Logger.log(Logger.LogLevel.DEBUG, "Mutations count in generation %d: " + statTracker, generation);

                // replace population with newPopulation
                population = newPopulation;
            }


            long generationEndTime = System.nanoTime();
            double generationElapsedTimeMS = (generationEndTime - generationStartTime) / 1e6;

            ReactorItem alphaFuelType = ComponentFactory.getDefaultComponent(alpha.getGenome().getFuelType());
            assert alphaFuelType != null;

            String alphaFuelTypeString = alphaFuelType.name;
            String alphaRender = String.format("%s - %.2fEU/t %s", alphaFuelTypeString, alpha.getSimulationData().avgEUOutput, alpha.getGenome().getERPCode());

            printVerbose(verbose, "Generation %d [%s] best fitness: %.2f, avg. fitness: %.2f, took %.2fms. Alpha: %s", generation, phaseName, bestFitnessInGeneration, totalFitness / (double) population.size(), generationElapsedTimeMS, alphaRender);
            generation++;
        }

        long globalEndTime = System.nanoTime();
        double globalElapsedTimeMS = (globalEndTime - globalStartTime) / 1e6;
        printVerbose(verbose, "Evolution process finished! Best fitness: %.2f, took %.2fms", overallBestFitness, globalElapsedTimeMS);

        // executor cleanup
        try {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                Logger.log(Logger.LogLevel.WARNING, "executor.awaitTermination timed out. Is there a threading issue?");
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        return evaluatedPopulation;
    }

    private ArrayList<EvaluatedGenome> simulatePopulation(ArrayList<ReactorGenome> population, ThreadLocal<ReactorSimulator> simulators, ExecutorService executor) {
        ArrayList<EvaluatedGenome> evaluatedGenomes = new ArrayList<>();

        // thread creation
        ArrayList<Future<SimulationData>> fitnessFutures = new ArrayList<>(population.size());
        for (ReactorGenome currentGenome : population) {
            final ReactorGenome genomeForThread = currentGenome.copy();

            Callable<SimulationData> task = () -> {
                ReactorSimulator threadSimulator = simulators.get();
                threadSimulator.resetState();

                Reactor currentReactor = genomeForThread.toReactor();
                return threadSimulator.runSimulation(currentReactor);
            };

            fitnessFutures.add(executor.submit(task));
        }

        // data gathering from threads
        try {
            for (int i = 0; i < population.size(); i++) {
                ReactorGenome genome = population.get(i);
                EvaluatedGenome evaluatedGenome = new EvaluatedGenome(genome);

                SimulationData simulationData = fitnessFutures.get(i).get();
                evaluatedGenome.setSimulationData(simulationData);
                evaluatedGenomes.add(evaluatedGenome);
            }
        } catch (Exception e) {
            Logger.log(e, "A simulation thread failed");
        }

        return evaluatedGenomes;
    }

    private ArrayList<ReactorGenome> initializePopulation(GAConfig config, ArrayList<ReactorGenome> seedPopulation, Random random) {
        ArrayList<ReactorGenome> population = new ArrayList<>();

        // fill with seedPopulation to start
        for (int i = 0; i < Math.min(seedPopulation.size(), config.evolution.populationSize); i++) {
            population.add(seedPopulation.get(i));
        }

        // fill the rest with random genomes
        for (int i = 0; i < config.evolution.populationSize - seedPopulation.size(); i++) {
            population.add(ReactorGenome.randomGenome(config, random));
        }

        return population;
    }

    private double evaluateGenomeFitness(EvaluatedGenome evaluatedGenome) {
        double fitness = 0.0;

        // Unstable reactors are disqualified, might look into heavily penalizing them in the future to reward experimentation
        // 50% heat is too much, disqualify.
        // TODO: put that in a config somewhere. Actually do we even need this?
        if (evaluatedGenome.getSimulationData().maxTemp > 5000)
            return 0.0;

        SimulationData simulationData = evaluatedGenome.getSimulationData();

        double avgEUOutput = simulationData.avgEUOutput;
        double fuelEfficiency = computeGenomeFuelEfficiency(evaluatedGenome.getGenome(), avgEUOutput);

        // Power output, the basis of the fitness
        fitness += avgEUOutput * this.config.fitness.euOutputWeight;

        // Fuel efficiency bonus. Based on a human designed "meta" reactor with great fuel efficiency and good power output.
        // Uses a sqrt() scaling
        double normalizedEfficiency = Math.sqrt(fuelEfficiency) / Math.sqrt(this.config.fitness.metaFuelEfficiencyTarget);
        fitness += avgEUOutput * normalizedEfficiency * this.config.fitness.fuelEfficiencyWeight;

        if (simulationData.firstComponentBrokenTime < Integer.MAX_VALUE)
            fitness *= this.config.fitness.componentBrokenPenalty;

        double heatPenalty = evaluatedGenome.getSimulationData().maxTemp * this.config.fitness.heatPenaltyMultiplier;
        fitness -= heatPenalty;

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe further penalize reactors that accumulate too much heat in their component (as a % of the heat capacity of the component)

        return fitness;
    }

    private double computeGenomeFuelEfficiency(ReactorGenome genome, double avgEUOutput) {
        int fuelRodCount = genome.getFuelRodCount();
        return fuelRodCount > 0 ? avgEUOutput / (double) fuelRodCount : 0;
    }

    private void printVerbose(boolean verbose, String message, Object... args) {
        if (verbose)
            Logger.log(message, args);
    }

    public static class EvaluatedGenome {
        private ReactorGenome genome;
        private SimulationData simulationData;
        private double fitness;

        public EvaluatedGenome(ReactorGenome genome) {
            this(genome, -1.0);
        }

        public EvaluatedGenome(ReactorGenome genome, double fitness) {
            this.genome = genome;
            this.simulationData = null;
            this.fitness = fitness;
        }

        public ReactorGenome getGenome() {
            return this.genome;
        }

        public void setGenome(ReactorGenome genome) {
            this.genome = genome;
        }

        public SimulationData getSimulationData() {
            return this.simulationData;
        }

        public void setSimulationData(SimulationData simulationData) {
            this.simulationData = simulationData;
        }

        public double getFitness() {
            return this.fitness;
        }

        public void setFitness(double fitness) {
            this.fitness = fitness;
        }

    }
}

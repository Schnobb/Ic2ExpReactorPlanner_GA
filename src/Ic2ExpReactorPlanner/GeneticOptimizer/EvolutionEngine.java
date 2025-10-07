package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.*;
import Ic2ExpReactorPlanner.components.ReactorItem;

import java.util.*;
import java.util.concurrent.*;

public class EvolutionEngine {
    private final GAConfig config;
    private final Random random;

    private final ExecutorService executor;
    private final ThreadLocal<ReactorSimulator> simulatorThreadLocal;

    public EvolutionEngine(GAConfig config) {
        this(config, new Random().nextLong());
    }

    public EvolutionEngine(GAConfig config, long seed) {
        this.config = config;
        this.random = new Random(seed);

        int coreCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(coreCount);
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

        ArrayList<Future<SimulationData>> fitnessFutures = new ArrayList<>(population.size());

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

            fitnessFutures.clear();

            // thread creation
            for (EvaluatedGenome currentGenome : population) {
                final ReactorGenome genomeForThread = currentGenome.getGenome().copy();

                Callable<SimulationData> task = () -> {
                    ReactorSimulator threadSimulator = this.simulatorThreadLocal.get();
                    threadSimulator.resetState();

                    Reactor currentReactor = genomeForThread.toReactor();
                    return threadSimulator.runSimulation(currentReactor);
                };

                fitnessFutures.add(executor.submit(task));
            }

            // data gathering from threads
            double maxEUOutputThisGeneration = -1.0;
            double maxFuelEfficiencyThisGeneration = -1.0;
            try {
                for (int i = 0; i < population.size(); i++) {
                    EvaluatedGenome evaluatedGenome = population.get(i);
                    SimulationData simulationData = fitnessFutures.get(i).get();
                    evaluatedGenome.setSimulationData(simulationData);

                    if (simulationData.avgEUOutput > maxEUOutputThisGeneration)
                        maxEUOutputThisGeneration = simulationData.avgEUOutput;

                    double fuelEfficiency = ComputeGenomeFuelEfficiency(evaluatedGenome.getGenome(), simulationData.avgEUOutput);
                    if (fuelEfficiency > maxFuelEfficiencyThisGeneration)
                        maxFuelEfficiencyThisGeneration = fuelEfficiency;
                }
            } catch (Exception e) {
                Logger.log(Logger.LogLevel.ERROR, "A simulation thread failed: %s", e.getCause() != null ? e.getCause().toString() : e.toString());
                e.printStackTrace();
            }

            // actual fitness computation
            for (EvaluatedGenome currentGenome : population) {
//                double normalizedEUOutput = maxEUOutputThisGeneration > 0 ? currentGenome.getAvgEUOutput() / maxEUOutputThisGeneration : 0;
//                double normalizedFuelEfficiency = maxFuelEfficiencyThisGeneration > 0 ? currentGenome.getFuelEfficiency() / maxFuelEfficiencyThisGeneration : 0;

//                currentGenome.setFitness(EvaluateGenomeFitness(normalizedEUOutput, normalizedFuelEfficiency));
                currentGenome.setFitness(EvaluateGenomeFitness(currentGenome, maxEUOutputThisGeneration, maxFuelEfficiencyThisGeneration));
            }

            int stableCount = 0;
            for (EvaluatedGenome genome : population) {
                if (genome.getFitness() > 0) stableCount++;
            }

            Logger.log(Logger.LogLevel.DEBUG, "Stable designs in generation %d: %d/%d (%.1f%%)", generation, stableCount, population.size(), 100.0 * stableCount / population.size());

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
            Logger.log(Logger.LogLevel.DEBUG, "Diversity in generation %d: %d unique genomes (%.2f%%)", generation, uniqueDesigns.size(), diversityRatio * 100);

            // TODO: parametrize those
            if (diversityRatio < 0.2 && generation > 20) {
                // TODO: also parametrize this
                int injectCount = population.size() / 4;
                Logger.log(Logger.LogLevel.DEBUG, "LOW DIVERSITY IN GENERATION %d. Injecting %d random designs", generation, injectCount);

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

            ReactorItem alphaFuelType = ComponentFactory.getDefaultComponent(alpha.getGenome().getFuelType());
            assert alphaFuelType != null;

            String alphaFuelTypeString = alphaFuelType.name;
            String alphaRender = String.format("%s - %.2fEU/t %s", alphaFuelTypeString, alpha.getSimulationData().avgEUOutput, alpha.getGenome().getERPCode());

            printVerbose(verbose, "Generation %d best fitness: %.2f, took %.2fms. Alpha: %s", generation, bestFitnessInGeneration, generationElapsedTimeMS, alphaRender);
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

    private double EvaluateGenomeFitness(EvaluatedGenome evaluatedGenome, double maxEUOutput, double maxFuelEfficiency) {
        double fitness = 0.0;

        // Unstable reactors are disqualified, might look into heavily penalizing them in the future to reward experimentation
        if (evaluatedGenome.getSimulationData().maxTemp > 0)
            return 0.0;

        SimulationData simulationData = evaluatedGenome.getSimulationData();

        double avgEUOutput = simulationData.avgEUOutput;
        double normalizedEUOutput = maxEUOutput > 0 ? avgEUOutput / maxEUOutput : 0;

        double fuelEfficiency = ComputeGenomeFuelEfficiency(evaluatedGenome.getGenome(), avgEUOutput);
        double normalizedFuelEfficiency = maxFuelEfficiency > 0 ? fuelEfficiency / maxFuelEfficiency : 0;

        fitness += normalizedEUOutput * this.config.fitness.euOutputWeight + normalizedFuelEfficiency * this.config.fitness.fuelEfficiencyWeight;

        if (simulationData.firstComponentBrokenTime < Integer.MAX_VALUE)
            fitness *= this.config.fitness.componentBrokenPenalty;

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe modify by fuel efficiency (EU/t per rod)
        // maybe further penalize reactors that accumulate too much heat in their component (as a % of the heat capacity of the component)

        return fitness;
    }

    private double ComputeGenomeFuelEfficiency(ReactorGenome genome, double avgEUOutput) {
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

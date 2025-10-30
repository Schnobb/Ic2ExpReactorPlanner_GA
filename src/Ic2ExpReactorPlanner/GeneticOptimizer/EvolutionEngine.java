package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.*;
import Ic2ExpReactorPlanner.components.ReactorItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class EvolutionEngine {
    private static final Logger Logger = LoggerFactory.getLogger(EvolutionEngine.class);

    private final GAConfig config;
    private final Random random;
    private final List<ReactorGenome> startingPopulation;

    private final ExecutorService executor;
    private final ThreadLocal<ReactorSimulator> simulatorThreadLocal;

    public IComponentFactory componentFactory;

    public EvolutionEngine(GAConfig config, long seed, IComponentFactory componentFactory) {
        this.config = config;
        this.random = new Random(seed);
        startingPopulation = new ArrayList<>();

        int coreCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(coreCount);
        this.simulatorThreadLocal = ThreadLocal.withInitial(ReactorSimulator::new);

        this.componentFactory = componentFactory;
    }

    public void preSeedGen0(List<ReactorGenome> startingPopulation) {
        this.startingPopulation.addAll(startingPopulation);
    }

    private double overallBestFitness;
    private int generation;
    private boolean exploratoryPhase;
    private long globalStartTime;
    private List<ReactorGenome> population;

    public void init() {
        List<EvaluatedGenome> evaluatedPopulation = null;

        overallBestFitness = -1;
        generation = 0;
        exploratoryPhase = true;

        globalStartTime = System.nanoTime();

        Logger.trace("Evolution settings: {}", this.config.evolution.toString());
        Logger.trace("Speciation settings: {}", this.config.speciation.toString());
        Logger.trace("Starting evolution...");

        population = initializePopulation(this.config, this.random, this.startingPopulation);
        Logger.trace("Initial population of {} candidates created. Seeded with {} pre-configured reactors.", population.size(), this.startingPopulation.size());
    }

    public List<EvaluatedGenome> run() {
        init();

        List<EvaluatedGenome> evaluatedPopulation = null;

        while (generation < this.config.evolution.maxGeneration) {
            evaluatedPopulation = runTick();
        }

        end();

        return evaluatedPopulation;
    }

    public List<EvaluatedGenome> runTick() {
        long generationStartTime = System.nanoTime();

        // Alternate between exploratory phases and refinement phases
        // TODO: decouple this stuff and handle it in its own bubble
        String phaseName = "exploratory";
        if (generation % this.config.evolution.phaseLengthGenerations == 0) {
            if (generation > 0)
                exploratoryPhase = !exploratoryPhase;
            if (!exploratoryPhase)
                phaseName = "refinement";
            Logger.trace("starting {} phase", phaseName);
        }

        assert !population.isEmpty() : "Population list cannot be empty.";

        // Run simulation and gather the SimulationData from each run
        List<EvaluatedGenome> simulatedPopulation = simulatePopulation(population, this.simulatorThreadLocal, this.executor);
        // Evaluate the population's fitness's
        var evaluatedPopulation = evaluatePopulationsFitness(simulatedPopulation);
        // Analyze the current generation's data (stable count, total fitness, etc.)
        GenerationSummary generationSummary = summarizeGeneration(evaluatedPopulation);

        Logger.debug("Valid designs in generation %d: %d/%d (%.1f%%)".formatted(generation, generationSummary.stableDesignsCount, evaluatedPopulation.size(), 100.0 * generationSummary.stableDesignsCount / evaluatedPopulation.size()));

        if (generationSummary.alpha.getFitness() > overallBestFitness) {
            overallBestFitness = generationSummary.alpha.getFitness();
        }

        // Don't need a new population for the last generation
        if (generation < this.config.evolution.maxGeneration - 1) {
            // Create the new population for next generation
            population = breedNextGeneration(config, random, evaluatedPopulation, exploratoryPhase, generation);
        }

        long generationEndTime = System.nanoTime();
        double generationElapsedTimeMS = (generationEndTime - generationStartTime) / 1e6;

        var alphaFuelType = componentFactory.getDefaultComponent(generationSummary.alpha.getGenome().getFuelType());
        assert alphaFuelType != null;
        String alphaFuelTypeString = alphaFuelType.getName();
        String alphaRender = String.format("%s - %.2fEU/t %s", alphaFuelTypeString, generationSummary.alpha.getSimulationData().avgEUOutput, generationSummary.alpha.getGenome().getERPCode());

        Logger.info("Generation %d [%s] best fitness: %.2f, avg. fitness: %.2f, took %.2fms. Alpha: %s".formatted(generation, phaseName, generationSummary.alpha.getFitness(), generationSummary.totalFitness / (double) population.size(), generationElapsedTimeMS, alphaRender));
        generation++;

        return evaluatedPopulation;
    }

    public void end() {
        long globalEndTime = System.nanoTime();
        double globalElapsedTimeMS = (globalEndTime - globalStartTime) / 1e6;
        Logger.trace("Evolution process finished! Best fitness: %.2f, took %.2fms".formatted(overallBestFitness, globalElapsedTimeMS));

        // executor cleanup
        try {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                Logger.warn("executor.awaitTermination timed out. Is there a threading issue?");
        } catch (Exception e) {
            Logger.warn("executor.awaitTermination had to be interrupted. Is there a threading issue? [" + e.getCause() + "]");
            Thread.currentThread().interrupt();
        }
    }

    private List<EvaluatedGenome> evaluatePopulationsFitness(List<EvaluatedGenome> simulatedPopulation) {
        return simulatedPopulation.stream()
                .map(simulatedGenome -> {
                    double fitness = evaluateGenomeFitness(simulatedGenome);
                    EvaluatedGenome evaluatedGenome = new EvaluatedGenome(simulatedGenome.getGenome(), fitness);
                    evaluatedGenome.setSimulationData(simulatedGenome.getSimulationData());

                    return evaluatedGenome;
                })
                .collect(Collectors.toList());
    }

    private GenerationSummary summarizeGeneration(List<EvaluatedGenome> evaluatedPopulation) {
        int stableCount = 0;
        double totalFitness = 0;
        EvaluatedGenome alpha = evaluatedPopulation.get(0);
        for (EvaluatedGenome evaluatedGenome : evaluatedPopulation) {
            totalFitness += evaluatedGenome.getFitness();
            if (evaluatedGenome.getFitness() > 0) stableCount++;
            if (evaluatedGenome.getFitness() > alpha.getFitness()) alpha = evaluatedGenome;
        }

        return new GenerationSummary(alpha, stableCount, totalFitness);
    }

    private List<ReactorGenome> breedNextGeneration(GAConfig config, Random random, List<EvaluatedGenome> evaluatedPopulation, boolean exploratoryPhase, int generation) {
        List<ReactorGenome> population = evaluatedPopulation.stream().
                map(EvaluatedGenome::getGenome)
                .collect(Collectors.toList());

        double populationDiversityMetric = calculateSpeciesDiversity(config, population);
        Logger.debug("Diversity in generation %d: %.2f%% individual species".formatted(generation, populationDiversityMetric * 100));

        int randomGenomesInjectCount = 0;
        if (populationDiversityMetric < config.evolution.lowDiversityThreshold) {
            randomGenomesInjectCount = (int) Math.floor((double) evaluatedPopulation.size() * config.evolution.lowDiversityCullingRatio);
            Logger.debug("LOW DIVERSITY IN GENERATION %d. Injecting %d random designs into next generation".formatted(generation, randomGenomesInjectCount));
        }

        // Create the new generation starting with the alphas
        List<ReactorGenome> newPopulation = initializeNewPopulationWithAlphas(config.evolution.alphaCount, evaluatedPopulation);

        // Fill the rest of the population with the tournament selection breeding
        int tournamentCount = config.evolution.populationSize - newPopulation.size() - randomGenomesInjectCount;
        ReactorGenome.MutationStatTracker statTracker = new ReactorGenome.MutationStatTracker();

        for (int i = 0; i < tournamentCount; i++) {
            // Parent selection
            ReactorGenome parentA = selectParentViaTournament(config, random, evaluatedPopulation);
            ReactorGenome parentB = selectParentViaTournament(config, random, evaluatedPopulation);

            // Breeding phase
            ReactorGenome childGenome = ReactorGenome.crossBreed(config, parentA, parentB, random, componentFactory);

            // Mutation phase
            GAConfig.PhaseProbabilities mutationProbabilities = exploratoryPhase ? config.mutation.exploration : config.mutation.refinement;
            childGenome.tryMutation(config, mutationProbabilities, random, statTracker);

            newPopulation.add(childGenome);
        }

        // Inject random genomes into new population to spike diversity if diversity is too low
        for (int i = 0; i < randomGenomesInjectCount; i++) {
            newPopulation.add(ReactorGenome.randomGenome(config, random, componentFactory));
        }

        Logger.debug("Mutations count in generation {}: {}", generation, statTracker);
        return newPopulation;
    }

    private List<ReactorGenome> initializeNewPopulationWithAlphas(int alphaCount, List<EvaluatedGenome> evaluatedPopulation) {
        List<ReactorGenome> newPopulation = new ArrayList<>();

        evaluatedPopulation.sort(Comparator.comparingDouble(EvaluatedGenome::getFitness).reversed());
        for (int i = 0; i < alphaCount; i++) {
            newPopulation.add(evaluatedPopulation.get(i).getGenome().copy());
        }

        return newPopulation;
    }

    private ReactorGenome selectParentViaTournament(GAConfig config, Random random, List<EvaluatedGenome> evaluatedPopulation) {
        List<EvaluatedGenome> tournamentSelection = new ArrayList<>();
        for (int k = 0; k < config.evolution.tournamentSizeK; k++) {
            tournamentSelection.add(evaluatedPopulation.get(random.nextInt(evaluatedPopulation.size())));
        }

        assert !tournamentSelection.isEmpty() : "Tournament competitor list cannot be empty.";

        return Collections.max(tournamentSelection, Comparator.comparingDouble(EvaluatedGenome::getFitness)).getGenome();
    }

    private double calculateSpeciesDiversity(GAConfig config, List<ReactorGenome> population) {
        if (population.isEmpty()) return 0.0;

        List<ReactorGenome> speciesRepresentatives = new ArrayList<>();
        for (ReactorGenome individual : population) {
            boolean newSpecies = true;
            for (ReactorGenome representative : speciesRepresentatives) {
                if (ReactorGenome.calculateSimilarity(config, individual, representative) > config.speciation.speciesSimilarityThreshold) {
                    newSpecies = false;
                    break;
                }
            }

            if (newSpecies)
                speciesRepresentatives.add(individual);
        }

        return (double) speciesRepresentatives.size() / (double) population.size();
    }

    private List<EvaluatedGenome> simulatePopulation(List<ReactorGenome> population, ThreadLocal<ReactorSimulator> simulators, ExecutorService executor) {
        List<EvaluatedGenome> evaluatedGenomes = new ArrayList<>();

        // thread creation
        List<Future<SimulationData>> fitnessFutures = new ArrayList<>(population.size());
        for (ReactorGenome currentGenome : population) {
            var simThread = new SimThread(currentGenome.copy());
            fitnessFutures.add(executor.submit(simThread));
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
            Logger.error("A simulation thread failed", e);
        }

        return evaluatedGenomes;
    }

    private List<ReactorGenome> initializePopulation(GAConfig config, Random random, List<ReactorGenome> seedPopulation) {
        List<ReactorGenome> population = new ArrayList<>();

        // fill with seedPopulation to start
        for (int i = 0; i < Math.min(seedPopulation.size(), config.evolution.populationSize); i++) {
            population.add(seedPopulation.get(i));
        }

        // fill the rest with random genomes
        for (int i = 0; i < config.evolution.populationSize - seedPopulation.size(); i++) {
            population.add(ReactorGenome.randomGenome(config, random, componentFactory));
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

    private static class GenerationSummary {
        public EvaluatedGenome alpha;
        public int stableDesignsCount;
        public double totalFitness;

        public GenerationSummary(EvaluatedGenome alpha, int stableDesignsCount, double totalFitness) {
            this.alpha = alpha;
            this.stableDesignsCount = stableDesignsCount;
            this.totalFitness = totalFitness;
        }
    }

    private static class SimThread implements Callable<SimulationData> {

        private final ReactorGenome reactorGenome;

        private SimThread(ReactorGenome reactorGenome) {
            this.reactorGenome = reactorGenome;
        }

        @Override
        public SimulationData call() throws Exception {
            var reactorSimulator = new ReactorSimulator();
            var currentReactor = reactorGenome.toReactor();
            return reactorSimulator.runSimulation(currentReactor);
        }
    }
}

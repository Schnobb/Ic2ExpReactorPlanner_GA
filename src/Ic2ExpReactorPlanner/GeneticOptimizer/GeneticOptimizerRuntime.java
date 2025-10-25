package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.ComponentFactory;
import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GeneticOptimizerRuntime {
    private static final Logger Logger = LoggerFactory.getLogger(GeneticOptimizerRuntime.class);

    static void main(String[] args) {
        GAConfig config = GAConfig.loadConfig(args.length > 0 ? args[0] : null);

        if (config == null) {
            System.exit(1);
        }

        boolean seedProvided = false;
        long providedSeed = -1;
        if (args.length > 1) {
            seedProvided = true;
            providedSeed = Long.parseLong(args[1]);
        }

        Logger.info("Loaded config '{}'.", config.getConfigName());

        // Create the seed
        long seed = seedProvided ? providedSeed : new SecureRandom().nextLong();
        Logger.info("Setting up evolution with seed {}", seed);

        // Set GTNH behavior on fuel since we want to generate GTNH reactors
        FuelRod.setGTNHBehavior(true);

        var componentFactory = ComponentFactory.getInstance();

        // Create the evolution engine
        EvolutionEngine evolutionEngine = new EvolutionEngine(config, seed, componentFactory);

        // Load seed reactors if configured
        if (config.evolution.seedFile != null && !config.evolution.seedFile.isEmpty()) {
            List<ReactorGenome> seedGenomes = SeedFileLoader.LoadSeedFile(config, config.evolution.seedFile, componentFactory);
            evolutionEngine.preSeedGen0(seedGenomes);
        }

        // Run
        List<EvolutionEngine.EvaluatedGenome> finalPopulation = evolutionEngine.run();

        // Show the top 10 reactor designs
        List<EvolutionEngine.EvaluatedGenome> top10 = getTop10Species(config, finalPopulation);

        Logger.info("Top 10 species:");
        for (int i = 0; i < Math.min(10, top10.size()); i++) {
            EvolutionEngine.EvaluatedGenome evaluatedGenome = top10.get(i);

            var fuelType = componentFactory.getDefaultComponent(evaluatedGenome.getGenome().getFuelType());
            assert fuelType != null;
            String fuelTypeName = fuelType.getName();

            Logger.info("%2d - %s Fitness: %7.2f; Output: %7.2fEU/t - %s".formatted(i + 1, fuelTypeName, evaluatedGenome.getFitness(), evaluatedGenome.getSimulationData().avgEUOutput, evaluatedGenome.getGenome().getERPCode()));
        }
    }

    private static List<EvolutionEngine.EvaluatedGenome> getTop10Species(GAConfig config, List<EvolutionEngine.EvaluatedGenome> population) {
        List<EvolutionEngine.EvaluatedGenome> top10Species = new ArrayList<>();

        // Create a sorted copy of the population, the best fitness first
        List<EvolutionEngine.EvaluatedGenome> sortedPopulation = new ArrayList<>(population);
        sortedPopulation.sort(Comparator.comparing(EvolutionEngine.EvaluatedGenome::getFitness).reversed());

        // Always add the alpha
        top10Species.add(sortedPopulation.get(0));

        for (EvolutionEngine.EvaluatedGenome candidate : sortedPopulation) {
            if (top10Species.size() >= 10) break;

            boolean distinctSpecies = true;
            for (EvolutionEngine.EvaluatedGenome champion : top10Species) {
                if (ReactorGenome.calculateSimilarity(config, champion.getGenome(), candidate.getGenome()) > config.speciation.speciesSimilarityThreshold) {
                    distinctSpecies = false;
                    break;
                }
            }

            if (distinctSpecies)
                top10Species.add(candidate);
        }

        return top10Species;
    }

    private static boolean alreadyInTop10(List<EvolutionEngine.EvaluatedGenome> top10, EvolutionEngine.EvaluatedGenome candidate) {
        for (EvolutionEngine.EvaluatedGenome evaluatedGenome : top10) {
            if (candidate.getGenome().equals(evaluatedGenome.getGenome()))
                return true;
        }

        return false;
    }
}

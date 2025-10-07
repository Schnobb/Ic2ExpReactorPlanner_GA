package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.ComponentFactory;
import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;

public class GeneticOptimizerRuntime {
    public static void main(String[] args) {
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

        Path logDirectory = Paths.get("logs");
        try {
            Logger.setLogFileFromDirectory("GeneticOptimizerRuntime", logDirectory);
        } catch (IOException e) {
            System.err.println("Error: Could not validate log directory '" + logDirectory + "'.");
            e.printStackTrace();
        }

        Logger.log("Loaded config '%s'.", config.getConfigName());

        // Create the seed
        long seed = seedProvided ? providedSeed : new SecureRandom().nextLong();
        Logger.log("Setting up evolution with seed %d", seed);

        // Set GTNH behavior on fuel since we want to generate GTNH reactors
        FuelRod.setGTNHBehavior(true);

        // Create the evolution engine
        EvolutionEngine evolutionEngine = new EvolutionEngine(config, seed);

        // Load seed reactors if configured
        if (config.evolution.seedFile != null && !config.evolution.seedFile.isEmpty()) {
            ArrayList<ReactorGenome> seedGenomes = SeedFileLoader.LoadSeedFile(config, config.evolution.seedFile);
            evolutionEngine.preSeedGen0(seedGenomes);
        }

        // Run
        ArrayList<EvolutionEngine.EvaluatedGenome> finalPopulation = evolutionEngine.Run(true);

        // Show the top 10 reactor designs
        finalPopulation.sort(Comparator.comparingDouble(EvolutionEngine.EvaluatedGenome::getFitness).reversed());
        ArrayList<EvolutionEngine.EvaluatedGenome> top10 = new ArrayList<>(10);

        for (EvolutionEngine.EvaluatedGenome evaluatedGenome : finalPopulation) {
            if (!alreadyInTop10(top10, evaluatedGenome))
                top10.add(evaluatedGenome);
        }

        Logger.log("");
        Logger.log("Final top 10:");
        for (int i = 0; i < Math.min(10, top10.size()); i++) {
            EvolutionEngine.EvaluatedGenome evaluatedGenome = top10.get(i);

            ReactorItem fuelType = ComponentFactory.getDefaultComponent(evaluatedGenome.getGenome().getFuelType());
            assert fuelType != null;
            String fuelTypeName = fuelType.name;

            Logger.log("%2d - %s Fitness: %7.2f; Output: %7.2fEU/t - %s", i + 1, fuelTypeName, evaluatedGenome.getFitness(), evaluatedGenome.getSimulationData().avgEUOutput, evaluatedGenome.getGenome().getERPCode());
        }
    }

    private static boolean alreadyInTop10(ArrayList<EvolutionEngine.EvaluatedGenome> top10, EvolutionEngine.EvaluatedGenome candidate) {
        for (EvolutionEngine.EvaluatedGenome evaluatedGenome : top10) {
            if (candidate.getGenome().equals(evaluatedGenome.getGenome()))
                return true;
        }

        return false;
    }
}

package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.Logger;
import Ic2ExpReactorPlanner.Reactor;
import Ic2ExpReactorPlanner.SimulationData;

import java.util.ArrayList;
import java.util.Comparator;

public class GeneticOptimizerRuntime {
    public static void main(String[] args) {
        GAConfig config = GAConfig.loadConfig(args.length > 1 ? args[1] : null);
        if (config == null)
            System.exit(1);

        // Create and run the evolution engine
        EvolutionEngine evolutionEngine = new EvolutionEngine(config);
        ArrayList<EvolutionEngine.EvaluatedGenome> finalPopulation = evolutionEngine.Run(true);

        // Show the top 10 reactor designs
        finalPopulation.sort(Comparator.comparingDouble(EvolutionEngine.EvaluatedGenome::getFitness).reversed());

        Logger.log("");
        Logger.log("Final top 10:");
        Logger.log("");
        for (int i = 0; i < 10; i++) {
            Logger.log("%d - %.2f - %s%n", i, finalPopulation.get(i).getFitness(), finalPopulation.get(i).getGenome().getERPCode());
        }
    }
}

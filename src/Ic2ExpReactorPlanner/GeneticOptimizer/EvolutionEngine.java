package Ic2ExpReactorPlanner.GeneticOptimizer;

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

    public void Run() {

    }
}

package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.GeneticOptimizer.EvolutionEngine;
import Ic2ExpReactorPlanner.GeneticOptimizer.GAConfig;
import Ic2ExpReactorPlanner.old.ComponentFactory;
import org.junit.Assert;
import org.junit.Test;

public class ReactorTest {

    @Test
    public void compareTest() {
        var seed = -2808806509836082453L;

        var config = GAConfig.loadConfig(null);

        EvolutionEngine evolutionEngineA = new EvolutionEngine(config, seed, Ic2ExpReactorPlanner.old.ComponentFactory.getInstance());
        evolutionEngineA.init();
        EvolutionEngine evolutionEngineB = new EvolutionEngine(config, seed, Ic2ExpReactorPlanner.ComponentFactory.getInstance());
        evolutionEngineB.init();

        for (int i = 0; i < 10; i++) {
            var listA = evolutionEngineA.runTick();
            var listB = evolutionEngineB.runTick();
            for (var genomeNb = 0; genomeNb < listA.size(); genomeNb++) {
                var genomeA = listA.get(genomeNb);
                var genomeB = listB.get(genomeNb);
                Assert.assertEquals(genomeA.getGenome().getERPCode(), genomeB.getGenome().getERPCode());
                Assert.assertEquals(genomeA.getFitness(), genomeB.getFitness(), Double.MIN_VALUE);
            }
        }
    }

}

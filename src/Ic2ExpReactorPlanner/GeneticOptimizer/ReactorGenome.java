package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.ComponentFactory;
import Ic2ExpReactorPlanner.IComponentFactory;
import Ic2ExpReactorPlanner.Reactor;
import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

public class ReactorGenome {
    public static final int FUEL_VALUE = 999;

    // Genome data
    private int fuelType;
    private final int[] reactorLayout;

    // Util stuff
    private int fuelRodCount;

    // Config stuff
    private final GAConfig config;

    private final IComponentFactory componentFactory;

    public ReactorGenome(GAConfig config, IComponentFactory componentFactory) {
        this.config = config;
        this.componentFactory = componentFactory;

        this.fuelType = -1;
        this.reactorLayout = new int[this.config.reactor.rowCount * this.config.reactor.colCount];
        this.fuelRodCount = -1;
    }

    public int getFuelType() {
        return fuelType;
    }

    public int[] getReactorLayout() {
        return reactorLayout;
    }

    public void setFuelType(int type) {
        this.fuelType = type;
    }

    public static ReactorGenome randomGenome(GAConfig config, Random random, IComponentFactory componentFactory) {
        ReactorGenome genome = new ReactorGenome(config, componentFactory);

        genome.fuelType = config.fuels.valid[random.nextInt(config.fuels.valid.length)];
        for (int i = 0; i < genome.reactorLayout.length; i++)
            genome.reactorLayout[i] = config.components.valid[random.nextInt(config.components.valid.length)];

        return genome;
    }

    public static String serialize(ReactorGenome genome) {
        Base64.Encoder b64Encoder = Base64.getUrlEncoder();
        String buffer = genome.toString();
        return b64Encoder.encodeToString(buffer.getBytes(StandardCharsets.UTF_8));
    }

    public static ReactorGenome deserialize(GAConfig config, String serializedGenome, IComponentFactory componentFactory) {
        ReactorGenome genome = new ReactorGenome(config, componentFactory);

        Base64.Decoder urlDecoder = Base64.getUrlDecoder();
        String buffer = new String(urlDecoder.decode(serializedGenome), StandardCharsets.UTF_8);

        String[] genomeData = buffer.split("\\|");
        genome.fuelType = Integer.parseInt(genomeData[0]);

        String[] genomeLayoutData = genomeData[1].split(",");
        for (int i = 0; i < genome.reactorLayout.length; i++) {
            genome.reactorLayout[i] = Integer.parseInt(genomeLayoutData[i]);
        }

        return genome;
    }

    public static double calculateSimilarity(GAConfig config, ReactorGenome genomeA, ReactorGenome genomeB) {
        // thinking about maybe giving some leniency to the fuel type. Perhaps a reactor that only mutated its fuel type could be considered related to its parent?
        if (genomeA.getFuelType() != genomeB.getFuelType())
            return 0.0;

        double fuelSimilarityScore = calculateFuelLayoutSimilarity(genomeA, genomeB);
        double componentsLayoutSimilarityScore = calculateComponentsLayoutSimilarity(genomeA, genomeB);

        return (fuelSimilarityScore * config.speciation.fuelLayoutWeight) + (componentsLayoutSimilarityScore * config.speciation.componentsLayoutWeight);
    }

    private static double calculateFuelLayoutSimilarity(ReactorGenome genomeA, ReactorGenome genomeB) {
        assert genomeA.reactorLayout.length == genomeB.reactorLayout.length;

        int intersection = 0; // where both have fuel
        int union = 0; // where only one has fuel

        for (int i = 0; i < genomeA.reactorLayout.length; i++) {
            boolean genomeAHasFuel = genomeA.isFuelRodAt(i);
            boolean genomeBHasFuel = genomeB.isFuelRodAt(i);

            if (genomeAHasFuel || genomeBHasFuel) {
                union++;

                if (genomeAHasFuel && genomeBHasFuel)
                    intersection++;
            }
        }

        if (union == 0 && intersection == 0)
            return 1.0;

        return union > 0 ? (double) intersection / (double) union : 0.0;
    }

    private static double calculateComponentsLayoutSimilarity(ReactorGenome genomeA, ReactorGenome genomeB) {
        assert genomeA.reactorLayout.length == genomeB.reactorLayout.length;

        int matchingCells = 0;
        int relevantCells = 0;
        int totalCells = genomeA.reactorLayout.length;

        for (int i = 0; i < totalCells; i++) {
            if (genomeA.isFuelRodAt(i) && genomeB.isFuelRodAt(i))
                continue;

            relevantCells++;
            if (genomeA.reactorLayout[i] == genomeB.reactorLayout[i])
                matchingCells++;
        }

        return relevantCells > 0 ? (double) matchingCells / (double) relevantCells : 0.0;
    }

    public boolean isFuelRodAt(int index) {
        return this.reactorLayout[index % this.reactorLayout.length] == FUEL_VALUE;
    }

    public String getERPCode() {
        var reactor = toReactor();
        return reactor.getCode();
    }

    public static ReactorGenome fromReactor(GAConfig config, Reactor reactor, IComponentFactory componentFactory) {
        ReactorGenome genome = new ReactorGenome(config, componentFactory);

        int fuelType = -1;
        int i = 0;
        for (int y = 0; y < config.reactor.rowCount; y++) {
            for (int x = 0; x < config.reactor.colCount; x++) {
                var component = reactor.getComponentAt(y, x);
                int componentId = -1;

                if (component != null)
                    componentId = component.getId();

                var defaultComponent = componentFactory.getDefaultComponent(componentId);
                if (defaultComponent instanceof FuelRod || defaultComponent instanceof Ic2ExpReactorPlanner.old.components.FuelRod) {
                    if (fuelType < 0) {
                        fuelType = componentId;
                        genome.fuelType = fuelType;
                    }

                    genome.reactorLayout[i] = FUEL_VALUE;
                } else {
                    genome.reactorLayout[i] = componentId;
                }

                i++;
            }
        }

        return genome;
    }

    public static ReactorGenome crossBreed(GAConfig config, ReactorGenome parentA, ReactorGenome parentB, Random random, IComponentFactory componentFactory) {
        ReactorGenome newGenome = new ReactorGenome(config, componentFactory);

        newGenome.fuelType = random.nextDouble() < 0.5 ? parentA.fuelType : parentB.fuelType;

        int crossoverPoint1 = random.nextInt(newGenome.reactorLayout.length);
        int crossoverPoint2 = random.nextInt(newGenome.reactorLayout.length);

        int crossoverStart = Math.min(crossoverPoint1, crossoverPoint2);
        int crossoverEnd = Math.max(crossoverPoint1, crossoverPoint2);

        for (int i = 0; i < newGenome.reactorLayout.length; i++) {
            int geneValue = parentA.reactorLayout[i];

            if (i >= crossoverStart && i < crossoverEnd)
                geneValue = parentB.reactorLayout[i];

            newGenome.reactorLayout[i] = geneValue;
        }

        return newGenome;
    }

    public Reactor toReactor() {
        var reactor = new Reactor(this.componentFactory);

        int i = 0;
        for (int y = 0; y < this.config.reactor.rowCount; y++) {
            for (int x = 0; x < this.config.reactor.colCount; x++) {
                int componentId = this.reactorLayout[i];

                if (componentId < 0) {
                    i++;
                    continue;
                }

                if (componentId == FUEL_VALUE) {
                    componentId = this.fuelType;
                }

                var component = componentFactory.createComponent(componentId);
                reactor.setComponentAt(y, x, component);

                i++;
            }
        }

        return reactor;
    }

    public void tryMutation(GAConfig config, GAConfig.PhaseProbabilities probabilities, Random random) {
        tryMutation(config, probabilities, random, null);
    }

    public void tryMutation(GAConfig config, GAConfig.PhaseProbabilities probabilities, Random random, MutationStatTracker mutationStatTracker) {
        // fuel type mutation
        if (random.nextDouble() < probabilities.probabilityFuelMutation) {
            this.fuelType = config.fuels.valid[random.nextInt(config.fuels.valid.length)];
            if (mutationStatTracker != null) mutationStatTracker.fuelMutationCount++;
        }

        // single layout mutation (refinement)
        if (random.nextDouble() < probabilities.probabilityLayoutMutation) {
            this.reactorLayout[random.nextInt(this.reactorLayout.length)] = config.components.valid[random.nextInt(config.components.valid.length)];
            if (mutationStatTracker != null) mutationStatTracker.layoutMutationCount++;
        }

        // per slot layout mutation (exploration)
        if (probabilities.probabilityLayoutPerSlotMutation > 0) {
            for (int i = 0; i < this.reactorLayout.length; i++) {
                if (random.nextDouble() < probabilities.probabilityLayoutPerSlotMutation) {
                    this.reactorLayout[i] = config.components.valid[random.nextInt(config.components.valid.length)];
                    if (mutationStatTracker != null) mutationStatTracker.layoutPerSlotMutationCount++;
                }
            }
        }
    }

    public int getFuelRodCount() {
        if (fuelRodCount < 0) {
            fuelRodCount = 0;
            for (int componentId : this.getReactorLayout()) {
                if (componentId == ReactorGenome.FUEL_VALUE) {
                    fuelRodCount++;
                }
            }
        }

        return fuelRodCount;
    }

    public ReactorGenome copy() {
        ReactorGenome newGenome = new ReactorGenome(this.config, this.componentFactory);
        newGenome.fuelType = this.fuelType;
        System.arraycopy(this.reactorLayout, 0, newGenome.reactorLayout, 0, this.reactorLayout.length);

        return newGenome;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        buffer.append(this.fuelType).append("|");

        for (int i = 0; i < this.reactorLayout.length; i++) {
            buffer.append(this.reactorLayout[i]);

            if (i < this.reactorLayout.length - 1)
                buffer.append(",");
        }

        return buffer.toString();
    }

    @Override
    public int hashCode() {
        int result = fuelType;
        result = 31 * result + Arrays.hashCode(reactorLayout);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReactorGenome that = (ReactorGenome) o;
        return fuelType == that.fuelType && Arrays.equals(reactorLayout, that.reactorLayout);
    }

    public static class MutationStatTracker {
        public int fuelMutationCount;
        public int layoutMutationCount;
        public int layoutPerSlotMutationCount;

        public MutationStatTracker() {
            this.fuelMutationCount = 0;
            this.layoutMutationCount = 0;
            this.layoutPerSlotMutationCount = 0;
        }

        @Override
        public String toString() {
            return String.format("Fuel Mutations = %d; Layout Mutations = %d; Layout Per Slot Mutations = %d", this.fuelMutationCount, this.layoutMutationCount, this.layoutPerSlotMutationCount);
        }
    }
}

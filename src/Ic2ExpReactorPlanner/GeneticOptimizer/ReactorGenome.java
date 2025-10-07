package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.ComponentFactory;
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

    public ReactorGenome(GAConfig config) {
        this.config = config;

        this.fuelType = -1;
        this.reactorLayout = new int[this.config.reactor.rowCount * this.config.reactor.colCount];
        this.fuelRodCount = -1;
    }

    public int getFuelType() { return fuelType; }
    public int[] getReactorLayout() { return reactorLayout; }
    public void setFuelType(int type) { this.fuelType = type; }

    public static ReactorGenome randomGenome(GAConfig config, Random random) {
        ReactorGenome genome = new ReactorGenome(config);

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

    public static ReactorGenome deserialize(GAConfig config, String serializedGenome) {
        ReactorGenome genome = new ReactorGenome(config);

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

    public String getERPCode() {
        Reactor reactor = toReactor();
        return reactor.getCode();
    }

    public static ReactorGenome fromReactor(GAConfig config, Reactor reactor) {
        ReactorGenome genome = new ReactorGenome(config);

        int fuelType = -1;
        int i = 0;
        for (int y = 0; y < config.reactor.rowCount; y++) {
            for (int x = 0; x < config.reactor.colCount; x++) {
                ReactorItem component = reactor.getComponentAt(y, x);
                int componentId = -1;

                if (component != null)
                    componentId = component.id;

                ReactorItem defaultComponent = ComponentFactory.getDefaultComponent(componentId);
                if (defaultComponent instanceof FuelRod) {
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

    public static ReactorGenome crossBreed(GAConfig config, ReactorGenome parentA, ReactorGenome parentB, Random random) {
        ReactorGenome newGenome = new ReactorGenome(config);

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
        Reactor reactor = new Reactor();

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

                ReactorItem component = ComponentFactory.createComponent(componentId);
                reactor.setComponentAt(y, x, component);

                i++;
            }
        }

        return reactor;
    }

    public void tryMutation(GAConfig config, GAConfig.PhaseProbabilities probabilities, Random random) {
        // fuel type mutation
        if (random.nextDouble() < probabilities.probabilityFuelMutation)
            this.fuelType = config.fuels.valid[random.nextInt(config.fuels.valid.length)];

        // single layout mutation (refinement)
        if (random.nextDouble() < probabilities.probabilityLayoutMutation)
            this.reactorLayout[random.nextInt(this.reactorLayout.length)] = config.components.valid[random.nextInt(config.components.valid.length)];

        // per slot layout mutation (exploration)
        if (probabilities.probabilityLayoutPerSlotMutation > 0) {
            for (int i = 0; i < this.reactorLayout.length; i++) {
                if (random.nextDouble() < probabilities.probabilityLayoutPerSlotMutation)
                    this.reactorLayout[i] = config.components.valid[random.nextInt(config.components.valid.length)];
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
        ReactorGenome newGenome = new ReactorGenome(this.config);
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
}

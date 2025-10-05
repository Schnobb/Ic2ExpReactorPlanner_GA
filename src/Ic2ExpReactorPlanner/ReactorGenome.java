package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

public class ReactorGenome {
    private static final int REACTOR_MAX_ROW = 6;
    private static final int REACTOR_MAX_COL = 9;
    private static final int REACTOR_GRID_SIZE = REACTOR_MAX_ROW * REACTOR_MAX_COL;
    private static final int FUEL_VALUE = 999;

    private static final double PROB_MUTATE_FUEL_TYPE = 0.001;
    private static final double PROB_MUTATE_LAYOUT = 0.025;

    private static final int[] validComponents = {-1, 999, /*9,*/ 10, 11, 12, 13, /*17,*/ 18, 19, 20, 35};
    private static final int[] validFuels = {1, 2, 3, 4, 5, 6, 26, 27, 28};

    private int fuelType;
    private final int[] reactorLayout;

    public ReactorGenome() {
        fuelType = -1;
        reactorLayout = new int[REACTOR_GRID_SIZE];
    }

    public static ReactorGenome randomGenome(Random random) {
        ReactorGenome genome = new ReactorGenome();

        genome.fuelType = validFuels[random.nextInt(validFuels.length)];
        for (int i = 0; i < genome.reactorLayout.length; i++)
            genome.reactorLayout[i] = validComponents[random.nextInt(validComponents.length)];

        return genome;
    }

    public static String serialize(ReactorGenome genome) {
        Base64.Encoder b64Encoder = Base64.getUrlEncoder();
        String buffer = genome.toString();
        return b64Encoder.encodeToString(buffer.getBytes(StandardCharsets.UTF_8));
    }

    public static ReactorGenome deserialize(String serializedGenome) {
        ReactorGenome genome = new ReactorGenome();

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

    public static ReactorGenome fromReactor(Reactor reactor) {
        ReactorGenome genome = new ReactorGenome();

        int fuelType = -1;
        int i = 0;
        for (int y = 0; y < REACTOR_MAX_ROW; y++) {
            for (int x = 0; x < REACTOR_MAX_COL; x++) {
                ReactorItem component = reactor.getComponentAt(x, y);
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

    public static ReactorGenome crossBreed(ReactorGenome parentA, ReactorGenome parentB, Random random) {
        ReactorGenome newGenome = new ReactorGenome();

        newGenome.fuelType = parentA.fuelType;

        int crossoverPoint1 = random.nextInt(newGenome.reactorLayout.length);
        int crossoverPoint2 = random.nextInt(newGenome.reactorLayout.length);

        int crossoverStart = Math.min(crossoverPoint1, crossoverPoint2);
        int crossoverEnd = Math.max(crossoverPoint1, crossoverPoint2);

        for(int i = 0; i < newGenome.reactorLayout.length; i++){
            int geneValue = parentA.reactorLayout[i];

            if(i >= crossoverStart && i < crossoverEnd)
                geneValue = parentB.reactorLayout[i];

            newGenome.reactorLayout[i] = geneValue;
        }

        return newGenome;
    }

    public Reactor toReactor() {
        Reactor reactor = new Reactor();

        int i = 0;
        for (int y = 0; y < REACTOR_MAX_ROW; y++) {
            for (int x = 0; x < REACTOR_MAX_COL; x++) {
                int componentId = reactorLayout[i];

                if (componentId < 0)
                    continue;

                if (componentId == FUEL_VALUE) {
                    componentId = fuelType;
                }

                ReactorItem component = ComponentFactory.createComponent(componentId);
                reactor.setComponentAt(x, y, component);

                i++;
            }
        }

        return reactor;
    }

    public void TryMutation(Random random) {
        // fuel type mutation
        if(random.nextDouble() < PROB_MUTATE_FUEL_TYPE)
            fuelType = validFuels[random.nextInt(validFuels.length)];

        // layout mutation
        if(random.nextDouble() < PROB_MUTATE_LAYOUT)
            reactorLayout[random.nextInt(reactorLayout.length)] = validComponents[random.nextInt(validComponents.length)];
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        buffer.append(fuelType).append("|");

        for (int i = 0; i < reactorLayout.length; i++) {
            buffer.append(reactorLayout[i]);

            if (i < reactorLayout.length - 1)
                buffer.append(",");
        }

        return buffer.toString();
    }
}

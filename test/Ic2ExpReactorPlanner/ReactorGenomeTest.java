package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.GeneticOptimizer.GAConfig;
import Ic2ExpReactorPlanner.GeneticOptimizer.ReactorGenome;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class ReactorGenomeTest {
    @Test
    public void testSerializationCycle_ShouldReturnIdenticalGenome() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        ReactorGenome originalGenome = new ReactorGenome(config);
        originalGenome.setFuelType(config.fuels.valid[0]);
        for (int i = 0; i < originalGenome.getReactorLayout().length; i++) {
            // create a non-random pattern in the reactor layout
            originalGenome.getReactorLayout()[i] = config.components.valid[i % config.components.valid.length];
        }

        // Test
        String serializedGenome = ReactorGenome.serialize(originalGenome);
        ReactorGenome deserializedGenome = ReactorGenome.deserialize(config, serializedGenome);

        // Asserts
        assertNotNull("Deserialized genome should not be null", deserializedGenome);
        assertEquals("Fuel types should match after deserialization", originalGenome.getFuelType(), deserializedGenome.getFuelType());
        assertArrayEquals("Reactor layouts should match after deserialization", originalGenome.getReactorLayout(), deserializedGenome.getReactorLayout());
    }

    @Test
    public void testERPSerializationCycle_ShouldReturnIdenticalERPCodes() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        String originalERPCode = "erp=AN0nc6OU0EZ6odjKIHf5LQtII1WK0d2I46Jsac29tPkOMkwUWLvXEmuRd6ZfDXo5b1GSvAM=";
        Reactor reactorA = new Reactor();
        reactorA.setCode(originalERPCode);

        // Test
        ReactorGenome reactorBGenome = ReactorGenome.fromReactor(config, reactorA);
        Reactor reactorB = reactorBGenome.toReactor();

        ReactorGenome reactorCGenome = ReactorGenome.fromReactor(config, reactorB);
        Reactor reactorC = reactorCGenome.toReactor();

        // Asserts
        assertNotNull("ReactorGenome.toReactor() should not be null", reactorC);
        assertEquals("ERP Code should match conversion to and from ReactorGenome", reactorA.getCode(), reactorC.getCode());
    }

    @Test
    public void testCrossBreed_ShouldCombineParentsAtPredictablePoints() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();
        mockRandom.setDoubleValues(0.99);
        mockRandom.setIntValues(10, 25);

        ReactorGenome parentA = new ReactorGenome(config);
        Arrays.fill(parentA.getReactorLayout(), 1);

        ReactorGenome parentB = new ReactorGenome(config);
        Arrays.fill(parentB.getReactorLayout(), 2);

        // Test
        ReactorGenome child = ReactorGenome.crossBreed(config, parentA, parentB, mockRandom);

        // Asserts
        for (int i = 0; i < child.getReactorLayout().length; i++) {
            int expectedValue;
            if (i >= 10 && i < 25) {
                expectedValue = 2; // This section should come from Parent B
            } else {
                expectedValue = 1; // The rest should come from Parent A
            }
            assertEquals("Gene at index '" + i + "' has incorrect value", expectedValue, child.getReactorLayout()[i]);
        }
    }

    @Test
    public void testTryMutation_ShouldNotMutate() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();
        mockRandom.setDoubleValues(0.99, 0.99);

        ReactorGenome originalGenome = ReactorGenome.randomGenome(config, new Random());
        ReactorGenome copyOfOriginal = originalGenome.copy();

        GAConfig.PhaseProbabilities probabilities = config.mutation.refinement;

        // Test
        originalGenome.tryMutation(config, probabilities, mockRandom);

        // Asserts
        assertEquals("Fuel type should not have changed", copyOfOriginal.getFuelType(), originalGenome.getFuelType());
        assertArrayEquals("Reactor layout should not have changed", copyOfOriginal.getReactorLayout(), originalGenome.getReactorLayout());
    }

    @Test
    public void testTryMutation_WhenLayoutShouldMutate_ShouldMutatePredictably() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();
        mockRandom.setDoubleValues(0.99, 0.01);

        int mutationIndex = 20;
        int newComponentListIndex = 5;
        mockRandom.setIntValues(mutationIndex, newComponentListIndex);

        ReactorGenome genome = new ReactorGenome(config);
        int originalFuelType = config.fuels.valid[0];
        genome.setFuelType(originalFuelType);

        GAConfig.PhaseProbabilities probabilities = config.mutation.refinement;

        // Test
        genome.tryMutation(config, probabilities, mockRandom);

        // Asserts
        assertEquals("Fuel type should not have changed", originalFuelType, genome.getFuelType());

        for (int i = 0; i < genome.getReactorLayout().length; i++) {
            if (i == mutationIndex) {
                int expectedComponent = config.components.valid[newComponentListIndex];
                assertEquals("Gene at mutation index '" + i + "' is incorrect", expectedComponent, genome.getReactorLayout()[i]);
            } else {
                assertEquals("Gene at non-mutated index '" + i + "' should be unchanged", 0, genome.getReactorLayout()[i]);
            }
        }
    }

    @Test
    public void testTryMutation_WhenPerSlotShouldMutate_ShouldMutateMultipleSpecificSlots() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();

        int layoutSize = config.reactor.rowCount * config.reactor.colCount;
        Double[] doubleSequence = new Double[layoutSize + 2]; // +2 for the first two mockRandom.nextDouble() for the other rolls
        Arrays.fill(doubleSequence, 0.99);

        int firstMutationIndex = 10;
        int secondMutationIndex = 35;
        double lowProb = 0.001;
        doubleSequence[firstMutationIndex + 2] = lowProb ; // +2 for the first two mockRandom.nextDouble() for the other rolls
        doubleSequence[secondMutationIndex + 2] = lowProb; // +2 for the first two mockRandom.nextDouble() for the other rolls

        mockRandom.setDoubleValues(doubleSequence);

        int firstNewComponentIndex = 2;
        int secondNewComponentIndex = 4;
        mockRandom.setIntValues(firstNewComponentIndex, secondNewComponentIndex);

        ReactorGenome genome = new ReactorGenome(config);

        GAConfig.PhaseProbabilities probabilities = config.mutation.exploration;

        // Test
        genome.tryMutation(config, probabilities, mockRandom);

        // Asserts
        assertEquals("Fuel type should not have changed", -1, genome.getFuelType());

        for (int i = 0; i < layoutSize; i++) {
            int geneValue = genome.getReactorLayout()[i];

            if (i == firstMutationIndex) {
                int expectedComponent = config.components.valid[firstNewComponentIndex];
                assertEquals("Gene at first mutation index '" + i + "' is incorrect", expectedComponent, geneValue);
            } else if (i == secondMutationIndex) {
                int expectedComponent = config.components.valid[secondNewComponentIndex];
                assertEquals("Gene at second mutation index '" + i + "' is incorrect", expectedComponent, geneValue);
            } else {
                assertEquals("Gene at non-mutated index " + i + " should be unchanged", 0, geneValue);
            }
        }
    }

    @Test
    public void testTryMutation_WhenFuelShouldMutate_ShouldMutatePredictably() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();
        mockRandom.setDoubleValues(0.0001, 0.99);
        int oldFuelType = config.fuels.valid[0];
        int newFuelTypeId = config.fuels.valid.length - 1;
        int newFuelType = config.fuels.valid[newFuelTypeId];
        mockRandom.setIntValues(newFuelTypeId);

        assertNotSame("Test setup failed: Not enough valid fuel types in config to test", oldFuelType, newFuelType);

        ReactorGenome genome = new ReactorGenome(config);
        genome.setFuelType(oldFuelType);

        GAConfig.PhaseProbabilities probabilities = config.mutation.refinement;

        // Test
        genome.tryMutation(config, probabilities, mockRandom);

        // Asserts
        assertEquals("Fuel type should have changed", genome.getFuelType(), newFuelType);

        for (int i = 0; i < genome.getReactorLayout().length; i++){
            assertEquals("Layout gene should now have changed at '" + i + "'", 0, genome.getReactorLayout()[i]);
        }
    }
}

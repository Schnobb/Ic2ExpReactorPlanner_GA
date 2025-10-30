package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.GeneticOptimizer.GAConfig;
import Ic2ExpReactorPlanner.GeneticOptimizer.ReactorGenome;
import Ic2ExpReactorPlanner.old.ComponentFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class ReactorGenomeTest {
    // A small delta for comparing floating-point numbers to account for minor inaccuracies.
    private static final double DELTA = 0.0001;

    @Test
    public void testSerializationCycle_ShouldReturnIdenticalGenome() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        ReactorGenome originalGenome = new ReactorGenome(config, ComponentFactory.getInstance());
        originalGenome.setFuelType(config.fuels.valid[0]);
        for (int i = 0; i < originalGenome.getReactorLayout().length; i++) {
            // create a non-random pattern in the reactor layout
            originalGenome.getReactorLayout()[i] = config.components.valid[i % config.components.valid.length];
        }

        // Test
        String serializedGenome = ReactorGenome.serialize(originalGenome);
        ReactorGenome deserializedGenome = ReactorGenome.deserialize(config, serializedGenome, ComponentFactory.getInstance());

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
        Reactor reactorA = new Reactor(ComponentFactory.getInstance());
        reactorA.setCode(originalERPCode);

        // Test
        ReactorGenome reactorBGenome = ReactorGenome.fromReactor(config, reactorA, ComponentFactory.getInstance());
        var reactorB = reactorBGenome.toReactor();

        ReactorGenome reactorCGenome = ReactorGenome.fromReactor(config, reactorB, ComponentFactory.getInstance());
        var reactorC = reactorCGenome.toReactor();

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

        ReactorGenome parentA = new ReactorGenome(config, ComponentFactory.getInstance());
        Arrays.fill(parentA.getReactorLayout(), 1);

        ReactorGenome parentB = new ReactorGenome(config, ComponentFactory.getInstance());
        Arrays.fill(parentB.getReactorLayout(), 2);

        // Test
        ReactorGenome child = ReactorGenome.crossBreed(config, parentA, parentB, mockRandom, ComponentFactory.getInstance());

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

        int layoutSize = config.reactor.rowCount * config.reactor.colCount;
        Double[] doubleSequence = new Double[layoutSize + 2]; // +2 for the first two mockRandom.nextDouble() for the other rolls
        Arrays.fill(doubleSequence, 0.99);

        mockRandom.setDoubleValues(doubleSequence);

        ReactorGenome originalGenome = ReactorGenome.randomGenome(config, new Random(), ComponentFactory.getInstance());
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

        int layoutSize = config.reactor.rowCount * config.reactor.colCount;
        Double[] doubleSequence = new Double[layoutSize + 2]; // +2 for the first two mockRandom.nextDouble() for the other rolls
        Arrays.fill(doubleSequence, 0.99);
        doubleSequence[1] = config.mutation.refinement.probabilityLayoutMutation / 2.0; // layout mutation

        mockRandom.setDoubleValues(doubleSequence);

        int mutationIndex = 20;
        int newComponentListIndex = 5;
        mockRandom.setIntValues(mutationIndex, newComponentListIndex);

        ReactorGenome genome = new ReactorGenome(config, ComponentFactory.getInstance());
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
        double lowProb = config.mutation.exploration.probabilityLayoutPerSlotMutation / 2.0;
        doubleSequence[firstMutationIndex + 2] = lowProb ; // +2 for the first two mockRandom.nextDouble() for the other rolls
        doubleSequence[secondMutationIndex + 2] = lowProb; // +2 for the first two mockRandom.nextDouble() for the other rolls

        mockRandom.setDoubleValues(doubleSequence);

        int firstNewComponentIndex = 2;
        int secondNewComponentIndex = 4;
        mockRandom.setIntValues(firstNewComponentIndex, secondNewComponentIndex);

        ReactorGenome genome = new ReactorGenome(config, ComponentFactory.getInstance());

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

        int layoutSize = config.reactor.rowCount * config.reactor.colCount;
        Double[] doubleSequence = new Double[layoutSize + 2]; // +2 for the first two mockRandom.nextDouble() for the other rolls
        Arrays.fill(doubleSequence, 0.99);
        doubleSequence[0] = config.mutation.refinement.probabilityFuelMutation / 2.0; // fuel mutation

        mockRandom.setDoubleValues(doubleSequence);

        int oldFuelType = config.fuels.valid[0];
        int newFuelTypeId = config.fuels.valid.length - 1;
        int newFuelType = config.fuels.valid[newFuelTypeId];
        mockRandom.setIntValues(newFuelTypeId);

        assertNotSame("Test setup failed: Not enough valid fuel types in config to test", oldFuelType, newFuelType);

        ReactorGenome genome = new ReactorGenome(config, ComponentFactory.getInstance());
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

    @Test
    public void testSpeciation_WhenCalculatingSimilarity_ShouldBe1ForIdenticalGenomes() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        // Hijack speciation configs to make tests more consistent
        config.speciation.speciesSimilarityThreshold = 0.9;
        config.speciation.fuelLayoutWeight = 0.9;
        config.speciation.componentsLayoutWeight = 0.1;

        Reactor reactorA = new Reactor(ComponentFactory.getInstance());
        reactorA.setCode("erp=N0nc6OU0SvFZnCLVeUv6NTtSGYRuhPMF5/rPVu58BwJq0rGgaqVCookKH7pbJVRL7i32LAM=");

        Reactor reactorB = new Reactor(ComponentFactory.getInstance());
        reactorB.setCode("erp=N0nc6OU0SvFZnCLVeUv6NTtSGYRuhPMF5/rPVu58BwJq0rGgaqVCookKH7pbJVRL7i32LAM=");

        ReactorGenome genomeA = ReactorGenome.fromReactor(config, reactorA, ComponentFactory.getInstance());
        ReactorGenome genomeB = ReactorGenome.fromReactor(config, reactorB, ComponentFactory.getInstance());

        // Test
        double speciesSimilarity = ReactorGenome.calculateSimilarity(config, genomeA, genomeB);

        // Assert
        assertEquals(String.format("Calculated similarity should be 1.0 for identical genomes (species similarity %.2f)", speciesSimilarity), 1.0, speciesSimilarity, DELTA);
    }

    @Test
    public void testSpeciation_WhenCalculatingSimilarity_ShouldBeSameSpeciesForSimilarGenomes() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        // Hijack speciation configs to make tests more consistent
        config.speciation.speciesSimilarityThreshold = 0.9;
        config.speciation.fuelLayoutWeight = 0.85;
        config.speciation.componentsLayoutWeight = 0.15;

        // reference reactor
        Reactor reactorA = new Reactor(ComponentFactory.getInstance());
        reactorA.setCode("erp=N0nc6OU0SvFZnCLVeUv6NTtSGYRuhPMF5/rPVu58BwJq0rGgaqVCookKH7pbJVRL7i32LAM=");

        // this reactor has 16/45 component match, with the provided config this should be just over the speciesSimilarityThreshold
        Reactor reactorB = new Reactor(ComponentFactory.getInstance());
        reactorB.setCode("erp=A3Sdzo5TRK8VmcItV5S/o1O1IZhG6EiX5uNQ4ZZX5RWIRtPH4sQVxSF7yXy/0Rg0aAM=");

        ReactorGenome genomeA = ReactorGenome.fromReactor(config, reactorA, ComponentFactory.getInstance());
        ReactorGenome genomeB = ReactorGenome.fromReactor(config, reactorB, ComponentFactory.getInstance());

        // Test
        double speciesSimilarity = ReactorGenome.calculateSimilarity(config, genomeA, genomeB);

        // Assert
        assertTrue(String.format("Similar genomes should count as same species (similarity score%.2f)", speciesSimilarity), speciesSimilarity + DELTA > config.speciation.speciesSimilarityThreshold);
    }

    @Test
    public void testSpeciation_WhenCalculatingSimilarity_ShouldBeDifferentSpeciesForDifferentGenomes() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        // Hijack speciation configs to make tests more consistent
        config.speciation.speciesSimilarityThreshold = 0.9;
        config.speciation.fuelLayoutWeight = 0.9;
        config.speciation.componentsLayoutWeight = 0.1;

        // reference reactor, Dual Fuel Rod (Uranium)
        Reactor reactorA = new Reactor(ComponentFactory.getInstance());
        reactorA.setCode("erp=N0nc6OU0SvFZnCLVeUv6NTtSGYRuhPMF5/rPVu58BwJq0rGgaqVCookKH7pbJVRL7i32LAM=");

        // Dual Fuel Rod (MOX) reactor, should get a similarity score of 0.0
        Reactor reactorB = new Reactor(ComponentFactory.getInstance());
        reactorB.setCode("erp=AN0nc6OU0Rz6w6jd/pKMVSHSxf7iuIgyxZ8Uu+NPBWGixMLV97mJ0BJ2BrnbiYBXbbzJTAM=");

        // Dual Fuel Rod (Uranium) reactor with slightly different fuel placement and different component layout, should be under the threshold for same species
        Reactor reactorC = new Reactor(ComponentFactory.getInstance());
        reactorC.setCode("erp=N0nc6OUzphbBGTu/kqIVc7xEriE+KThUiL0KKKJxdeoLyJ/WM429Hn73C+GyosGEBgGWLAM=");

        ReactorGenome genomeA = ReactorGenome.fromReactor(config, reactorA, ComponentFactory.getInstance());
        ReactorGenome genomeB = ReactorGenome.fromReactor(config, reactorB, ComponentFactory.getInstance());
        ReactorGenome genomeC = ReactorGenome.fromReactor(config, reactorC, ComponentFactory.getInstance());

        // Test
        double speciesSimilarityAB = ReactorGenome.calculateSimilarity(config, genomeA, genomeB);
        double speciesSimilarityAC = ReactorGenome.calculateSimilarity(config, genomeA, genomeC);

        // Assert
        assertEquals(String.format("Different fuel type genomes should have a similarity score of 0.0 (similarity score %.2f)", speciesSimilarityAB), 0.0, speciesSimilarityAB, DELTA);
        assertTrue(String.format("Genome with significant layout differences should not be classified as the same species (similarity score %.2f)", speciesSimilarityAC), speciesSimilarityAC - DELTA <= config.speciation.speciesSimilarityThreshold);
    }
}

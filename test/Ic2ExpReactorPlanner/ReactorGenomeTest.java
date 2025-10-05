package Ic2ExpReactorPlanner;

import org.junit.Test;

import java.util.Arrays;

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
        assertNotNull("ReactorGenom.toReactor() should not be null", reactorC);
        assertEquals("ERP Code should match avec conversion to and from ReactorGenome", reactorA.getCode(), reactorC.getCode());
    }

    @Test
    public void testCrossBreed_ShouldCombineParentsAtPredictablePoints() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        MockRandom mockRandom = new MockRandom();
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
            assertEquals("Gene at index " + i + " has incorrect value", expectedValue, child.getReactorLayout()[i]);
        }
    }
}

package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.GeneticOptimizer.GAConfig;
import Ic2ExpReactorPlanner.GeneticOptimizer.ReactorGenome;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class ReactorSimulatorTest {
    private final String STANDARD_STABLE_REACTOR_ERP_CODE = "erp=AN0nc6OU0EZ6odjKIHf5LQtII1WK0d2I46Jsac29tPkOMkwUWLvXEmuRd6ZfDXo5b1GSvAM=";
    private final String STANDARD_EXPLODY_REACTOR_ERP_CODE = "erp=AN0nc6OU0EZ6odjKIHf5LQtII1WK0d2I46Jsac29tENct0TQ5x2AuOHMgYsd4XMbSlGSvAM=";

    // A small delta for comparing floating-point numbers to account for minor inaccuracies.
    private final double DELTA = 0.0001;

    @Test
    public void testReactorSimulator_WhenRunWithKnownReactor_ShouldProduceKnownValues() {
        // Setup
        ReactorSimulator simulator = new ReactorSimulator();

        Reactor reactor = new Reactor();
        reactor.setCode(STANDARD_EXPLODY_REACTOR_ERP_CODE);

        // Test
        SimulationData data = simulator.runSimulation(reactor);

        // Assert
        assertNotNull("Simulator data should not be null", data);

        // only check some key metrics here, known values for tested reactor
        assertEquals("Time to Burn should be '66'", 66, data.timeToBurn);
        assertEquals("Time to Explode should be '76'", 76, data.timeToXplode);
        assertEquals("First component broken time should be '13'", 13, data.firstComponentBrokenTime);
        assertEquals("Pre-break total EU output should be '135200.0'", 135200.0, data.prebreakTotalEUoutput, DELTA);
        assertEquals("Pre-break max EU output should be '520.0'", 520.0, data.prebreakMaxEUoutput, DELTA);
        assertEquals("Max temp should be '10072.0'", 10072.0, data.maxTemp, DELTA);
    }

    @Test
    public void testResetState_WhenRunTwice_ShouldProduceIdenticalSimulatorData() {
        // Setup
        ReactorSimulator simulator = new ReactorSimulator();

        Reactor reactorA = new Reactor();
        reactorA.setCode(STANDARD_STABLE_REACTOR_ERP_CODE);

        Reactor reactorB = new Reactor();
        reactorB.setCode(STANDARD_STABLE_REACTOR_ERP_CODE);

        // Test
        SimulationData expectedData = simulator.runSimulation(reactorA);
        simulator.resetState();
        SimulationData actualData = simulator.runSimulation(reactorB);

        // Assert
        assertSimulationDataIsEquivalent(expectedData, actualData);
    }

    /**
     * A helper method to compare the deterministic fields of two SimulationData objects.
     * It ignores non-deterministic fields like timestamps and provides detailed
     * error messages for each field comparison.
     *
     * @param expected The SimulationData object with the expected values.
     * @param actual   The SimulationData object produced by the code under test.
     */
    private void assertSimulationDataIsEquivalent(SimulationData expected, SimulationData actual) {
        assertNotNull("Expected data should not be null", expected);
        assertNotNull("Actual data should not be null", actual);

        // --- Times to Temperature Thresholds ---
        assertEquals("Time to Below 50 should be identical", expected.timeToBelow50, actual.timeToBelow50);
        assertEquals("Time to Burn should be identical", expected.timeToBurn, actual.timeToBurn);
        assertEquals("Time to Evaporate should be identical", expected.timeToEvaporate, actual.timeToEvaporate);
        assertEquals("Time to Hurt should be identical", expected.timeToHurt, actual.timeToHurt);
        assertEquals("Time to Lava should be identical", expected.timeToLava, actual.timeToLava);
        assertEquals("Time to Explode should be identical", expected.timeToXplode, actual.timeToXplode);

        // --- Special ---
        assertEquals("Total rod count should be identical", expected.totalRodCount, actual.totalRodCount);

        // --- First Component Broken Details ---
        assertEquals("First component broken time should be identical", expected.firstComponentBrokenTime, actual.firstComponentBrokenTime);
        assertEquals("First component broken row should be identical", expected.firstComponentBrokenRow, actual.firstComponentBrokenRow);
        assertEquals("First component broken column should be identical", expected.firstComponentBrokenCol, actual.firstComponentBrokenCol);
        assertEquals("First component broken description should be identical", expected.firstComponentBrokenDescription, actual.firstComponentBrokenDescription);
        assertEquals("Pre-break total EU output should be identical", expected.prebreakTotalEUoutput, actual.prebreakTotalEUoutput, DELTA);
        assertEquals("Pre-break average EU output should be identical", expected.prebreakAvgEUoutput, actual.prebreakAvgEUoutput, DELTA);
        assertEquals("Pre-break min EU output should be identical", expected.prebreakMinEUoutput, actual.prebreakMinEUoutput, DELTA);
        assertEquals("Pre-break max EU output should be identical", expected.prebreakMaxEUoutput, actual.prebreakMaxEUoutput, DELTA);
        assertEquals("Pre-break total HU output should be identical", expected.prebreakTotalHUoutput, actual.prebreakTotalHUoutput, DELTA);
        assertEquals("Pre-break average HU output should be identical", expected.prebreakAvgHUoutput, actual.prebreakAvgHUoutput, DELTA);
        assertEquals("Pre-break min HU output should be identical", expected.prebreakMinHUoutput, actual.prebreakMinHUoutput, DELTA);
        assertEquals("Pre-break max HU output should be identical", expected.prebreakMaxHUoutput, actual.prebreakMaxHUoutput, DELTA);

        // --- First Rod Depleted Details ---
        assertEquals("First rod depleted time should be identical", expected.firstRodDepletedTime, actual.firstRodDepletedTime);
        assertEquals("First rod depleted row should be identical", expected.firstRodDepletedRow, actual.firstRodDepletedRow);
        assertEquals("First rod depleted column should be identical", expected.firstRodDepletedCol, actual.firstRodDepletedCol);
        assertEquals("First rod depleted description should be identical", expected.firstRodDepletedDescription, actual.firstRodDepletedDescription);
        assertEquals("Pre-deplete total EU output should be identical", expected.predepleteTotalEUoutput, actual.predepleteTotalEUoutput, DELTA);
        assertEquals("Pre-deplete average EU output should be identical", expected.predepleteAvgEUoutput, actual.predepleteAvgEUoutput, DELTA);
        assertEquals("Pre-deplete min EU output should be identical", expected.predepleteMinEUoutput, actual.predepleteMinEUoutput, DELTA);
        assertEquals("Pre-deplete max EU output should be identical", expected.predepleteMaxEUoutput, actual.predepleteMaxEUoutput, DELTA);
        assertEquals("Pre-deplete total HU output should be identical", expected.predepleteTotalHUoutput, actual.predepleteTotalHUoutput, DELTA);
        assertEquals("Pre-deplete average HU output should be identical", expected.predepleteAvgHUoutput, actual.predepleteAvgHUoutput, DELTA);
        assertEquals("Pre-deplete min HU output should be identical", expected.predepleteMinHUoutput, actual.predepleteMinHUoutput, DELTA);
        assertEquals("Pre-deplete max HU output should be identical", expected.predepleteMaxHUoutput, actual.predepleteMaxHUoutput, DELTA);
        assertEquals("Pre-deplete min temp should be identical", expected.predepleteMinTemp, actual.predepleteMinTemp, DELTA);
        assertEquals("Pre-deplete max temp should be identical", expected.predepleteMaxTemp, actual.predepleteMaxTemp, DELTA);

        // --- Completed-Simulation Details ---
        assertEquals("Total reactor ticks should be identical", expected.totalReactorTicks, actual.totalReactorTicks);
        assertEquals("Total EU output should be identical", expected.totalEUoutput, actual.totalEUoutput, DELTA);
        assertEquals("Average EU output should be identical", expected.avgEUoutput, actual.avgEUoutput, DELTA);
        assertEquals("Min EU output should be identical", expected.minEUoutput, actual.minEUoutput, DELTA);
        assertEquals("Max EU output should be identical", expected.maxEUoutput, actual.maxEUoutput, DELTA);
        assertEquals("Total HU output should be identical", expected.totalHUoutput, actual.totalHUoutput, DELTA);
        assertEquals("Average HU output should be identical", expected.avgHUoutput, actual.avgHUoutput, DELTA);
        assertEquals("Min HU output should be identical", expected.minHUoutput, actual.minHUoutput, DELTA);
        assertEquals("Max HU output should be identical", expected.maxHUoutput, actual.maxHUoutput, DELTA);
        assertEquals("Min temp should be identical", expected.minTemp, actual.minTemp, DELTA);
        assertEquals("Max temp should be identical", expected.maxTemp, actual.maxTemp, DELTA);

        // --- Heating and Cooling Details ---
        assertEquals("Hull heating should be identical", expected.hullHeating, actual.hullHeating, DELTA);
        assertEquals("Component heating should be identical", expected.componentHeating, actual.componentHeating, DELTA);
        assertEquals("Hull cooling should be identical", expected.hullCooling, actual.hullCooling, DELTA);
        assertEquals("Hull cooling capacity should be identical", expected.hullCoolingCapacity, actual.hullCoolingCapacity, DELTA);
        assertEquals("Vent cooling should be identical", expected.ventCooling, actual.ventCooling, DELTA);
        assertEquals("Vent cooling capacity should be identical", expected.ventCoolingCapacity, actual.ventCoolingCapacity, DELTA);
    }
}

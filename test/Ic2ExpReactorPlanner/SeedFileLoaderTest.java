package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.GeneticOptimizer.GAConfig;
import Ic2ExpReactorPlanner.GeneticOptimizer.ReactorGenome;
import Ic2ExpReactorPlanner.GeneticOptimizer.SeedFileLoader;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SeedFileLoaderTest {
    @Test
    public void testSeedFileLoader_WhenLoadFile_CleanupCommentsAndReturnCorrectArray() {
        // Setup
        GAConfig config = GAConfig.loadConfig(null);
        assertNotNull("Test setup failed: Could not load config", config);

        ArrayList<String> expectedCodes = loadExpectedCodes();
        ArrayList<ReactorGenome> expectedGenomes = new ArrayList<>();

        for (String code : expectedCodes) {
            Reactor reactor = new Reactor();
            reactor.setCode(code);
            expectedGenomes.add(ReactorGenome.fromReactor(config, reactor));
        }

        // Test
        String currentDir = System.getProperty("user.dir");
        ArrayList<ReactorGenome> actualGenomes = SeedFileLoader.LoadSeedFile(config, "preload_test.txt");

        // Assert
        assertEquals("Loaded genomes and expected genomes are different.", expectedGenomes, actualGenomes);
    }

    private static ArrayList<String> loadExpectedCodes() {
        ArrayList<String> expectedCodes = new ArrayList<>();
        expectedCodes.add("erp=bpO50cpokTyxp3t0Zt8MixpJUtrDtJOeeURH9JAFEGPgaDwULfUgoAKdNq5H48RiCKO/RAM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMLrtTl3E+Qa1lMiFinWf5mdnT0gWeD8/RB7ECoP0QL6lF54ZOxTy7+AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMLrtTl3E+Qa1lMiFinWhS0hx/8k15oPnQReCYX71jwkLbByDZBTy7+AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMRwKBdsK57Ynew1cOZd4tksB0s7I5RpXgM9hJ4WYNNK2E4cZOxTy7+AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMLrtTl3E+Qa1lMiFinWhS0hx/8k15oPnQReCYX71jwkLbByDZBTy73AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMRwKBdsK57Ynew1cOZd4tksB0s7I5RpXgM9hJ4WYNNK2E4cZOxTy7+AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhPMRwKBdsK57Ynew1c3Zd4tksB0s7I5RpXgM9hJ4WYNNK2E4cZOxTy7+AM=");
        expectedCodes.add("erp=G6TudHKbwHmuXhRMPwKBdsK57Ynew1cOZd4tksB0s7I5RpXgM9hJ4WYNNK2E4cZOxTy7+AM=");
        return expectedCodes;
    }
}

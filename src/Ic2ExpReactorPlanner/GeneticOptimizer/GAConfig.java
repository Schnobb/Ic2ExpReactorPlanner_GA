package Ic2ExpReactorPlanner.GeneticOptimizer;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GAConfig {
    public EvolutionConfig evolution;
    public FitnessConfig fitness;
    public MutationConfig mutation;
    public ComponentConfig components;
    public FuelConfig fuels;
    public ReactorConfig reactor;

    public static final String DEFAULT_CONFIG_FILE_NAME = "ga_default_config.json";

    public static class EvolutionConfig {
        public int phaseLengthGenerations;
        public int populationSize;
        public int maxGeneration;
        public int alphaCount;
        public int tournamentSizeK;
    }

    public static class FitnessConfig {
        public double euOutputWeight;
        public double fuelEfficiencyWeight;
        public double metaEfficiencyTarget;
        public double componentBrokenPenalty;
    }

    public static class MutationConfig {
        public PhaseProbabilities refinement;
        public PhaseProbabilities exploration;
    }

    public static class PhaseProbabilities {
        public double probabilityFuelMutation;
        public double probabilityLayoutMutation;
        public double probabilityLayoutPerSlotMutation;
    }

    public static class ComponentConfig {
        public int[] valid;
    }

    public static class FuelConfig {
        public int[] valid;
    }

    public static class ReactorConfig {
        public int rowCount;
        public int colCount;
    }

    public static GAConfig loadConfig(String path) {
        try {
            ClassLoader classLoader = GAConfig.class.getClassLoader();

            if (path == null || path.isEmpty()) {
                path = DEFAULT_CONFIG_FILE_NAME;
            }

            try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
                if (inputStream == null) {
                    throw new FileNotFoundException("Resource not found: " + path);
                }

                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                JsonReader jsonReader = new JsonReader(inputStreamReader);
                jsonReader.setLenient(true);

                Gson gson = new Gson();
                return gson.fromJson(jsonReader, GAConfig.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

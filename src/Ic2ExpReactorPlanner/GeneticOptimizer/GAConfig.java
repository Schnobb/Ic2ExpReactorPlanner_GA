package Ic2ExpReactorPlanner.GeneticOptimizer;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GAConfig {
    public EvolutionConfig evolution;
    public FitnessConfig fitness;
    public MutationConfig mutation;
    public ComponentConfig components;
    public FuelConfig fuels;
    public ReactorConfig reactor;

    private String configName;

    public static final String DEFAULT_CONFIG_FILE_NAME = "ga_default_config.json";

    private GAConfig() { }

    public String getConfigName() {
        return this.configName;
    }

    public static class EvolutionConfig {
        public int phaseLengthGenerations;
        public int populationSize;
        public int maxGeneration;
        public int alphaCount;
        public int tournamentSizeK;
        public double lowDiversityThreshold;
        public double lowDiversityCullingRatio;
        public String seedFile;

        @Override
        public String toString() {
            return String.format("maxGeneration = %d; populationSize = %d; phaseLengthGenerations = %d; alphaCount = %d; tournamentSizeK = %d; lowDiversityThreshold = %.2f; lowDiversityCullingRatio = %.2f", maxGeneration, populationSize, phaseLengthGenerations, alphaCount, tournamentSizeK, lowDiversityThreshold, lowDiversityCullingRatio);
        }
    }

    public static class FitnessConfig {
        public double euOutputWeight;
        public double fuelEfficiencyWeight;
        public double metaFuelEfficiencyTarget;
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

                // Read file line by line and strip comments
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder jsonContent = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    // Remove inline comments but keep the rest of the line
                    int commentIndex = line.indexOf("//");
                    if (commentIndex >= 0) {
                        line = line.substring(0, commentIndex);
                    }

                    // Only add non-empty lines
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        jsonContent.append(line).append("\n");
                    }
                }

                String cleanedJson = jsonContent.toString();

                // Strip multi-line comments (/* ... */)
                cleanedJson = cleanedJson.replaceAll("/\\*.*?\\*/", "");

                Gson gson = new Gson();
                GAConfig config = gson.fromJson(cleanedJson, GAConfig.class);
                config.configName = path;
                return config;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

package Ic2ExpReactorPlanner.GeneticOptimizer;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class GAConfig {
    public EvolutionConfig evolution;
    public SpeciationConfig speciation;
    public FitnessConfig fitness;
    public MutationConfig mutation;
    public ComponentConfig components;
    public FuelConfig fuels;
    public ReactorConfig reactor;

    private String configName;

    public static final String DEFAULT_CONFIG_FILE_NAME = "ga_default_config.json";

    private GAConfig() {
    }

    public String getConfigName() {
        return this.configName;
    }

    private static abstract class Config {
        @Override
        public String toString() {
            final String valueDelimiter = " = ";
            final String fieldsDelimiter = "; ";

            StringBuilder stringBuilder = new StringBuilder();
            for (Field field : getClass().getFields()) {
                stringBuilder.append(field.getName())
                        .append(valueDelimiter);
                try {
                    stringBuilder.append(field.get(this));
                } catch (IllegalAccessException e) {
                    stringBuilder.append("<inaccessible>");
                }

                stringBuilder.append(fieldsDelimiter);
            }

            return stringBuilder.toString();
        }
    }

    public static class EvolutionConfig extends Config {
        public int phaseLengthGenerations;
        public int populationSize;
        public int maxGeneration;
        public int alphaCount;
        public int tournamentSizeK;
        public double lowDiversityThreshold;
        public double lowDiversityCullingRatio;
        public String seedFile;
    }

    public static class SpeciationConfig extends Config {
        public double speciesSimilarityThreshold;
        public double fuelLayoutWeight;
        public double componentsLayoutWeight;
    }

    public static class FitnessConfig extends Config {
        public double euOutputWeight;
        public double fuelEfficiencyWeight;
        public double metaFuelEfficiencyTarget;
        public double componentBrokenPenalty;
        public double heatPenaltyMultiplier;
    }

    public static class MutationConfig extends Config {
        public PhaseProbabilities refinement;
        public PhaseProbabilities exploration;
    }

    public static class PhaseProbabilities extends Config {
        public double probabilityFuelMutation;
        public double probabilityLayoutMutation;
        public double probabilityLayoutPerSlotMutation;
    }

    public static class ComponentConfig extends Config {
        public int[] valid;
    }

    public static class FuelConfig extends Config {
        public int[] valid;
    }

    public static class ReactorConfig extends Config {
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
            Logger.log(e, "Config loading has failed");
            return null;
        }
    }
}

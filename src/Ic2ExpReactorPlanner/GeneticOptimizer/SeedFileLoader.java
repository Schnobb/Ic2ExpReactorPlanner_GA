package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.Reactor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SeedFileLoader {
    private static final String COMMENT_PATTERN = "\\h*(//|#).*$";

    public static ArrayList<ReactorGenome> LoadSeedFile(GAConfig config, String path) {
        ArrayList<ReactorGenome> seedList = new ArrayList<>();

        String cleanedContent;
        Pattern commentPattern = Pattern.compile(COMMENT_PATTERN);
        try (InputStream inputStream = SeedFileLoader.class.getClassLoader().getResourceAsStream(path)) {
            assert inputStream != null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            cleanedContent = reader
                    .lines()
                    .map(line -> commentPattern.matcher(line).replaceAll(""))
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        for (String code : cleanedContent.split("\n")) {
            Reactor newReactor = new Reactor();
            newReactor.setCode(code);
            seedList.add(ReactorGenome.fromReactor(config, newReactor));
        }

        return seedList;
    }
}

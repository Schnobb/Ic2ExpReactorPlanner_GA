package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.IComponentFactory;
import Ic2ExpReactorPlanner.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SeedFileLoader {
    private static final Logger Logger = LoggerFactory.getLogger(SeedFileLoader.class);

    private static final String COMMENT_PATTERN = "\\h*(//|#).*$";

    public static List<ReactorGenome> LoadSeedFile(GAConfig config, String path, IComponentFactory componentFactory) {
        List<ReactorGenome> seedList = new ArrayList<>();

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
            Logger.error("Could not load seed file '" + path + "'", e);
            return null;
        }

        if (!cleanedContent.isEmpty()) {
            for (String code : cleanedContent.split("\n")) {
                Reactor newReactor = new Reactor(componentFactory);
                newReactor.setCode(code);
                seedList.add(ReactorGenome.fromReactor(config, newReactor, componentFactory));
            }
        }

        return seedList;
    }
}

package Ic2ExpReactorPlanner;

import java.text.DecimalFormat;

public class GeneticOptimizer {
    public static void main(String[] args) {
        String testReactor = "erp=bpO50cpi8E5OP2NCrFVHygib7/xvxtcd8jWwWed4soVHBTDMyEdVSDc8pKimGhvVXAM=";
        Reactor reactor = new Reactor();
        reactor.setCode(testReactor);

        System.out.println("Testing reactor '" + testReactor + "'\n");
        ReactorSimulator simulator = new ReactorSimulator();
        SimulationData data = simulator.runSimulation(reactor);

        DecimalFormat outputFormat = new DecimalFormat("#,##0.00");
        System.out.println("Simulation ended.");
        // EU is x10 in GTNH
        System.out.println("\tAVG EU Output: " + outputFormat.format(data.avgEUoutput * 10) + " EU/t");
        System.out.println("\tMax Temp: " + outputFormat.format(data.maxTemp) + " Hu");
        System.out.println("\tTotal EU Output: " + outputFormat.format(data.totalEUoutput * 10) + " EU/t");

        System.out.println();
        ReactorGenome genome = ReactorGenome.fromReactor(reactor);
        System.out.println(genome);
        System.out.println(ReactorGenome.serialize(genome));
    }

    private double EvaluateGenomeFitness(SimulationData data, ReactorGenome genome) {
        double fitness = 0.0;

        if(data.maxTemp > 0)
            return 0.0;

        // base fitness is average EU/t for the duration
        fitness += data.avgEUoutput;

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe modify by fuel efficiency (EU/t per rod)
        // maybe more reactor analysis for more modifiers

        return fitness;
    }
}

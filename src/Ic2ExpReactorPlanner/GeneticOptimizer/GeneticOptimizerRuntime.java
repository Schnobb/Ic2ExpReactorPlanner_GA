package Ic2ExpReactorPlanner.GeneticOptimizer;

import Ic2ExpReactorPlanner.Reactor;
import Ic2ExpReactorPlanner.SimulationData;

public class GeneticOptimizerRuntime {
    public static void main(String[] args) {
        GAConfig config = GAConfig.loadConfig(args.length > 1 ? args[1] : null);
        if (config == null)
            System.exit(1);

        String testReactor = "erp=AN0nc6OU0EZ6odjKIHf5LQtII1WK0d2I46Jsac29tPkOMkwUWLvXEmuRd6ZfDXo5b1GSvAM=";

        Reactor reactorA = new Reactor();
        reactorA.setCode(testReactor);

        ReactorGenome reactorBGenome = ReactorGenome.fromReactor(config, reactorA);
        Reactor reactorB = reactorBGenome.toReactor();

        ReactorGenome reactorCGenome = ReactorGenome.fromReactor(config, reactorB);
        Reactor reactorC = reactorCGenome.toReactor();

        System.out.println("Test Reactor: " + testReactor);
        System.out.println("Reactor A: " + reactorA.getCode());
        System.out.println("Reactor B: " + reactorB.getCode());
        System.out.println("Reactor C: " + reactorC.getCode());
//        String testReactor = "erp=bpO50cpi8E5OP2NCrFVHygib7/xvxtcd8jWwWed4soVHBTDMyEdVSDc8pKimGhvVXAM=";
//        Reactor reactor = new Reactor();
//        reactor.setCode(testReactor);
//
//        System.out.println("Testing reactor '" + testReactor + "'\n");
//        ReactorSimulator simulator = new ReactorSimulator();
//        SimulationData data = simulator.runSimulation(reactor);
//
//        DecimalFormat outputFormat = new DecimalFormat("#,##0.00");
//        System.out.println("Simulation ended.");
//        // EU is x10 in GTNH
//        System.out.println("\tAVG EU Output: " + outputFormat.format(data.avgEUoutput * 10) + " EU/t");
//        System.out.println("\tMax Temp: " + outputFormat.format(data.maxTemp) + " Hu");
//        System.out.println("\tTotal EU Output: " + outputFormat.format(data.totalEUoutput * 10) + " EU/t");
//
//        System.out.println();
//        ReactorGenome genome = ReactorGenome.fromReactor(reactor);
//        System.out.println(genome);
//        System.out.println(ReactorGenome.serialize(genome));
//        System.out.println(genome.getERPCode());
//
//        String erpIssueReactorCodeBefore = "erp=AN0nc6OU0EZ6odjKIHf5LQtII1WK0d2I46Jsac29tPkOMkwUWLvXEmuRd6ZfDXo5b1GSvAM=";
//        Reactor erpIssueReactorBefore = new Reactor();
//        erpIssueReactorBefore.setCode(erpIssueReactorCodeBefore);
//
//        ReactorGenome erpIssueGenome = ReactorGenome.fromReactor(erpIssueReactorBefore);
//        Reactor erpIssueReactorAfter = new Reactor();
//        erpIssueReactorAfter.setCode(erpIssueGenome.getERPCode());
//        String erpIssueReactorCodeAfter = erpIssueReactorAfter.getCode();
//
//        System.out.println(erpIssueReactorCodeBefore);
//        System.out.println(erpIssueReactorCodeAfter);

    }

    private double EvaluateGenomeFitness(SimulationData data, ReactorGenome genome) {
        double fitness = 0.0;

        if (data.maxTemp > 0)
            return 0.0;

        // base fitness is average EU/t for the duration
        fitness += data.avgEUoutput;

        // maybe modify by total EU generation? but this will put more importance on later fuels
        // maybe modify by fuel efficiency (EU/t per rod)
        // maybe more reactor analysis for more modifiers

        return fitness;
    }
}

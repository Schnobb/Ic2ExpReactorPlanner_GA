package Ic2ExpReactorPlanner;

import static Ic2ExpReactorPlanner.BundleHelper.formatI18n;
import static Ic2ExpReactorPlanner.BundleHelper.getI18n;

import Ic2ExpReactorPlanner.components.ReactorItem;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 *
 * @author Brian McCloud
 */
public class AutomationSimulator extends SwingWorker<Void, String> {

    private final JTextArea output;
    private final JPanel[][] reactorButtonPanels;

    private final File csvFile;
    private final int csvLimit;

    private boolean completed = false;
    private final Reactor reactor;
    private SimulationData data;

    public SimulationData getData() {
        if (completed) {
            return data;
        }
        return null;
    }

    public AutomationSimulator(final Reactor reactor, final JTextArea output, final JPanel[][] reactorButtonPanels, final File csvFile, final int csvLimit) {
        this.reactor = reactor;
        this.output = output;
        this.reactorButtonPanels = reactorButtonPanels;
        this.csvFile = csvFile;
        this.csvLimit = csvLimit;
    }

    @Override
    protected Void doInBackground() {
        PrintWriter csvOut = null;
        if (csvFile != null) {
            try {
                csvOut = new PrintWriter(csvFile);
            } catch (IOException ex) {
                publish(getI18n("Simulation.CSVOpenFailure"));
            }
        }

        ReactorSimulator simulator = new ReactorSimulator();

        try {
            if (csvOut != null) {
                csvOut.print(getI18n("CSVData.HeaderReactorTick"));
                csvOut.print(getI18n("CSVData.HeaderCoreHeat"));
                if (reactor.isFluid()) {
                    csvOut.print(getI18n("CSVData.HeaderHUOutput"));
                } else {
                    csvOut.print(getI18n("CSVData.HeaderEUOutput"));
                }
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorItem component = reactor.getComponentAt(row, col);
                        if (component != null && (component.getMaxHeat() > 1 || component.getMaxDamage() > 1)) {
                            csvOut.printf(getI18n("CSVData.HeaderComponentName"), component.name, row, col);
                        }
                        if (component != null && component.producesOutput()) {
                            csvOut.printf(getI18n("CSVData.HeaderComponentOutput"), component.name, row, col);
                        }
                    }
                }
                csvOut.println();
            }

            data = simulator.runSimulation(this.reactor, true, this::publish);

        } catch (Throwable e) {
            if (simulator.getCooldownTicks() == 0) {
                publish(formatI18n("Simulation.ErrorReactor", simulator.getReactorTicks()));
            } else {
                publish(formatI18n("Simulation.ErrorCooldown", simulator.getCooldownTicks()));
            }

            publish(e.toString());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                publish(stackTraceElement.toString());
            }

            if (csvOut != null) {
                csvOut.close();
            }
        }

        double elapsedTime = (double) (data.endTime - data.startTime) / 1.0e6;
        publish(formatI18n("Simulation.ElapsedTime", elapsedTime));
        completed = true;
        firePropertyChange("completed", null, true);
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String chunk : chunks) {
            if (chunk.isEmpty()) {
                output.setText(""); //NO18N
            } else {
                if (chunk.matches("R\\dC\\d:.*")) { //NO18N
                    String temp = chunk.substring(5);
                    int row = chunk.charAt(1) - '0';
                    int col = chunk.charAt(3) - '0';
                    if (temp.startsWith("0x")) { //NO18N
                        reactorButtonPanels[row][col].setBackground(Color.decode(temp));
                        switch (temp) {
                            case "0xC0C0C0":
                                reactorButtonPanels[row][col].setToolTipText(null);
                                break;
                            case "0xFF0000":
                                reactorButtonPanels[row][col].setToolTipText(getI18n("ComponentTooltip.Broken"));
                                break;
                            case "0xFFA500":
                                reactorButtonPanels[row][col].setToolTipText(getI18n("ComponentTooltip.ResidualHeat"));
                                break;
                        }
                    }
                } else {
                    output.append(chunk);
                }
            }
        }
    }

}

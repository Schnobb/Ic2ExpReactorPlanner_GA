package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.ReactorItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.function.Consumer;

import static Ic2ExpReactorPlanner.BundleHelper.formatI18n;
import static Ic2ExpReactorPlanner.BundleHelper.getI18n;

public class ReactorSimulator {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(getI18n("Simulation.DecimalFormat"));

    public int initialHeat;
    public boolean active;
    public int pauseTimer;

    private boolean reachedBelow50;
    private boolean reachedBurn;
    private boolean reachedEvaporate;
    private boolean reachedHurt;
    private boolean reachedLava;
    private boolean reachedExplode;

    private int activeTime;
    private int inactiveTime;
    private int currentActiveTime;
    private int minActiveTime;
    private int maxActiveTime;
    private int currentInactiveTime;
    private int minInactiveTime;
    private int maxInactiveTime;

    private double minEUOutput;
    private double maxEUOutput;
    private double minHeatOutput;
    private double maxHeatOutput;

    private boolean allFuelRodsDepleted;
    private boolean componentsIntact;
    private boolean anyRodsDepleted;
    private boolean showHeatingCoolingCalled = false;

    private int redstoneUsed;
    private int lapisUsed;

    private int reactorTicks;
    private int cooldownTicks;
    private int totalRodCount;

    private double totalHullHeating;
    private double totalComponentHeating;
    private double totalHullCooling;
    private double totalVentCooling;

    private final MaterialsList replacedItems;
    private final boolean[][] alreadyBroken;
    private final boolean[][] needsCooldown;

    private final ArrayList<ReactorItem> allComponents = new ArrayList<>(54);

    private Consumer<String> publisher;

    public ReactorSimulator() {
        replacedItems = new MaterialsList();
        alreadyBroken = new boolean[6][9]; // nice
        needsCooldown = new boolean[6][9]; // nice

        this.resetState();
    }

    public void resetState() {
        initialHeat = 0;
        active = true;
        pauseTimer = 0;

        reachedBelow50 = false;
        reachedBurn = false;
        reachedEvaporate = false;
        reachedHurt = false;
        reachedLava = false;
        reachedExplode = false;

        activeTime = 0;
        inactiveTime = 0;
        currentActiveTime = 0;
        minActiveTime = Integer.MAX_VALUE;
        maxActiveTime = 0;
        currentInactiveTime = 0;
        minInactiveTime = Integer.MAX_VALUE;
        maxInactiveTime = 0;

        minEUOutput = Double.MAX_VALUE;
        maxEUOutput = 0.0;
        minHeatOutput = Double.MAX_VALUE;
        maxHeatOutput = 0.0;

        allFuelRodsDepleted = false;
        componentsIntact = true;
        anyRodsDepleted = false;

        redstoneUsed = 0;
        lapisUsed = 0;

        reactorTicks = 0;
        cooldownTicks = 0;
        totalRodCount = 0;

        totalHullHeating = 0;
        totalComponentHeating = 0;
        totalHullCooling = 0;
        totalVentCooling = 0;

        replacedItems.clear();

        for (boolean[] row : alreadyBroken) {
            java.util.Arrays.fill(row, false);
        }
        for (boolean[] row : needsCooldown) {
            java.util.Arrays.fill(row, false);
        }
    }

    public int getReactorTicks() {
        return this.reactorTicks;
    }

    public int getCooldownTicks() {
        return this.cooldownTicks;
    }

    public SimulationData runSimulation(Reactor reactor) {
        return runSimulation(reactor, false, null);
    }

    public SimulationData runSimulation(Reactor reactor, boolean loggingEnabled, Consumer<String> publisher) {
        SimulationData data = new SimulationData();
        this.publisher = publisher;

        data.startTime = System.nanoTime();

        sendToPublisher(getI18n("Simulation.Started"));
        reactor.setCurrentHeat(initialHeat);
        reactor.clearVentedHeat();
        double minReactorHeat = initialHeat;
        double maxReactorHeat = initialHeat;
        reachedBelow50 = false;
        reachedBurn = initialHeat >= 0.4 * reactor.getMaxHeat();
        reachedEvaporate = initialHeat >= 0.5 * reactor.getMaxHeat();
        reachedHurt = initialHeat >= 0.7 * reactor.getMaxHeat();
        reachedLava = initialHeat >= 0.85 * reactor.getMaxHeat();
        reachedExplode = false;

        buildComponentList(reactor);

        for (ReactorItem component : allComponents) {
            component.clearCurrentHeat();
            component.clearDamage();
            totalRodCount += component.getRodCount();
            component.cacheNeighbors(reactor);
            sendToPublisher(String.format("R%dC%d:0xC0C0C0", component.getRow(), component.getCol())); //NOI18N
        }

        data.totalRodCount = totalRodCount;
        double lastEUoutput;
        double totalEUoutput = 0.0;
        double lastHeatOutput;
        double totalHeatOutput = 0.0;
        double maxGeneratedHeat = 0.0;
        allFuelRodsDepleted = false;
        componentsIntact = true;
        anyRodsDepleted = false;

        do {
            reactorTicks++;
            reactor.clearEUOutput();
            reactor.clearVentedHeat();

            for (ReactorItem component : allComponents) {
                component.preReactorTick();
            }

            if (active) {
                allFuelRodsDepleted = true; // assume rods depleted until one is found that isn't.
            }

            double generatedHeat = 0.0;

            for (ReactorItem component : allComponents) {
                if (component.isBroken())
                    continue;

                if (allFuelRodsDepleted && component.getRodCount() > 0) {
                    allFuelRodsDepleted = false;
                }

                if (active) {
                    generatedHeat += component.generateHeat();
                }

                component.dissipate();
                component.transfer();
            }

            maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
            minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
            checkReactorTemperature(reactor, data, reactorTicks);
            maxGeneratedHeat = Math.max(generatedHeat, maxGeneratedHeat);

            if (active) {
                for (ReactorItem component : allComponents) {
                    if (component.isBroken())
                        continue;

                    component.generateEnergy();
                }
            }

            lastEUoutput = reactor.getCurrentEUOutput();
            totalEUoutput += lastEUoutput;
            lastHeatOutput = reactor.getVentedHeat();
            totalHeatOutput += lastHeatOutput;

            if (reactor.getCurrentHeat() <= reactor.getMaxHeat()) {
                if (reactor.isPulsed() || reactor.isAutomated()) {
                    int clockPeriod = reactor.getOnPulse() + reactor.getOffPulse();
                    if (active) {
                        activeTime++;
                        currentActiveTime++;
                        if (reactor.isPulsed() && (reactor.getCurrentHeat() >= reactor.getSuspendTemp() || (reactorTicks % clockPeriod) >= reactor.getOnPulse())) {
                            active = false;
                            minActiveTime = Math.min(currentActiveTime, minActiveTime);
                            maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                            currentActiveTime = 0;
                        }
                    } else {
                        inactiveTime++;
                        currentInactiveTime++;
                        if (reactor.isAutomated() && pauseTimer > 0) {
                            pauseTimer--;
                        } else if ((reactor.isPulsed() && reactor.getCurrentHeat() <= reactor.getResumeTemp() && (reactorTicks % clockPeriod) < reactor.getOnPulse())) {
                            active = true;
                            minInactiveTime = Math.min(currentInactiveTime, minInactiveTime);
                            maxInactiveTime = Math.max(currentInactiveTime, maxInactiveTime);
                            currentInactiveTime = 0;
                        }
                    }
                }
                minEUOutput = Math.min(lastEUoutput, minEUOutput);
                maxEUOutput = Math.max(lastEUoutput, maxEUOutput);
                minHeatOutput = Math.min(lastHeatOutput, minHeatOutput);
                maxHeatOutput = Math.max(lastHeatOutput, maxHeatOutput);
            }
            calculateHeatingCooling(reactorTicks);
            handleAutomation(reactor, reactorTicks, loggingEnabled);
            handleBrokenComponents(reactor, data, reactorTicks, totalHeatOutput, totalRodCount, totalEUoutput, minReactorHeat, maxReactorHeat, loggingEnabled);
        } while (reactor.getCurrentHeat() < reactor.getMaxHeat() && (!allFuelRodsDepleted || lastEUoutput > 0 || lastHeatOutput > 0) && reactorTicks < reactor.getMaxSimulationTicks());

        data.minTemp = minReactorHeat;
        data.maxTemp = maxReactorHeat;
        sendToPublisher(formatI18n("Simulation.ReactorMinTemp", minReactorHeat));
        sendToPublisher(formatI18n("Simulation.ReactorMaxTemp", maxReactorHeat));
        if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
            sendToPublisher(formatI18n("Simulation.TimeWithoutExploding", reactorTicks));
            if (reactor.isPulsed()) {
                String rangeString = "";
                if (maxActiveTime > minActiveTime) {
                    rangeString = formatI18n("Simulation.ActiveTimeRange", minActiveTime, maxActiveTime);
                } else if (minActiveTime < activeTime) {
                    rangeString = formatI18n("Simulation.ActiveTimeSingle", minActiveTime);
                }
                sendToPublisher(formatI18n("Simulation.ActiveTime", activeTime, rangeString));
                rangeString = "";
                if (maxInactiveTime > minInactiveTime) {
                    rangeString = formatI18n("Simulation.InactiveTimeRange", minInactiveTime, maxInactiveTime);
                } else if (minInactiveTime < inactiveTime) {
                    rangeString = formatI18n("Simulation.InactiveTimeSingle", minInactiveTime);
                }
                sendToPublisher(formatI18n("Simulation.InactiveTime", inactiveTime, rangeString));
            }
            final String replacedItemsString = replacedItems.toString();
            if (!replacedItemsString.isEmpty()) {
                data.replacedItems = new MaterialsList(replacedItems);
                sendToPublisher(formatI18n("Simulation.ComponentsReplaced", replacedItemsString));
            }

            if (reactorTicks > 0) {
                data.totalReactorTicks = reactorTicks;
                if (reactor.isFluid()) {
                    data.totalHUoutput = 40 * totalHeatOutput;
                    data.avgHUoutput = 2 * totalHeatOutput / reactorTicks;
                    data.minHUoutput = 2 * minHeatOutput;
                    data.maxHUoutput = 2 * maxHeatOutput;
                    if (totalHeatOutput > 0) {
                        sendToPublisher(formatI18n("Simulation.HeatOutputs",
                                DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                DECIMAL_FORMAT.format(2 * totalHeatOutput / reactorTicks),
                                DECIMAL_FORMAT.format(2 * minHeatOutput),
                                DECIMAL_FORMAT.format(2 * maxHeatOutput)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                        }
                    }
                } else {
                    data.totalEUoutput = totalEUoutput;
                    data.avgEUOutput = totalEUoutput / (reactorTicks * 20);
                    data.minEUoutput = minEUOutput / 20.0;
                    data.maxEUoutput = maxEUOutput / 20.0;
                    if (totalEUoutput > 0) {
                        sendToPublisher(formatI18n("Simulation.EUOutputs",
                                DECIMAL_FORMAT.format(totalEUoutput),
                                DECIMAL_FORMAT.format(totalEUoutput / (reactorTicks * 20)),
                                DECIMAL_FORMAT.format(minEUOutput / 20.0),
                                DECIMAL_FORMAT.format(maxEUOutput / 20.0)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUOutput / 100 / totalRodCount, maxEUOutput / 100 / totalRodCount));
                        }
                    }
                }
            }

            if (reactor.getCurrentHeat() > 0.0) {
                sendToPublisher(formatI18n("Simulation.ReactorRemainingHeat", reactor.getCurrentHeat()));
            }

            double prevReactorHeat = reactor.getCurrentHeat();
            double prevTotalComponentHeat = 0.0;

            for (ReactorItem component : allComponents) {
                if (component.isBroken())
                    continue;

                if (component.getCurrentHeat() <= 0.0)
                    continue;

                prevTotalComponentHeat += component.getCurrentHeat();
                sendToPublisher(String.format("R%dC%d:0xFFA500", component.getRow(), component.getCol())); // NOI18N
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.RemainingHeat", component.getCurrentHeat()));
            }

            if (prevReactorHeat == 0.0 && prevTotalComponentHeat == 0.0) {
                sendToPublisher(getI18n("Simulation.NoCooldown"));
            } else if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
                double currentTotalComponentHeat = prevTotalComponentHeat;
                int reactorCooldownTime = 0;
                do {
                    reactor.clearVentedHeat();
                    prevReactorHeat = reactor.getCurrentHeat();
                    if (prevReactorHeat == 0.0) {
                        reactorCooldownTime = cooldownTicks;
                    }
                    for (int row = 0; row < 6; row++) {
                        for (int col = 0; col < 9; col++) {
                            ReactorItem component = reactor.getComponentAt(row, col);
                            if (component != null && !component.isBroken()) {
                                component.dissipate();
                                component.transfer();
                            }
                        }
                    }
                    lastHeatOutput = reactor.getVentedHeat();
                    totalHeatOutput += lastHeatOutput;
                    minEUOutput = Math.min(lastEUoutput, minEUOutput);
                    maxEUOutput = Math.max(lastEUoutput, maxEUOutput);
                    minHeatOutput = Math.min(lastHeatOutput, minHeatOutput);
                    maxHeatOutput = Math.max(lastHeatOutput, maxHeatOutput);
                    cooldownTicks++;
                    currentTotalComponentHeat = 0.0;
                    for (int row = 0; row < 6; row++) {
                        for (int col = 0; col < 9; col++) {
                            ReactorItem component = reactor.getComponentAt(row, col);
                            if (component != null && !component.isBroken()) {
                                currentTotalComponentHeat += component.getCurrentHeat();
                                if (component.getCurrentHeat() == 0.0 && needsCooldown[row][col]) {
                                    if (loggingEnabled)
                                        component.info.append(formatI18n("ComponentInfo.CooldownTime", cooldownTicks));
                                    needsCooldown[row][col] = false;
                                }
                            }
                        }
                    }
                } while (lastHeatOutput > 0 && cooldownTicks < 50000);
                if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
                    if (reactor.getCurrentHeat() == 0.0) {
                        sendToPublisher(formatI18n("Simulation.ReactorCooldownTime", reactorCooldownTime));
                    } else if (reactorCooldownTime > 0) {
                        sendToPublisher(formatI18n("Simulation.ReactorResidualHeat", reactor.getCurrentHeat(), reactorCooldownTime));
                    }
                    sendToPublisher(formatI18n("Simulation.TotalCooldownTime", cooldownTicks));
                }
            }
        } else {
            sendToPublisher(formatI18n("Simulation.ReactorOverheatedTime", reactorTicks));
            double explosionPower = 10.0;
            double explosionPowerMult = 1.0;

            for (ReactorItem component : allComponents) {
                explosionPower += component.getExplosionPowerOffset();
                explosionPowerMult *= component.getExplosionPowerMultiplier();
            }
            explosionPower *= explosionPowerMult;
            sendToPublisher(formatI18n("Simulation.ExplosionPower", explosionPower));
        }

        double totalEffectiveVentCooling = 0.0;
        double totalVentCoolingCapacity = 0.0;
        double totalCellCooling = 0.0;
        double totalCondensatorCooling = 0.0;

        for (ReactorItem component : allComponents) {
            if (component.getVentCoolingCapacity() > 0) {
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.UsedCooling", component.getBestVentCooling(), component.getVentCoolingCapacity()));
                totalEffectiveVentCooling += component.getBestVentCooling();
                totalVentCoolingCapacity += component.getVentCoolingCapacity();
            } else if (component.getBestCellCooling() > 0) {
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.ReceivedHeat", component.getBestCellCooling()));
                totalCellCooling += component.getBestCellCooling();
            } else if (component.getBestCondensatorCooling() > 0) {
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.ReceivedHeat", component.getBestCondensatorCooling()));
                totalCondensatorCooling += component.getBestCondensatorCooling();
            } else if (component.getMaxHeatGenerated() > 0) {
                if (!reactor.isFluid() && component.getMaxEUGenerated() > 0) {
                    if (loggingEnabled)
                        component.info.append(formatI18n("ComponentInfo.GeneratedEU", component.getMinEUGenerated(), component.getMaxEUGenerated()));
                }
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.GeneratedHeat", component.getMinHeatGenerated(), component.getMaxHeatGenerated()));
            }

            if (component.getMaxReachedHeat() > 0) {
                if (loggingEnabled)
                    component.info.append(formatI18n("ComponentInfo.ReachedHeat", component.getMaxReachedHeat(), component.getMaxHeat()));
            }
        }

        if (totalVentCoolingCapacity > 0) {
            sendToPublisher(formatI18n("Simulation.TotalVentCooling", totalEffectiveVentCooling, totalVentCoolingCapacity));
        }
        showHeatingCooling(reactor, data, reactorTicks);  // Call to show this info in case it hasn't already been shown, such as for an automated reactor.
        if (totalCellCooling > 0) {
            sendToPublisher(formatI18n("Simulation.TotalCellCooling", totalCellCooling));
        }
        if (totalCondensatorCooling > 0) {
            sendToPublisher(formatI18n("Simulation.TotalCondensatorCooling", totalCondensatorCooling));
        }
        if (maxGeneratedHeat > 0) {
            sendToPublisher(formatI18n("Simulation.MaxHeatGenerated", maxGeneratedHeat));
        }
        if (redstoneUsed > 0) {
            sendToPublisher(formatI18n("Simulation.RedstoneUsed", redstoneUsed));
        }
        if (lapisUsed > 0) {
            sendToPublisher(formatI18n("Simulation.LapisUsed", lapisUsed));
        }
        double totalCooling = totalEffectiveVentCooling + totalCellCooling + totalCondensatorCooling;
        if (totalCooling >= maxGeneratedHeat) {
            sendToPublisher(formatI18n("Simulation.ExcessCooling", totalCooling - maxGeneratedHeat));
        } else {
            sendToPublisher(formatI18n("Simulation.ExcessHeating", maxGeneratedHeat - totalCooling));
        }

        data.endTime = System.nanoTime();
        return data;
    }

    private void handleBrokenComponents(Reactor reactor, SimulationData data, int reactorTicks, final double totalHeatOutput, final int totalRodCount, final double totalEUoutput, final double minReactorHeat, final double maxReactorHeat, boolean logginEnabled) {
        for (ReactorItem component : allComponents) {
            int row = component.getRow();
            int col = component.getCol();
            if (component.isBroken() && !alreadyBroken[row][col]) {
                alreadyBroken[row][col] = true;
                if (component.getRodCount() == 0) {
                    sendToPublisher(String.format("R%dC%d:0xFF0000", row, col)); //NOI18N
                    if (logginEnabled)
                        component.info.append(formatI18n("ComponentInfo.BrokeTime", reactorTicks));
                    if (componentsIntact) {
                        componentsIntact = false;
                        data.firstComponentBrokenTime = reactorTicks;
                        data.firstComponentBrokenRow = row;
                        data.firstComponentBrokenCol = col;
                        data.firstComponentBrokenDescription = component.toString();
                        // publish(formatI18n("Simulation.FirstComponentBrokenDetails", component.toString(), row, col, reactorTicks));
                        if (reactor.isFluid()) {
                            data.prebreakTotalHUoutput = 40 * totalHeatOutput;
                            data.prebreakAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                            data.prebreakMinHUoutput = 2 * minHeatOutput;
                            data.prebreakMaxHUoutput = 2 * maxHeatOutput;
                            sendToPublisher(formatI18n("Simulation.HeatOutputsBeforeBreak",
                                    DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                    DECIMAL_FORMAT.format(2 * totalHeatOutput / reactorTicks),
                                    DECIMAL_FORMAT.format(2 * minHeatOutput),
                                    DECIMAL_FORMAT.format(2 * maxHeatOutput)));
                            if (totalRodCount > 0) {
                                sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                            }
                        } else {
                            data.prebreakTotalEUoutput = totalEUoutput;
                            data.prebreakAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                            data.prebreakMinEUoutput = minEUOutput / 20.0;
                            data.prebreakMaxEUoutput = maxEUOutput / 20.0;
                            sendToPublisher(formatI18n("Simulation.EUOutputsBeforeBreak",
                                    DECIMAL_FORMAT.format(totalEUoutput),
                                    DECIMAL_FORMAT.format(totalEUoutput / (reactorTicks * 20)),
                                    DECIMAL_FORMAT.format(minEUOutput / 20.0),
                                    DECIMAL_FORMAT.format(maxEUOutput / 20.0)));
                            if (totalRodCount > 0) {
                                sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUOutput / 100 / totalRodCount, maxEUOutput / 100 / totalRodCount));
                            }
                        }
                    }
                } else if (!anyRodsDepleted) {
                    anyRodsDepleted = true;
                    data.firstRodDepletedTime = reactorTicks;
                    data.firstRodDepletedRow = row;
                    data.firstRodDepletedCol = col;
                    data.firstRodDepletedDescription = component.toString();
                    sendToPublisher(formatI18n("Simulation.FirstRodDepletedDetails", component.toString(), row, col, reactorTicks));
                    if (reactor.isFluid()) {
                        data.predepleteTotalHUoutput = 40 * totalHeatOutput;
                        data.predepleteAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                        data.predepleteMinHUoutput = 2 * minHeatOutput;
                        data.predepleteMaxHUoutput = 2 * maxHeatOutput;
                        sendToPublisher(formatI18n("Simulation.HeatOutputsBeforeDepleted",
                                DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                DECIMAL_FORMAT.format(2 * totalHeatOutput / reactorTicks),
                                DECIMAL_FORMAT.format(2 * minHeatOutput),
                                DECIMAL_FORMAT.format(2 * maxHeatOutput)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                        }
                    } else {
                        data.predepleteTotalEUoutput = totalEUoutput;
                        data.predepleteAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                        data.predepleteMinEUoutput = minEUOutput / 20.0;
                        data.predepleteMaxEUoutput = maxEUOutput / 20.0;
                        sendToPublisher(formatI18n("Simulation.EUOutputsBeforeDepleted",
                                DECIMAL_FORMAT.format(totalEUoutput),
                                DECIMAL_FORMAT.format(totalEUoutput / (reactorTicks * 20)),
                                DECIMAL_FORMAT.format(minEUOutput / 20.0),
                                DECIMAL_FORMAT.format(maxEUOutput / 20.0)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUOutput / 100 / totalRodCount, maxEUOutput / 100 / totalRodCount));
                        }
                    }
                    data.predepleteMinTemp = minReactorHeat;
                    data.predepleteMaxTemp = maxReactorHeat;
                    sendToPublisher(formatI18n("Simulation.ReactorMinTempBeforeDepleted", minReactorHeat));
                    sendToPublisher(formatI18n("Simulation.ReactorMaxTempBeforeDepleted", maxReactorHeat));
                }
                showHeatingCooling(reactor, data, reactorTicks);
            }
        }
    }

    private void handleAutomation(Reactor reactor, final int reactorTicks, boolean logginEnabled) {
        for (ReactorItem component : allComponents) {
            if (reactor.isAutomated()) {
                if (component.getMaxHeat() > 1) {
                    if (component.getAutomationThreshold() > component.getInitialHeat() && component.getCurrentHeat() >= component.getAutomationThreshold()) {
                        component.clearCurrentHeat();
                        replacedItems.add(component.name);
                        if (logginEnabled)
                            component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                        if (component.getReactorPause() > 0) {
                            active = false;
                            pauseTimer = Math.max(pauseTimer, component.getReactorPause());
                            minActiveTime = Math.min(currentActiveTime, minActiveTime);
                            maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                            currentActiveTime = 0;
                        }
                    } else if (component.getAutomationThreshold() < component.getInitialHeat() && component.getCurrentHeat() <= component.getAutomationThreshold()) {
                        component.clearCurrentHeat();
                        replacedItems.add(component.name);
                        if (logginEnabled)
                            component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                        if (component.getReactorPause() > 0) {
                            active = false;
                            pauseTimer = Math.max(pauseTimer, component.getReactorPause());
                            minActiveTime = Math.min(currentActiveTime, minActiveTime);
                            maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                            currentActiveTime = 0;
                        }
                    }
                } else if (component.isBroken() || (component.getMaxDamage() > 1 && component.getCurrentDamage() >= component.getAutomationThreshold())) {
                    component.clearDamage();
                    replacedItems.add(component.name);
                    if (logginEnabled)
                        component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                    if (component.getReactorPause() > 0) {
                        active = false;
                        pauseTimer = Math.max(pauseTimer, component.getReactorPause());
                        minActiveTime = Math.min(currentActiveTime, minActiveTime);
                        maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                        currentActiveTime = 0;
                    }
                }
            }

            if (reactor.isUsingReactorCoolantInjectors() && component != null && component.needsCoolantInjected()) {
                component.injectCoolant();
                if ("rshCondensator".equals(component.baseName)) {
                    redstoneUsed++;
                } else if ("lzhCondensator".equals(component.baseName)) {
                    lapisUsed++;
                }
            }
        }
    }

    private void checkReactorTemperature(Reactor reactor, SimulationData data, final int reactorTicks) {
        if (reactor.getCurrentHeat() < 0.5 * reactor.getMaxHeat() && !reachedBelow50 && reachedEvaporate) {
            sendToPublisher(formatI18n("Simulation.TimeToBelow50", reactorTicks));
            reachedBelow50 = true;
            data.timeToBelow50 = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.4 * reactor.getMaxHeat() && !reachedBurn) {
            sendToPublisher(formatI18n("Simulation.TimeToBurn", reactorTicks));
            reachedBurn = true;
            data.timeToBurn = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.5 * reactor.getMaxHeat() && !reachedEvaporate) {
            sendToPublisher(formatI18n("Simulation.TimeToEvaporate", reactorTicks));
            reachedEvaporate = true;
            data.timeToEvaporate = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.7 * reactor.getMaxHeat() && !reachedHurt) {
            sendToPublisher(formatI18n("Simulation.TimeToHurt", reactorTicks));
            reachedHurt = true;
            data.timeToHurt = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.85 * reactor.getMaxHeat() && !reachedLava) {
            sendToPublisher(formatI18n("Simulation.TimeToLava", reactorTicks));
            reachedLava = true;
            data.timeToLava = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= reactor.getMaxHeat() && !reachedExplode) {
            sendToPublisher(formatI18n("Simulation.TimeToXplode", reactorTicks));
            reachedExplode = true;
            data.timeToXplode = reactorTicks;
        }
    }

    private void calculateHeatingCooling(final int reactorTicks) {
        if (reactorTicks > 20) {
            for (ReactorItem component : allComponents) {
                totalHullHeating += component.getCurrentHullHeating();
                totalComponentHeating += component.getCurrentComponentHeating();
                totalHullCooling += component.getCurrentHullCooling();
                totalVentCooling += component.getCurrentVentCooling();
            }
        }
    }

    private void buildComponentList(Reactor reactor) {
        allComponents.clear();
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                ReactorItem component = reactor.getComponentAt(y, x);
                if (component != null) {
                    allComponents.add(component);
                }
            }
        }
    }

    private void showHeatingCooling(Reactor reactor, SimulationData data, final int reactorTicks) {
        if (!showHeatingCoolingCalled) {
            showHeatingCoolingCalled = true;
            if (reactorTicks >= 40) {
                double totalHullCoolingCapacity = 0;
                double totalVentCoolingCapacity = 0;
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorItem component = reactor.getComponentAt(row, col);
                        if (component != null) {
                            totalHullCoolingCapacity += component.getHullCoolingCapacity();
                            totalVentCoolingCapacity += component.getVentCoolingCapacity();
                        }
                    }
                }
                data.hullHeating = totalHullHeating / (reactorTicks - 20);
                data.componentHeating = totalComponentHeating / (reactorTicks - 20);
                data.hullCooling = totalHullCooling / (reactorTicks - 20);
                data.hullCoolingCapacity = totalHullCoolingCapacity;
                data.ventCooling = totalVentCooling / (reactorTicks - 20);
                data.ventCoolingCapacity = totalVentCoolingCapacity;
                if (totalHullHeating > 0) {
                    sendToPublisher(formatI18n("Simulation.HullHeating", totalHullHeating / (reactorTicks - 20)));
                }
                if (totalComponentHeating > 0) {
                    sendToPublisher(formatI18n("Simulation.ComponentHeating", totalComponentHeating / (reactorTicks - 20)));
                }
                if (totalHullCoolingCapacity > 0) {
                    sendToPublisher(formatI18n("Simulation.HullCooling", totalHullCooling / (reactorTicks - 20), totalHullCoolingCapacity));
                }
                if (totalVentCoolingCapacity > 0) {
                    sendToPublisher(formatI18n("Simulation.VentCooling", totalVentCooling / (reactorTicks - 20), totalVentCoolingCapacity));
                }
            }
        }
    }

    private void sendToPublisher(String msg) {
        if (this.publisher != null)
            publisher.accept(msg);
    }
}

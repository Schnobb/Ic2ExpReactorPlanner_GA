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
    private boolean showHeatingCoolingCalled;

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
        this.replacedItems = new MaterialsList();
        this.alreadyBroken = new boolean[6][9]; // nice
        this.needsCooldown = new boolean[6][9]; // nice

        this.resetState();
    }

    public void resetState() {
        this.initialHeat = 0;
        this.active = true;
        this.pauseTimer = 0;

        this.reachedBelow50 = false;
        this.reachedBurn = false;
        this.reachedEvaporate = false;
        this.reachedHurt = false;
        this.reachedLava = false;
        this.reachedExplode = false;

        this.activeTime = 0;
        this.inactiveTime = 0;
        this.currentActiveTime = 0;
        this.minActiveTime = Integer.MAX_VALUE;
        this.maxActiveTime = 0;
        this.currentInactiveTime = 0;
        this.minInactiveTime = Integer.MAX_VALUE;
        this.maxInactiveTime = 0;

        this.minEUOutput = Double.MAX_VALUE;
        this.maxEUOutput = 0.0;
        this.minHeatOutput = Double.MAX_VALUE;
        this.maxHeatOutput = 0.0;

        this.allFuelRodsDepleted = false;
        this.componentsIntact = true;
        this.anyRodsDepleted = false;
        this.showHeatingCoolingCalled = false;

        this.redstoneUsed = 0;
        this.lapisUsed = 0;

        this.reactorTicks = 0;
        this.cooldownTicks = 0;
        this.totalRodCount = 0;

        this.totalHullHeating = 0;
        this.totalComponentHeating = 0;
        this.totalHullCooling = 0;
        this.totalVentCooling = 0;

        this.replacedItems.clear();

        for (boolean[] row : this.alreadyBroken) {
            java.util.Arrays.fill(row, false);
        }
        for (boolean[] row : this.needsCooldown) {
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
        return this.runSimulation(reactor, false, null);
    }

    public SimulationData runSimulation(Reactor reactor, boolean loggingEnabled, Consumer<String> publisher) {
        SimulationData data = new SimulationData();
        this.publisher = publisher;

        data.startTime = System.nanoTime();

        sendToPublisher(getI18n("Simulation.Started"));
        reactor.setCurrentHeat(this.initialHeat);
        reactor.clearVentedHeat();
        double minReactorHeat = this.initialHeat;
        double maxReactorHeat = this.initialHeat;
        this.reachedBelow50 = false;
        this.reachedBurn = this.initialHeat >= 0.4 * reactor.getMaxHeat();
        this.reachedEvaporate = this.initialHeat >= 0.5 * reactor.getMaxHeat();
        this.reachedHurt = this.initialHeat >= 0.7 * reactor.getMaxHeat();
        this.reachedLava = this.initialHeat >= 0.85 * reactor.getMaxHeat();
        this.reachedExplode = false;

        buildComponentList(reactor);

        for (ReactorItem component : this.allComponents) {
            component.clearCurrentHeat();
            component.clearDamage();
            this.totalRodCount += component.getRodCount();
            component.cacheNeighbors(reactor);
            sendToPublisher(String.format("R%dC%d:0xC0C0C0", component.getRow(), component.getCol())); //NOI18N
        }

        data.totalRodCount = this.totalRodCount;
        double lastEUoutput;
        double totalEUoutput = 0.0;
        double lastHeatOutput;
        double totalHeatOutput = 0.0;
        double maxGeneratedHeat = 0.0;
        this.allFuelRodsDepleted = false;
        this.componentsIntact = true;
        this.anyRodsDepleted = false;

        do {
            this.reactorTicks++;
            reactor.clearEUOutput();
            reactor.clearVentedHeat();

            for (ReactorItem component : this.allComponents) {
                component.preReactorTick();
            }

            if (this.active) {
                this.allFuelRodsDepleted = true; // assume rods depleted until one is found that isn't.
            }

            double generatedHeat = 0.0;

            for (ReactorItem component : this.allComponents) {
                if (component.isBroken())
                    continue;

                if (this.allFuelRodsDepleted && component.getRodCount() > 0) {
                    this.allFuelRodsDepleted = false;
                }

                if (this.active) {
                    generatedHeat += component.generateHeat();
                }

                component.dissipate();
                component.transfer();
            }

            maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
            minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
            checkReactorTemperature(reactor, data, this.reactorTicks);
            maxGeneratedHeat = Math.max(generatedHeat, maxGeneratedHeat);

            if (this.active) {
                for (ReactorItem component : this.allComponents) {
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
                    if (this.active) {
                        this.activeTime++;
                        this.currentActiveTime++;
                        if (reactor.isPulsed() && (reactor.getCurrentHeat() >= reactor.getSuspendTemp() || (reactorTicks % clockPeriod) >= reactor.getOnPulse())) {
                            this.active = false;
                            this.minActiveTime = Math.min(this.currentActiveTime, this.minActiveTime);
                            this.maxActiveTime = Math.max(this.currentActiveTime, this.maxActiveTime);
                            this.currentActiveTime = 0;
                        }
                    } else {
                        this.inactiveTime++;
                        this.currentInactiveTime++;
                        if (reactor.isAutomated() && this.pauseTimer > 0) {
                            this.pauseTimer--;
                        } else if ((reactor.isPulsed() && reactor.getCurrentHeat() <= reactor.getResumeTemp() && (this.reactorTicks % clockPeriod) < reactor.getOnPulse())) {
                            this.active = true;
                            this.minInactiveTime = Math.min(this.currentInactiveTime, this.minInactiveTime);
                            this.maxInactiveTime = Math.max(this.currentInactiveTime, this.maxInactiveTime);
                            this.currentInactiveTime = 0;
                        }
                    }
                }
                this.minEUOutput = Math.min(lastEUoutput, this.minEUOutput);
                this.maxEUOutput = Math.max(lastEUoutput, this.maxEUOutput);
                this.minHeatOutput = Math.min(lastHeatOutput, this.minHeatOutput);
                this.maxHeatOutput = Math.max(lastHeatOutput, this.maxHeatOutput);
            }
            calculateHeatingCooling(this.reactorTicks);
            handleAutomation(reactor, this.reactorTicks, loggingEnabled);
            handleBrokenComponents(reactor, data, this.reactorTicks, totalHeatOutput, this.totalRodCount, totalEUoutput, minReactorHeat, maxReactorHeat, loggingEnabled);
        } while (reactor.getCurrentHeat() < reactor.getMaxHeat() && (!this.allFuelRodsDepleted || lastEUoutput > 0 || lastHeatOutput > 0) && this.reactorTicks < reactor.getMaxSimulationTicks());

        data.minTemp = minReactorHeat;
        data.maxTemp = maxReactorHeat;
        sendToPublisher(formatI18n("Simulation.ReactorMinTemp", minReactorHeat));
        sendToPublisher(formatI18n("Simulation.ReactorMaxTemp", maxReactorHeat));
        if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
            sendToPublisher(formatI18n("Simulation.TimeWithoutExploding", this.reactorTicks));
            if (reactor.isPulsed()) {
                String rangeString = "";
                if (this.maxActiveTime > this.minActiveTime) {
                    rangeString = formatI18n("Simulation.ActiveTimeRange", this.minActiveTime, this.maxActiveTime);
                } else if (this.minActiveTime < this.activeTime) {
                    rangeString = formatI18n("Simulation.ActiveTimeSingle", this.minActiveTime);
                }
                sendToPublisher(formatI18n("Simulation.ActiveTime", this.activeTime, rangeString));
                rangeString = "";
                if (this.maxInactiveTime > this.minInactiveTime) {
                    rangeString = formatI18n("Simulation.InactiveTimeRange", this.minInactiveTime, this.maxInactiveTime);
                } else if (this.minInactiveTime < this.inactiveTime) {
                    rangeString = formatI18n("Simulation.InactiveTimeSingle", this.minInactiveTime);
                }
                sendToPublisher(formatI18n("Simulation.InactiveTime", this.inactiveTime, rangeString));
            }
            final String replacedItemsString = this.replacedItems.toString();
            if (!replacedItemsString.isEmpty()) {
                data.replacedItems = new MaterialsList(this.replacedItems);
                sendToPublisher(formatI18n("Simulation.ComponentsReplaced", replacedItemsString));
            }

            if (this.reactorTicks > 0) {
                data.totalReactorTicks = this.reactorTicks;
                if (reactor.isFluid()) {
                    data.totalHUoutput = 40 * totalHeatOutput;
                    data.avgHUoutput = 2 * totalHeatOutput / this.reactorTicks;
                    data.minHUoutput = 2 * this.minHeatOutput;
                    data.maxHUoutput = 2 * this.maxHeatOutput;
                    if (totalHeatOutput > 0) {
                        sendToPublisher(formatI18n("Simulation.HeatOutputs",
                                DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                DECIMAL_FORMAT.format(2 * totalHeatOutput / this.reactorTicks),
                                DECIMAL_FORMAT.format(2 * this.minHeatOutput),
                                DECIMAL_FORMAT.format(2 * this.maxHeatOutput)));
                        if (this.totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / this.reactorTicks / 4 / this.totalRodCount, this.minHeatOutput / 4 / this.totalRodCount, this.maxHeatOutput / 4 / this.totalRodCount));
                        }
                    }
                } else {
                    data.totalEUoutput = totalEUoutput;
                    data.avgEUOutput = totalEUoutput / (this.reactorTicks * 20);
                    data.minEUoutput = this.minEUOutput / 20.0;
                    data.maxEUoutput = this.maxEUOutput / 20.0;
                    if (totalEUoutput > 0) {
                        sendToPublisher(formatI18n("Simulation.EUOutputs",
                                DECIMAL_FORMAT.format(totalEUoutput),
                                DECIMAL_FORMAT.format(totalEUoutput / (this.reactorTicks * 20)),
                                DECIMAL_FORMAT.format(this.minEUOutput / 20.0),
                                DECIMAL_FORMAT.format(this.maxEUOutput / 20.0)));
                        if (this.totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / this.reactorTicks / 100 / this.totalRodCount, this.minEUOutput / 100 / this.totalRodCount, this.maxEUOutput / 100 / this.totalRodCount));
                        }
                    }
                }
            }

            if (reactor.getCurrentHeat() > 0.0) {
                sendToPublisher(formatI18n("Simulation.ReactorRemainingHeat", reactor.getCurrentHeat()));
            }

            double prevReactorHeat = reactor.getCurrentHeat();
            double prevTotalComponentHeat = 0.0;

            for (ReactorItem component : this.allComponents) {
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
                        reactorCooldownTime = this.cooldownTicks;
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
                    this.minEUOutput = Math.min(lastEUoutput, this.minEUOutput);
                    this.maxEUOutput = Math.max(lastEUoutput, this.maxEUOutput);
                    this.minHeatOutput = Math.min(lastHeatOutput, this.minHeatOutput);
                    this.maxHeatOutput = Math.max(lastHeatOutput, this.maxHeatOutput);
                    this.cooldownTicks++;
                    currentTotalComponentHeat = 0.0;
                    for (int row = 0; row < 6; row++) {
                        for (int col = 0; col < 9; col++) {
                            ReactorItem component = reactor.getComponentAt(row, col);
                            if (component != null && !component.isBroken()) {
                                currentTotalComponentHeat += component.getCurrentHeat();
                                if (component.getCurrentHeat() == 0.0 && this.needsCooldown[row][col]) {
                                    if (loggingEnabled)
                                        component.info.append(formatI18n("ComponentInfo.CooldownTime", this.cooldownTicks));
                                    this.needsCooldown[row][col] = false;
                                }
                            }
                        }
                    }
                } while (lastHeatOutput > 0 && this.cooldownTicks < 50000);
                if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
                    if (reactor.getCurrentHeat() == 0.0) {
                        sendToPublisher(formatI18n("Simulation.ReactorCooldownTime", reactorCooldownTime));
                    } else if (reactorCooldownTime > 0) {
                        sendToPublisher(formatI18n("Simulation.ReactorResidualHeat", reactor.getCurrentHeat(), reactorCooldownTime));
                    }
                    sendToPublisher(formatI18n("Simulation.TotalCooldownTime", this.cooldownTicks));
                }
            }
        } else {
            sendToPublisher(formatI18n("Simulation.ReactorOverheatedTime", this.reactorTicks));
            double explosionPower = 10.0;
            double explosionPowerMult = 1.0;

            for (ReactorItem component : this.allComponents) {
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

        for (ReactorItem component : this.allComponents) {
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
        showHeatingCooling(reactor, data, this.reactorTicks);  // Call to show this info in case it hasn't already been shown, such as for an automated reactor.
        if (totalCellCooling > 0) {
            sendToPublisher(formatI18n("Simulation.TotalCellCooling", totalCellCooling));
        }
        if (totalCondensatorCooling > 0) {
            sendToPublisher(formatI18n("Simulation.TotalCondensatorCooling", totalCondensatorCooling));
        }
        if (maxGeneratedHeat > 0) {
            sendToPublisher(formatI18n("Simulation.MaxHeatGenerated", maxGeneratedHeat));
        }
        if (this.redstoneUsed > 0) {
            sendToPublisher(formatI18n("Simulation.RedstoneUsed", this.redstoneUsed));
        }
        if (this.lapisUsed > 0) {
            sendToPublisher(formatI18n("Simulation.LapisUsed", this.lapisUsed));
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
        for (ReactorItem component : this.allComponents) {
            int row = component.getRow();
            int col = component.getCol();
            if (component.isBroken() && !this.alreadyBroken[row][col]) {
                this.alreadyBroken[row][col] = true;
                if (component.getRodCount() == 0) {
                    sendToPublisher(String.format("R%dC%d:0xFF0000", row, col)); //NOI18N
                    if (logginEnabled)
                        component.info.append(formatI18n("ComponentInfo.BrokeTime", reactorTicks));
                    if (this.componentsIntact) {
                        this.componentsIntact = false;
                        data.firstComponentBrokenTime = reactorTicks;
                        data.firstComponentBrokenRow = row;
                        data.firstComponentBrokenCol = col;
                        data.firstComponentBrokenDescription = component.toString();
                        // publish(formatI18n("Simulation.FirstComponentBrokenDetails", component.toString(), row, col, reactorTicks));
                        if (reactor.isFluid()) {
                            data.prebreakTotalHUoutput = 40 * totalHeatOutput;
                            data.prebreakAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                            data.prebreakMinHUoutput = 2 * this.minHeatOutput;
                            data.prebreakMaxHUoutput = 2 * this.maxHeatOutput;
                            sendToPublisher(formatI18n("Simulation.HeatOutputsBeforeBreak",
                                    DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                    DECIMAL_FORMAT.format(2 * totalHeatOutput / reactorTicks),
                                    DECIMAL_FORMAT.format(2 * this.minHeatOutput),
                                    DECIMAL_FORMAT.format(2 * this.maxHeatOutput)));
                            if (totalRodCount > 0) {
                                sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, this.minHeatOutput / 4 / totalRodCount, this.maxHeatOutput / 4 / totalRodCount));
                            }
                        } else {
                            data.prebreakTotalEUoutput = totalEUoutput;
                            data.prebreakAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                            data.prebreakMinEUoutput = this.minEUOutput / 20.0;
                            data.prebreakMaxEUoutput = this.maxEUOutput / 20.0;
                            sendToPublisher(formatI18n("Simulation.EUOutputsBeforeBreak",
                                    DECIMAL_FORMAT.format(totalEUoutput),
                                    DECIMAL_FORMAT.format(totalEUoutput / (reactorTicks * 20)),
                                    DECIMAL_FORMAT.format(this.minEUOutput / 20.0),
                                    DECIMAL_FORMAT.format(this.maxEUOutput / 20.0)));
                            if (totalRodCount > 0) {
                                sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, this.minEUOutput / 100 / totalRodCount, this.maxEUOutput / 100 / totalRodCount));
                            }
                        }
                    }
                } else if (!this.anyRodsDepleted) {
                    this.anyRodsDepleted = true;
                    data.firstRodDepletedTime = reactorTicks;
                    data.firstRodDepletedRow = row;
                    data.firstRodDepletedCol = col;
                    data.firstRodDepletedDescription = component.toString();
                    sendToPublisher(formatI18n("Simulation.FirstRodDepletedDetails", component.toString(), row, col, reactorTicks));
                    if (reactor.isFluid()) {
                        data.predepleteTotalHUoutput = 40 * totalHeatOutput;
                        data.predepleteAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                        data.predepleteMinHUoutput = 2 * this.minHeatOutput;
                        data.predepleteMaxHUoutput = 2 * this.maxHeatOutput;
                        sendToPublisher(formatI18n("Simulation.HeatOutputsBeforeDepleted",
                                DECIMAL_FORMAT.format(40 * totalHeatOutput),
                                DECIMAL_FORMAT.format(2 * totalHeatOutput / reactorTicks),
                                DECIMAL_FORMAT.format(2 * this.minHeatOutput),
                                DECIMAL_FORMAT.format(2 * this.maxHeatOutput)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, this.minHeatOutput / 4 / totalRodCount, this.maxHeatOutput / 4 / totalRodCount));
                        }
                    } else {
                        data.predepleteTotalEUoutput = totalEUoutput;
                        data.predepleteAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                        data.predepleteMinEUoutput = this.minEUOutput / 20.0;
                        data.predepleteMaxEUoutput = this.maxEUOutput / 20.0;
                        sendToPublisher(formatI18n("Simulation.EUOutputsBeforeDepleted",
                                DECIMAL_FORMAT.format(totalEUoutput),
                                DECIMAL_FORMAT.format(totalEUoutput / (reactorTicks * 20)),
                                DECIMAL_FORMAT.format(this.minEUOutput / 20.0),
                                DECIMAL_FORMAT.format(this.maxEUOutput / 20.0)));
                        if (totalRodCount > 0) {
                            sendToPublisher(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, this.minEUOutput / 100 / totalRodCount, this.maxEUOutput / 100 / totalRodCount));
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

    private void handleAutomation(Reactor reactor, final int reactorTicks, boolean loggingEnabled) {
        for (ReactorItem component : this.allComponents) {
            if (reactor.isAutomated()) {
                if (component.getMaxHeat() > 1) {
                    if (component.getAutomationThreshold() > component.getInitialHeat() && component.getCurrentHeat() >= component.getAutomationThreshold()) {
                        component.clearCurrentHeat();
                        this.replacedItems.add(component.name);
                        if (loggingEnabled)
                            component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                        if (component.getReactorPause() > 0) {
                            this.active = false;
                            this.pauseTimer = Math.max(this.pauseTimer, component.getReactorPause());
                            this.minActiveTime = Math.min(this.currentActiveTime, this.minActiveTime);
                            this.maxActiveTime = Math.max(this.currentActiveTime, this.maxActiveTime);
                            this.currentActiveTime = 0;
                        }
                    } else if (component.getAutomationThreshold() < component.getInitialHeat() && component.getCurrentHeat() <= component.getAutomationThreshold()) {
                        component.clearCurrentHeat();
                        this.replacedItems.add(component.name);
                        if (loggingEnabled)
                            component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                        if (component.getReactorPause() > 0) {
                            this.active = false;
                            this.pauseTimer = Math.max(this.pauseTimer, component.getReactorPause());
                            this.minActiveTime = Math.min(this.currentActiveTime, this.minActiveTime);
                            this.maxActiveTime = Math.max(this.currentActiveTime, this.maxActiveTime);
                            this.currentActiveTime = 0;
                        }
                    }
                } else if (component.isBroken() || (component.getMaxDamage() > 1 && component.getCurrentDamage() >= component.getAutomationThreshold())) {
                    component.clearDamage();
                    this.replacedItems.add(component.name);
                    if (loggingEnabled)
                        component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                    if (component.getReactorPause() > 0) {
                        this.active = false;
                        this.pauseTimer = Math.max(this.pauseTimer, component.getReactorPause());
                        this.minActiveTime = Math.min(this.currentActiveTime, this.minActiveTime);
                        this.maxActiveTime = Math.max(this.currentActiveTime, this.maxActiveTime);
                        this.currentActiveTime = 0;
                    }
                }
            }

            if (reactor.isUsingReactorCoolantInjectors() && component != null && component.needsCoolantInjected()) {
                component.injectCoolant();
                if ("rshCondensator".equals(component.baseName)) {
                    this.redstoneUsed++;
                } else if ("lzhCondensator".equals(component.baseName)) {
                    this.lapisUsed++;
                }
            }
        }
    }

    private void checkReactorTemperature(Reactor reactor, SimulationData data, final int reactorTicks) {
        if (reactor.getCurrentHeat() < 0.5 * reactor.getMaxHeat() && !this.reachedBelow50 && this.reachedEvaporate) {
            sendToPublisher(formatI18n("Simulation.TimeToBelow50", reactorTicks));
            this.reachedBelow50 = true;
            data.timeToBelow50 = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.4 * reactor.getMaxHeat() && !this.reachedBurn) {
            sendToPublisher(formatI18n("Simulation.TimeToBurn", reactorTicks));
            this.reachedBurn = true;
            data.timeToBurn = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.5 * reactor.getMaxHeat() && !this.reachedEvaporate) {
            sendToPublisher(formatI18n("Simulation.TimeToEvaporate", reactorTicks));
            this.reachedEvaporate = true;
            data.timeToEvaporate = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.7 * reactor.getMaxHeat() && !this.reachedHurt) {
            sendToPublisher(formatI18n("Simulation.TimeToHurt", reactorTicks));
            this.reachedHurt = true;
            data.timeToHurt = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= 0.85 * reactor.getMaxHeat() && !this.reachedLava) {
            sendToPublisher(formatI18n("Simulation.TimeToLava", reactorTicks));
            this.reachedLava = true;
            data.timeToLava = reactorTicks;
        }
        if (reactor.getCurrentHeat() >= reactor.getMaxHeat() && !this.reachedExplode) {
            sendToPublisher(formatI18n("Simulation.TimeToXplode", reactorTicks));
            this.reachedExplode = true;
            data.timeToXplode = reactorTicks;
        }
    }

    private void calculateHeatingCooling(final int reactorTicks) {
        if (reactorTicks > 20) {
            for (ReactorItem component : this.allComponents) {
                this.totalHullHeating += component.getCurrentHullHeating();
                this.totalComponentHeating += component.getCurrentComponentHeating();
                this.totalHullCooling += component.getCurrentHullCooling();
                this.totalVentCooling += component.getCurrentVentCooling();
            }
        }
    }

    private void buildComponentList(Reactor reactor) {
        this.allComponents.clear();
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                ReactorItem component = reactor.getComponentAt(y, x);
                if (component != null) {
                    this.allComponents.add(component);
                }
            }
        }
    }

    private void showHeatingCooling(Reactor reactor, SimulationData data, final int reactorTicks) {
        if (!this.showHeatingCoolingCalled) {
            this.showHeatingCoolingCalled = true;
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
                if (this.totalHullHeating > 0) {
                    sendToPublisher(formatI18n("Simulation.HullHeating", this.totalHullHeating / (reactorTicks - 20)));
                }
                if (this.totalComponentHeating > 0) {
                    sendToPublisher(formatI18n("Simulation.ComponentHeating", this.totalComponentHeating / (reactorTicks - 20)));
                }
                if (totalHullCoolingCapacity > 0) {
                    sendToPublisher(formatI18n("Simulation.HullCooling", this.totalHullCooling / (reactorTicks - 20), totalHullCoolingCapacity));
                }
                if (totalVentCoolingCapacity > 0) {
                    sendToPublisher(formatI18n("Simulation.VentCooling", this.totalVentCooling / (reactorTicks - 20), totalVentCoolingCapacity));
                }
            }
        }
    }

    private void sendToPublisher(String msg) {
        if (this.publisher != null)
            this.publisher.accept(msg);
    }
}

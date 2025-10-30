package Ic2ExpReactorPlanner.components;

import Ic2ExpReactorPlanner.Reactor;

import java.awt.*;

public interface IReactorItem {
    int getId();

    String getName();

    String getBaseName();

    StringBuffer getInfo();

    Image getImage();

    double getMaxDamage();

    double getMaxHeat();

    double getInitialHeat();

    void setInitialHeat(double value);

    int getAutomationThreshold();

    void setAutomationThreshold(int value);

    int getReactorPause();

    void setReactorPause(int value);

    double getCurrentDamage();

    double getCurrentHeat();

    double getMaxReachedHeat();

    double getCurrentEUGenerated();

    double getMinEUGenerated();

    double getMaxEUGenerated();

    double getCurrentHeatGenerated();

    double getMinHeatGenerated();

    double getMaxHeatGenerated();

    double getCurrentHullHeating();

    double getCurrentComponentHeating();

    double getCurrentHullCooling();

    double getCurrentVentCooling();

    double getBestVentCooling();

    double getCurrentCellCooling();

    double getBestCellCooling();

    double getCurrentCondensatorCooling();

    double getBestCondensatorCooling();

    @Override
    String toString();

    boolean isHeatAcceptor();

    boolean isCoolable();

    boolean isNeutronReflector();

    void preReactorTick();

    double generateHeat();

    double generateEnergy();

    double dissipate();

    void transfer();

    void addToReactor(Reactor parent, int row, int col);

    void removeFromReactor();

    void clearCurrentHeat();

    double adjustCurrentHeat(double heat);

    void clearDamage();

    void applyDamage(double damage);

    boolean isBroken();

    int getRodCount();

    double getExplosionPowerOffset();

    double getExplosionPowerMultiplier();

    double getVentCoolingCapacity();

    double getHullCoolingCapacity();

    double getCurrentOutput();

    boolean producesOutput();

    boolean needsCoolantInjected();

    void injectCoolant();

    int getRow();

    int getCol();

    void cacheNeighbors(Reactor reactor);
}

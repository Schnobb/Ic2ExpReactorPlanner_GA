/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.old.components;

import Ic2ExpReactorPlanner.components.IReactorItem;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a heat exchanger of some sort in a reactor.
 * @author Brian McCloud
 */
public class Exchanger extends ReactorItem {
    
    private final int switchSide;
    private final int switchReactor;

    private final List<IReactorItem> heatableNeighbors = new ArrayList<>(4);
    
    public Exchanger(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod, final int switchSide, final int switchReactor) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.switchSide = switchSide;
        this.switchReactor = switchReactor;
    }
    
    public Exchanger(final Exchanger other) {
        super(other);
        this.switchSide = other.switchSide;
        this.switchReactor = other.switchReactor;
    }
    
    @Override
    public void transfer() {
        heatableNeighbors.clear();

        for (var component : adjacentNeighbors) {
            if (component != null && component.isHeatAcceptor()) {
                heatableNeighbors.add(component);
            }
        }

        // Code adapted from decompiled IC2 code, class ItemReactorHeatSwitch, with permission from Thunderdark.
        double myHeat = 0;
        if (switchSide > 0) {
            for (var heatableNeighbor : heatableNeighbors) {
                double mymed = getCurrentHeat() * 100.0 / getMaxHeat();
                double heatablemed = heatableNeighbor.getCurrentHeat() * 100.0 / heatableNeighbor.getMaxHeat();

                double add = (int) (heatableNeighbor.getMaxHeat() / 100.0 * (heatablemed + mymed / 2.0));
                if (add > switchSide) {
                    add = switchSide;
                }
                if (heatablemed + mymed / 2.0 < 1.0) {
                    add = switchSide / 2;
                }
                if (heatablemed + mymed / 2.0 < 0.75) {
                    add = switchSide / 4;
                }
                if (heatablemed + mymed / 2.0 < 0.5) {
                    add = switchSide / 8;
                }
                if (heatablemed + mymed / 2.0 < 0.25) {
                    add = 1;
                }
                if (Math.round(heatablemed * 10.0) / 10.0 > Math.round(mymed * 10.0) / 10.0) {
                    add -= 2 * add;
                } else if (Math.round(heatablemed * 10.0) / 10.0 == Math.round(mymed * 10.0) / 10.0) {
                    add = 0;
                }
                myHeat -= add;
                if (add > 0) {
                    currentComponentHeating += add;
                }
                add = heatableNeighbor.adjustCurrentHeat(add);
                myHeat += add;
            }
        }
        if (switchReactor > 0) {
            double mymed = getCurrentHeat() * 100.0 / getMaxHeat();
            double Reactormed = parent.getCurrentHeat() * 100.0 / parent.getMaxHeat();

            int add = (int) Math.round(parent.getMaxHeat() / 100.0 * (Reactormed + mymed / 2.0));
            if (add > switchReactor) {
                add = switchReactor;
            }
            if (Reactormed + mymed / 2.0 < 1.0) {
                add = switchSide / 2;
            }
            if (Reactormed + mymed / 2.0 < 0.75) {
                add = switchSide / 4;
            }
            if (Reactormed + mymed / 2.0 < 0.5) {
                add = switchSide / 8;
            }
            if (Reactormed + mymed / 2.0 < 0.25) {
                add = 1;
            }
            if (Math.round(Reactormed * 10.0) / 10.0 > Math.round(mymed * 10.0) / 10.0) {
                add -= 2 * add;
            } else if (Math.round(Reactormed * 10.0) / 10.0 == Math.round(mymed * 10.0) / 10.0) {
                add = 0;
            }
            myHeat -= add;
            parent.adjustCurrentHeat(add);
            if (add > 0) {
                currentHullHeating = add;
            } else {
                currentHullCooling = -add;
            }
        }
        adjustCurrentHeat(myHeat);
    }
    
    @Override
    public double getHullCoolingCapacity() {
        return switchReactor;
    }
    
}

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
 * Represents some kind of vent in a reactor.
 * @author Brian McCloud
 */
public class Vent extends ReactorItem {
    
    private final int selfVent;
    private final int hullDraw;
    private final int sideVent;

    private final List<IReactorItem> coolableNeighbors = new ArrayList<>(4);
    
    public Vent(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod,
            final int selfVent, final int hullDraw, final int sideVent) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.selfVent = selfVent;
        this.hullDraw = hullDraw;
        this.sideVent = sideVent;
    }
    
    public Vent(final Vent other) {
        super(other);
        this.selfVent = other.selfVent;
        this.hullDraw = other.hullDraw;
        this.sideVent = other.sideVent;
    }
    
    @Override
    public double dissipate() {
        double deltaHeat = Math.min(hullDraw, parent.getCurrentHeat());
        currentHullCooling = deltaHeat;
        parent.adjustCurrentHeat(-deltaHeat);
        this.adjustCurrentHeat(deltaHeat);
        final double currentDissipation = Math.min(selfVent, getCurrentHeat());
        currentVentCooling = currentDissipation;
        parent.ventHeat(currentDissipation);
        adjustCurrentHeat(-currentDissipation);
        if (sideVent > 0) {
            coolableNeighbors.clear();

            for (var component : adjacentNeighbors) {
                if (component != null && component.isCoolable()) {
                    coolableNeighbors.add(component);
                }
            }

            for (var coolableNeighbor : coolableNeighbors) {
                double rejectedCooling = coolableNeighbor.adjustCurrentHeat(-sideVent);
                double tempDissipatedHeat = sideVent + rejectedCooling;
                parent.ventHeat(tempDissipatedHeat);
                currentVentCooling += tempDissipatedHeat;
            }
        }
        bestVentCooling = Math.max(bestVentCooling, currentVentCooling);
        return currentDissipation;
    }
    
    @Override
    public double getVentCoolingCapacity() {
        double result = selfVent;
        if (sideVent > 0) {
            for (var component : adjacentNeighbors) {
                if (component != null && component.isCoolable()) {
                    result += sideVent;
                }
            }
        }
        return result;
    }
    
    @Override
    public double getHullCoolingCapacity() {
        return hullDraw;
    }
    
    @Override
    public double getCurrentOutput() {
        return currentVentCooling;
    }
 }

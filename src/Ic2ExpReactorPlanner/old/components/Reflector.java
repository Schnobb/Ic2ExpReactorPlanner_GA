/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.old.components;

import java.awt.*;

/**
 * Represents a neutron reflector in a reactor.
 * @author Brian McCloud
 */
public class Reflector extends ReactorItem {
    
    private static String mcVersion = "1.12.2";
    private final boolean is1710;
    
    public Reflector(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.is1710 = "1.7.10".equals(mcVersion);
    }
    
    public Reflector(final Reflector other) {
        super(other);
        this.is1710 = "1.7.10".equals(mcVersion);
    }
    
    @Override
    public boolean isNeutronReflector() {
        return !isBroken();
    }

    @Override
    public double generateHeat() {
        for (var component : adjacentNeighbors) {
            if (component != null) {
                applyDamage(component.getRodCount());
            }
        }

        return 0;
    }
    
    @Override
    public double getMaxDamage() {
        if (maxDamage > 1 && this.is1710) {
            return maxDamage / 3;
        }
        return maxDamage;
    }
    
    public static void setMcVersion(String newVersion) {
        mcVersion = newVersion;
    }
}

package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.IReactorItem;
import Ic2ExpReactorPlanner.components.ReactorItem;

public interface IComponentFactory {
    /**
     * Gets a default instances of the specified component (such as for drawing button images)
     *
     * @param id the id of the component.
     * @return the component with the specified id, or null if the id is out of range.
     */
    IReactorItem getDefaultComponent(int id);

    /**
     * Get the number of defined components.
     *
     * @return the number of defined components.
     */
    int getComponentCount();

    IReactorItem getDefaultComponent(String name);

    IReactorItem createComponent(int id);

    IReactorItem createComponent(String name);
}

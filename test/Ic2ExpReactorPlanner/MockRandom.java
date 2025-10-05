package Ic2ExpReactorPlanner;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class MockRandom extends Random {
    private final Queue<Integer> intQueue = new LinkedList<>();
    private final Queue<Double> doubleQueue = new LinkedList<>();

    /**
     * Sets the sequence of integers that nextInt() will return.
     * @param values The sequence of integers to be returned in order.
     */
    public void setIntValues(Integer... values) {
        intQueue.clear();
        Collections.addAll(intQueue, values);
    }

    /**
     * Sets the sequence of doubles that nextDouble() will return.
     * @param values The sequence of doubles to be returned in order.
     */
    public void setDoubleValues(Double... values) {
        doubleQueue.clear();
        Collections.addAll(doubleQueue, values);
    }

    @Override
    public int nextInt(int bound) {
        if (intQueue.isEmpty()) {
            throw new IllegalStateException("MockRandom's int queue is empty! Test was not set up correctly.");
        }
        return intQueue.poll();
    }

    @Override
    public double nextDouble() {
        if (doubleQueue.isEmpty()) {
            throw new IllegalStateException("MockRandom's double queue is empty! Test was not set up correctly.");
        }
        return doubleQueue.poll();
    }
}

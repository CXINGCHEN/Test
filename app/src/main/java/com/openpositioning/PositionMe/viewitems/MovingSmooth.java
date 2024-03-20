package com.openpositioning.PositionMe.viewitems;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A moving average calculator to smooth out the data.
 */
public class MovingSmooth {
    private Queue<Double> window = new LinkedList<>();
    private int period;
    private double sum;

    public MovingSmooth(int period) {
        assert period > 0 : "Period must be a positive integer!";
        this.period = period;
    }

    public void add(double num) {
        sum += num;
        window.add(num);
        if (window.size() > period) {
            sum -= window.remove();
        }
    }

    public double getAverage() {
        if (window.isEmpty()) return 0; // technically the average is undefined
        return sum / window.size();
    }


    public void updateN(int newN) {
        assert newN > 0 : "Period must be a positive integer!";
        this.period = newN;
        this.window = new LinkedList<>();
        this.sum = 0;
    }
}

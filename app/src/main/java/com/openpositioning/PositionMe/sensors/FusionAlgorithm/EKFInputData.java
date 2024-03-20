package com.openpositioning.PositionMe.sensors.FusionAlgorithm;
public class EKFInputData {
    public enum Source {
        API, GNSS, PDR
    }

    public Source source;
    public double[] LatLong;
    public long timestamp;

    public EKFInputData(Source source, double[] LatLong, long timestamp) {
        this.source = source;
        this.LatLong = LatLong;
        this.timestamp = timestamp;
    }
}
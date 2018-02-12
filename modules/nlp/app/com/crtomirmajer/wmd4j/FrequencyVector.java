package com.crtomirmajer.wmd4j;

import fasttext.Vector;

/**
 * Created by Majer on 21.9.2016.
 */
public class FrequencyVector {
    
    private volatile long     frequency;
    private          Vector vector;
    
    public FrequencyVector(Vector vector) {
        this(1, vector);
    }
    
    public FrequencyVector(long frequency, Vector vector) {
        this.frequency = frequency;
        this.vector = vector;
    }
    
    public void incrementFrequency() {
        frequency += 1;
    }
    
    public long getFrequency() {
        return frequency;
    }
    
    public Vector getVector() {
        return vector;
    }
}

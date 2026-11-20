package com.kivo.waitlist.model;

import java.util.Collections;
import java.util.List;

/**
 * Thread-safe read-only snapshot of current course offering state.
 */
public class OfferingSnapshot {
    private final String offeringId;
    private final String title;
    private final int capacity;
    private final int enrolledCount;
    private final int availableSeats;
    private final List<String> enrolledLearners;
    private final List<String> waitlist;

    public OfferingSnapshot(String offeringId, String title, int capacity, int enrolledCount, int availableSeats, List<String> enrolledLearners, List<String> waitlist) {
        this.offeringId = offeringId;
        this.title = title;
        this.capacity = capacity;
        this.enrolledCount = enrolledCount;
        this.availableSeats = availableSeats;
        this.enrolledLearners = Collections.unmodifiableList(enrolledLearners);
        this.waitlist = Collections.unmodifiableList(waitlist);
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getTitle() {
        return title;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public List<String> getEnrolledLearners() {
        return enrolledLearners;
    }

    public List<String> getWaitlist() {
        return waitlist;
    }
}

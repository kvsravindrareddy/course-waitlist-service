package com.kivo.waitlist.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing a bounded single course offering.
 */
public class CourseOffering {
    private final String id;
    private final String title;
    private final int capacity;
    private final Set<String> prerequisites;

    public CourseOffering(String id, String title, int capacity, Set<String> prerequisites) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Course offering id cannot be null or empty.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive (> 0), received: " + capacity);
        }
        this.id = id.trim();
        this.title = title != null ? title.trim() : this.id;
        this.capacity = capacity;
        this.prerequisites = prerequisites != null
                ? Collections.unmodifiableSet(new HashSet<>(prerequisites))
                : Collections.emptySet();
    }

    public CourseOffering(String id, String title, int capacity) {
        this(id, title, capacity, Collections.emptySet());
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getCapacity() {
        return capacity;
    }

    public Set<String> getPrerequisites() {
        return prerequisites;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseOffering that = (CourseOffering) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

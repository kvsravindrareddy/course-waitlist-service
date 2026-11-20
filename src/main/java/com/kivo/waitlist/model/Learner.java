package com.kivo.waitlist.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing a student/learner.
 */
public class Learner {
    private final String id;
    private final String name;
    private final Set<String> completedCourses;

    public Learner(String id, String name, Set<String> completedCourses) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Learner ID cannot be null or empty.");
        }
        this.id = id.trim();
        this.name = name != null ? name.trim() : this.id;
        this.completedCourses = completedCourses != null
                ? Collections.unmodifiableSet(new HashSet<>(completedCourses))
                : Collections.emptySet();
    }

    public Learner(String id, String name) {
        this(id, name, Collections.emptySet());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCompletedCourses() {
        return completedCourses;
    }

    /**
     * Checks if this learner satisfies all required course prerequisites.
     */
    public boolean hasPrerequisites(Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        return completedCourses.containsAll(required);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Learner learner = (Learner) o;
        return Objects.equals(id, learner.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Learner{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", completedCourses=" + completedCourses +
                '}';
    }
}

package com.kivo.waitlist.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public class RegisterLearnerRequest {
    @NotBlank(message = "ID cannot be blank")
    private String id;

    @NotBlank(message = "Name cannot be blank")
    private String name;

    private Set<String> completedCourses;

    public RegisterLearnerRequest() {}

    public RegisterLearnerRequest(String id, String name, Set<String> completedCourses) {
        this.id = id;
        this.name = name;
        this.completedCourses = completedCourses;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getCompletedCourses() {
        return completedCourses;
    }

    public void setCompletedCourses(Set<String> completedCourses) {
        this.completedCourses = completedCourses;
    }
}

package com.kivo.waitlist.dto;

import jakarta.validation.constraints.NotBlank;

public class DropRequest {
    @NotBlank(message = "Learner ID cannot be blank")
    private String learnerId;

    public DropRequest() {}

    public DropRequest(String learnerId) {
        this.learnerId = learnerId;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public void setLearnerId(String learnerId) {
        this.learnerId = learnerId;
    }
}

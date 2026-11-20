package com.kivo.waitlist.dto;

import jakarta.validation.constraints.NotBlank;

public class EnrollRequest {
    @NotBlank(message = "Learner ID cannot be blank")
    private String learnerId;

    public EnrollRequest() {}

    public EnrollRequest(String learnerId) {
        this.learnerId = learnerId;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public void setLearnerId(String learnerId) {
        this.learnerId = learnerId;
    }
}

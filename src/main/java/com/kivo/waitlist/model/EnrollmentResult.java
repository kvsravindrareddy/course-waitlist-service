package com.kivo.waitlist.model;

/**
 * Result object returned from an enrollment attempt.
 */
public class EnrollmentResult {
    private final EnrollmentStatus status;
    private final String message;
    private final Integer waitlistPosition;
    private final String offeringId;
    private final String learnerId;

    public EnrollmentResult(EnrollmentStatus status, String message, Integer waitlistPosition, String offeringId, String learnerId) {
        this.status = status;
        this.message = message;
        this.waitlistPosition = waitlistPosition;
        this.offeringId = offeringId;
        this.learnerId = learnerId;
    }

    public static EnrollmentResult enrolled(String offeringId, String learnerId, String courseTitle) {
        return new EnrollmentResult(
                EnrollmentStatus.ENROLLED,
                "Successfully enrolled in " + courseTitle + ".",
                null,
                offeringId,
                learnerId
        );
    }

    public static EnrollmentResult waitlisted(String offeringId, String learnerId, int capacity, int position) {
        return new EnrollmentResult(
                EnrollmentStatus.WAITLISTED,
                "Course is at capacity (" + capacity + "/" + capacity + "). Placed on waitlist at position #" + position + ".",
                position,
                offeringId,
                learnerId
        );
    }

    public static EnrollmentResult alreadyEnrolled(String offeringId, String learnerId, String courseTitle) {
        return new EnrollmentResult(
                EnrollmentStatus.ALREADY_ENROLLED,
                "Learner '" + learnerId + "' is already enrolled in " + courseTitle + ".",
                null,
                offeringId,
                learnerId
        );
    }

    public static EnrollmentResult alreadyWaitlisted(String offeringId, String learnerId, int position) {
        return new EnrollmentResult(
                EnrollmentStatus.ALREADY_WAITLISTED,
                "Learner '" + learnerId + "' is already on the waitlist at position #" + position + ".",
                position,
                offeringId,
                learnerId
        );
    }

    public static EnrollmentResult prerequisiteNotMet(String offeringId, String learnerId, String missingCourses, boolean isWaitlist) {
        String action = isWaitlist ? "Cannot join waitlist" : "Cannot enroll";
        return new EnrollmentResult(
                EnrollmentStatus.PREREQUISITE_NOT_MET,
                action + ": missing prerequisite course(s): " + missingCourses + ".",
                null,
                offeringId,
                learnerId
        );
    }

    public static EnrollmentResult invalidRequest(String offeringId, String learnerId, String message) {
        return new EnrollmentResult(
                EnrollmentStatus.INVALID_REQUEST,
                message,
                null,
                offeringId,
                learnerId
        );
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getLearnerId() {
        return learnerId;
    }
}

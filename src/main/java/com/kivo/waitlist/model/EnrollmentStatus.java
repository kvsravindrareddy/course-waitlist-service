package com.kivo.waitlist.model;

/**
 * Result status codes for course enrollment attempts.
 */
public enum EnrollmentStatus {
    ENROLLED,
    WAITLISTED,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    PREREQUISITE_NOT_MET,
    INVALID_REQUEST
}

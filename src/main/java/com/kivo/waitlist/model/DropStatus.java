package com.kivo.waitlist.model;

/**
 * Result status codes for course drop attempts.
 */
public enum DropStatus {
    DROPPED,
    DROPPED_AND_PROMOTED,
    DROPPED_FROM_WAITLIST,
    NOT_ENROLLED,
    INVALID_REQUEST
}

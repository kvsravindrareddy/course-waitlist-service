package com.kivo.waitlist.model;

/**
 * Result object returned from a drop operation.
 */
public class DropResult {
    private final DropStatus status;
    private final String message;
    private final String droppedLearnerId;
    private final String promotedLearnerId;
    private final boolean seatOpened;

    public DropResult(DropStatus status, String message, String droppedLearnerId, String promotedLearnerId, boolean seatOpened) {
        this.status = status;
        this.message = message;
        this.droppedLearnerId = droppedLearnerId;
        this.promotedLearnerId = promotedLearnerId;
        this.seatOpened = seatOpened;
    }

    public static DropResult droppedAndPromoted(String droppedLearnerId, String promotedLearnerId) {
        return new DropResult(
                DropStatus.DROPPED_AND_PROMOTED,
                "Learner '" + droppedLearnerId + "' dropped. Seat filled by waitlisted learner '" + promotedLearnerId + "'.",
                droppedLearnerId,
                promotedLearnerId,
                false
        );
    }

    public static DropResult droppedSeatOpened(String droppedLearnerId) {
        return new DropResult(
                DropStatus.DROPPED,
                "Learner '" + droppedLearnerId + "' dropped. 1 seat opened (no eligible waitlisted learners available).",
                droppedLearnerId,
                null,
                true
        );
    }

    public static DropResult droppedFromWaitlist(String droppedLearnerId) {
        return new DropResult(
                DropStatus.DROPPED_FROM_WAITLIST,
                "Learner '" + droppedLearnerId + "' was removed from the waitlist.",
                droppedLearnerId,
                null,
                false
        );
    }

    public static DropResult notEnrolled(String droppedLearnerId, String courseTitle) {
        return new DropResult(
                DropStatus.NOT_ENROLLED,
                "Learner '" + droppedLearnerId + "' is neither enrolled nor waitlisted in " + courseTitle + ".",
                droppedLearnerId,
                null,
                false
        );
    }

    public static DropResult invalidRequest(String message) {
        return new DropResult(
                DropStatus.INVALID_REQUEST,
                message,
                null,
                null,
                false
        );
    }

    public DropStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getDroppedLearnerId() {
        return droppedLearnerId;
    }

    public String getPromotedLearnerId() {
        return promotedLearnerId;
    }

    public boolean isSeatOpened() {
        return seatOpened;
    }
}

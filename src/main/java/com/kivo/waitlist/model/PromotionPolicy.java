package com.kivo.waitlist.model;

/**
 * Waitlist promotion eligibility policy.
 */
public enum PromotionPolicy {
    /**
     * Ineligible waitlisted learners are skipped for immediate promotion but retain their queue position.
     */
    SKIP_AND_RETAIN,

    /**
     * Ineligible waitlisted learners are disqualified and pruned from the waitlist.
     */
    SKIP_AND_EJECT
}

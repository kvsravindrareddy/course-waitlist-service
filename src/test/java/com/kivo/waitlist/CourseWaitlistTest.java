package com.kivo.waitlist;

import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Course Waitlist Behavior Tests")
class CourseWaitlistTest {

    private CourseOffering offering;
    private CourseWaitlistService service;

    @BeforeEach
    void setUp() {
        offering = new CourseOffering("BIO101", "General Biology", 2, Set.of("CHEM100"));
        service = new CourseWaitlistService(offering);

        for (int i = 1; i <= 5; i++) {
            service.registerLearner(new Learner("S" + i, "Student " + i, Set.of("CHEM100")));
        }
        service.registerLearner(new Learner("INELIGIBLE", "No Chem", Set.of()));
    }

    @Test
    @DisplayName("When full, subsequent eligible learners join waitlist with incremental FIFO positions")
    void testWaitlistActivationUponReachingCapacity() {
        EnrollmentResult r1 = service.enroll("S1");
        EnrollmentResult r2 = service.enroll("S2");
        assertEquals(EnrollmentStatus.ENROLLED, r1.getStatus());
        assertEquals(EnrollmentStatus.ENROLLED, r2.getStatus());

        EnrollmentResult r3 = service.enroll("S3");
        assertEquals(EnrollmentStatus.WAITLISTED, r3.getStatus());
        assertEquals(1, r3.getWaitlistPosition());

        EnrollmentResult r4 = service.enroll("S4");
        assertEquals(EnrollmentStatus.WAITLISTED, r4.getStatus());
        assertEquals(2, r4.getWaitlistPosition());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(2, snap.getEnrolledCount());
        assertEquals(0, snap.getAvailableSeats());
        assertEquals(List.of("S3", "S4"), snap.getWaitlist());
    }

    @Test
    @DisplayName("Duplicate enrollment requests from a waitlisted learner return ALREADY_WAITLISTED without shifting position")
    void testDuplicateWaitlistAttemptIsRejectedIdempotently() {
        service.enroll("S1");
        service.enroll("S2");
        EnrollmentResult r3 = service.enroll("S3");
        assertEquals(1, r3.getWaitlistPosition());

        service.enroll("S4"); // S4 at position #2

        // S3 attempts to enroll again
        EnrollmentResult dupR3 = service.enroll("S3");
        assertEquals(EnrollmentStatus.ALREADY_WAITLISTED, dupR3.getStatus());
        assertEquals(1, dupR3.getWaitlistPosition());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(List.of("S3", "S4"), snap.getWaitlist());
    }

    @Test
    @DisplayName("An enrolled learner cannot join the waitlist")
    void testEnrolledLearnerCannotJoinWaitlist() {
        service.enroll("S1");
        service.enroll("S2");

        EnrollmentResult dupEnrolled = service.enroll("S1");
        assertEquals(EnrollmentStatus.ALREADY_ENROLLED, dupEnrolled.getStatus());

        OfferingSnapshot snap = service.getSnapshot();
        assertFalse(snap.getWaitlist().contains("S1"));
        assertTrue(snap.getEnrolledLearners().contains("S1"));
    }

    @Test
    @DisplayName("Learner lacking prerequisites is rejected from waitlist when course is full")
    void testIneligibleLearnerCannotJoinWaitlist() {
        service.enroll("S1");
        service.enroll("S2");

        EnrollmentResult res = service.enroll("INELIGIBLE");
        assertEquals(EnrollmentStatus.PREREQUISITE_NOT_MET, res.getStatus());
        assertTrue(res.getMessage().contains("CHEM100"));

        OfferingSnapshot snap = service.getSnapshot();
        assertFalse(snap.getWaitlist().contains("INELIGIBLE"));
    }

    @Test
    @DisplayName("A learner can drop from the waitlist directly, freeing their waitlist slot without opening an enrolled seat")
    void testDroppingDirectlyFromWaitlist() {
        service.enroll("S1");
        service.enroll("S2");
        service.enroll("S3");
        service.enroll("S4");

        DropResult dropRes = service.drop("S3");
        assertEquals(DropStatus.DROPPED_FROM_WAITLIST, dropRes.getStatus());
        assertEquals("S3", dropRes.getDroppedLearnerId());
        assertFalse(dropRes.isSeatOpened());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(2, snap.getEnrolledCount());
        assertEquals(List.of("S4"), snap.getWaitlist());
    }
}

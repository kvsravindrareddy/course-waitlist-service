package com.kivo.waitlist;

import com.kivo.waitlist.model.CourseOffering;
import com.kivo.waitlist.model.DropResult;
import com.kivo.waitlist.model.DropStatus;
import com.kivo.waitlist.model.Learner;
import com.kivo.waitlist.model.OfferingSnapshot;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Course Drop & Idempotency Tests")
class CourseDropsTest {

    private CourseOffering offering;
    private CourseWaitlistService service;

    @BeforeEach
    void setUp() {
        offering = new CourseOffering("HIST101", "World History", 2, Set.of());
        service = new CourseWaitlistService(offering);

        for (int i = 1; i <= 5; i++) {
            service.registerLearner(new Learner("H" + i, "History Student " + i));
        }
    }

    @Test
    @DisplayName("Dropping a learner who was never enrolled returns NOT_ENROLLED with no side effects")
    void testDropNonEnrolledLearnerReturnsNotEnrolled() {
        DropResult res = service.drop("H1");
        assertEquals(DropStatus.NOT_ENROLLED, res.getStatus());
        assertEquals("H1", res.getDroppedLearnerId());
        assertFalse(res.isSeatOpened());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(0, snap.getEnrolledCount());
        assertEquals(2, snap.getAvailableSeats());
    }

    @Test
    @DisplayName("Dropping an already dropped learner returns NOT_ENROLLED without corrupting capacity or waitlist")
    void testRepeatedDropOfEnrolledLearner() {
        service.enroll("H1");
        service.enroll("H2");

        // First drop succeeds
        DropResult drop1 = service.drop("H1");
        assertEquals(DropStatus.DROPPED, drop1.getStatus());
        assertTrue(drop1.isSeatOpened());
        assertEquals(1, service.getSnapshot().getAvailableSeats());

        // Second drop of the exact same learner
        DropResult drop2 = service.drop("H1");
        assertEquals(DropStatus.NOT_ENROLLED, drop2.getStatus());
        assertFalse(drop2.isSeatOpened());

        // Third drop of the exact same learner
        DropResult drop3 = service.drop("H1");
        assertEquals(DropStatus.NOT_ENROLLED, drop3.getStatus());

        // Capacity and seats must remain strictly 1
        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(1, snap.getEnrolledCount());
        assertEquals(1, snap.getAvailableSeats());
        assertEquals(List.of("H2"), snap.getEnrolledLearners());
    }

    @Test
    @DisplayName("Invalid or whitespace learner IDs on drop return INVALID_REQUEST")
    void testInvalidLearnerIdOnDrop() {
        String[] invalidIds = new String[]{null, "", "   "};
        for (String bad : invalidIds) {
            DropResult res = service.drop(bad);
            assertEquals(DropStatus.INVALID_REQUEST, res.getStatus());
        }
    }

    @Test
    @DisplayName("Dropping an intermediate waitlist learner removes them while preserving relative FIFO order of others")
    void testDropFromWaitlistPreservesRemainingWaitlistOrder() {
        service.enroll("H1");
        service.enroll("H2");
        service.enroll("H3"); // waitlist #1
        service.enroll("H4"); // waitlist #2
        service.enroll("H5"); // waitlist #3

        assertEquals(List.of("H3", "H4", "H5"), service.getSnapshot().getWaitlist());

        // H4 drops from waitlist
        DropResult res = service.drop("H4");
        assertEquals(DropStatus.DROPPED_FROM_WAITLIST, res.getStatus());
        assertEquals("H4", res.getDroppedLearnerId());

        // Remaining waitlist must preserve strict FIFO relative order
        assertEquals(List.of("H3", "H5"), service.getSnapshot().getWaitlist());
    }
}

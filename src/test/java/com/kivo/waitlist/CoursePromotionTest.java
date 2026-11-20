package com.kivo.waitlist;

import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Waitlist Promotion & Skip-and-Retain Policy Tests")
class CoursePromotionTest {

    private CourseOffering offering;
    private CourseWaitlistService service;

    @BeforeEach
    void setUp() {
        offering = new CourseOffering("ENG205", "Advanced Composition", 2, Set.of("ENG101"));
        service = new CourseWaitlistService(offering);

        for (int i = 1; i <= 5; i++) {
            service.registerLearner(new Learner("L" + i, "Learner " + i, Set.of("ENG101")));
        }
    }

    @Test
    @DisplayName("When an enrolled learner drops, the earliest eligible waitlisted learner is promoted")
    void testFifoPromotionUponDrop() {
        service.enroll("L1");
        service.enroll("L2");

        EnrollmentResult r3 = service.enroll("L3");
        EnrollmentResult r4 = service.enroll("L4");
        assertEquals(1, r3.getWaitlistPosition());
        assertEquals(2, r4.getWaitlistPosition());

        // L1 drops -> L3 promoted
        DropResult dropRes = service.drop("L1");
        assertEquals(DropStatus.DROPPED_AND_PROMOTED, dropRes.getStatus());
        assertEquals("L1", dropRes.getDroppedLearnerId());
        assertEquals("L3", dropRes.getPromotedLearnerId());
        assertFalse(dropRes.isSeatOpened());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(2, snap.getEnrolledCount());
        assertEquals(0, snap.getAvailableSeats());
        assertEquals(List.of("L2", "L3"), snap.getEnrolledLearners());
        assertEquals(List.of("L4"), snap.getWaitlist());
    }

    @Test
    @DisplayName("An ineligible waitlisted learner is skipped (not promoted) but retained in queue, and the next eligible learner is promoted")
    void testSkipAndRetainPolicyForIneligibleWaitlistedLearner() throws Exception {
        service.enroll("L1");
        service.enroll("L2");

        // Learner lacking prerequisite
        service.registerLearner(new Learner("INELIGIBLE_1", "No English 101", Set.of()));

        // L3 waitlists (eligible)
        service.enroll("L3");

        // Simulate an ineligible learner ahead in queue (e.g. prerequisite revoked or grade incomplete)
        Field waitlistField = CourseWaitlistService.class.getDeclaredField("waitlist");
        waitlistField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> rawWaitlist = (List<String>) waitlistField.get(service);
        rawWaitlist.add(0, "INELIGIBLE_1"); // Inject at head of queue ahead of L3

        assertEquals(List.of("INELIGIBLE_1", "L3"), service.getSnapshot().getWaitlist());

        // L1 drops
        DropResult dropRes = service.drop("L1");

        // INELIGIBLE_1 cannot be promoted; L3 must be promoted instead
        assertEquals(DropStatus.DROPPED_AND_PROMOTED, dropRes.getStatus());
        assertEquals("L1", dropRes.getDroppedLearnerId());
        assertEquals("L3", dropRes.getPromotedLearnerId());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(List.of("L2", "L3"), snap.getEnrolledLearners());
        // INELIGIBLE_1 is skipped and retained at head of waitlist!
        assertEquals(List.of("INELIGIBLE_1"), snap.getWaitlist());
    }

    @Test
    @DisplayName("When an enrolled learner drops and waitlist is empty, capacity opens and no promotion occurs")
    void testDroppingWhenWaitlistIsEmptyOpensSeat() {
        service.enroll("L1");
        service.enroll("L2");

        assertEquals(0, service.getSnapshot().getAvailableSeats());

        DropResult dropRes = service.drop("L1");
        assertEquals(DropStatus.DROPPED, dropRes.getStatus());
        assertEquals("L1", dropRes.getDroppedLearnerId());
        assertNull(dropRes.getPromotedLearnerId());
        assertTrue(dropRes.isSeatOpened());

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(1, snap.getEnrolledCount());
        assertEquals(1, snap.getAvailableSeats());
        assertEquals(List.of("L2"), snap.getEnrolledLearners());
    }

    @Test
    @DisplayName("Sequential drops promote sequential waitlisted learners until waitlist becomes empty")
    void testSequentialDropsAndPromotionsUntilWaitlistExhaustion() {
        service.enroll("L1");
        service.enroll("L2");
        service.enroll("L3"); // waitlist #1
        service.enroll("L4"); // waitlist #2

        // Drop 1
        DropResult d1 = service.drop("L1");
        assertEquals("L3", d1.getPromotedLearnerId());
        assertEquals(List.of("L4"), service.getSnapshot().getWaitlist());

        // Drop 2
        DropResult d2 = service.drop("L2");
        assertEquals("L4", d2.getPromotedLearnerId());
        assertEquals(List.of(), service.getSnapshot().getWaitlist());

        // Drop 3
        DropResult d3 = service.drop("L3");
        assertEquals(DropStatus.DROPPED, d3.getStatus());
        assertNull(d3.getPromotedLearnerId());
        assertTrue(d3.isSeatOpened());
        assertEquals(1, service.getSnapshot().getAvailableSeats());
    }
}

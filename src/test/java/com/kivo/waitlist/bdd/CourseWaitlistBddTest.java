package com.kivo.waitlist.bdd;

import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BDD Feature: Course Enrollment, Drop, and FIFO Waitlist Promotion")
class CourseWaitlistBddTest {

    private CourseWaitlistService service;
    private EnrollmentResult lastEnrollmentResult;
    private DropResult lastDropResult;

    @BeforeEach
    void setupFeatureContext() {
        CourseOffering offering = new CourseOffering("CS201", "Data Structures & Algorithms", 2, Set.of("CS101"));
        service = new CourseWaitlistService(offering);

        // Register default persona pool
        service.registerLearner(new Learner("alice", "Alice Chen", Set.of("CS101")));
        service.registerLearner(new Learner("bob", "Bob Smith", Set.of("CS101")));
        service.registerLearner(new Learner("carol", "Carol Danvers", Collections.emptySet())); // Ineligible
        service.registerLearner(new Learner("david", "David Miller", Set.of("CS101")));
        service.registerLearner(new Learner("eve", "Eve Johnson", Set.of("CS101")));
        service.registerLearner(new Learner("frank", "Frank Wright", Collections.emptySet())); // Ineligible
    }

    // ── BDD Steps Helpers ───────────────────────────────────────────────────

    private void givenLearnerIsEnrolled(String learnerId) {
        EnrollmentResult res = service.enroll(learnerId);
        assertEquals(EnrollmentStatus.ENROLLED, res.getStatus(), "Setup precondition: learner must be enrolled");
    }

    private void givenLearnerIsWaitlisted(String learnerId, int expectedPos) {
        EnrollmentResult res = service.enroll(learnerId);
        assertEquals(EnrollmentStatus.WAITLISTED, res.getStatus(), "Setup precondition: learner must be waitlisted");
        assertEquals(expectedPos, res.getWaitlistPosition());
    }

    private void whenLearnerEnrolls(String learnerId) {
        lastEnrollmentResult = service.enroll(learnerId);
    }

    private void whenLearnerDrops(String learnerId) {
        lastDropResult = service.drop(learnerId);
    }

    private void thenEnrollmentStatusShouldBe(EnrollmentStatus expected) {
        assertNotNull(lastEnrollmentResult, "Enrollment result should not be null");
        assertEquals(expected, lastEnrollmentResult.getStatus());
    }

    private void thenDropStatusShouldBe(DropStatus expected) {
        assertNotNull(lastDropResult, "Drop result should not be null");
        assertEquals(expected, lastDropResult.getStatus());
    }

    private void thenEnrolledCountShouldBe(int expected) {
        assertEquals(expected, service.getSnapshot().getEnrolledCount());
    }

    private void thenAvailableSeatsShouldBe(int expected) {
        assertEquals(expected, service.getSnapshot().getAvailableSeats());
    }

    // ── Scenarios ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Scenario: Eligible learner enrolls successfully when capacity is available")
    class ScenarioEligibleEnrollment {
        @Test
        void execute() {
            // Given course has capacity and alice has CS101
            whenLearnerEnrolls("alice");

            thenEnrollmentStatusShouldBe(EnrollmentStatus.ENROLLED);
            thenEnrolledCountShouldBe(1);
            thenAvailableSeatsShouldBe(1);
            assertTrue(service.getSnapshot().getEnrolledLearners().contains("alice"));
        }
    }

    @Nested
    @DisplayName("Scenario: Ineligible learner is rejected when prerequisite is missing")
    class ScenarioIneligibleEnrollment {
        @Test
        void execute() {
            // When carol enrolls (missing CS101)
            whenLearnerEnrolls("carol");

            thenEnrollmentStatusShouldBe(EnrollmentStatus.PREREQUISITE_NOT_MET);
            thenEnrolledCountShouldBe(0);
            thenAvailableSeatsShouldBe(2);
            assertFalse(service.getSnapshot().getEnrolledLearners().contains("carol"));
            assertFalse(service.getSnapshot().getWaitlist().contains("carol"));
        }
    }

    @Nested
    @DisplayName("Scenario: Course reaches full capacity and subsequent eligible learner joins waitlist")
    class ScenarioWaitlistPlacement {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");

            whenLearnerEnrolls("david");

            thenEnrollmentStatusShouldBe(EnrollmentStatus.WAITLISTED);
            assertEquals(1, lastEnrollmentResult.getWaitlistPosition());
            thenEnrolledCountShouldBe(2);
            thenAvailableSeatsShouldBe(0);
            assertEquals(List.of("david"), service.getSnapshot().getWaitlist());
        }
    }

    @Nested
    @DisplayName("Scenario: Duplicate enrollment requests are rejected idempotently")
    class ScenarioDuplicateRejections {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");
            givenLearnerIsWaitlisted("david", 1);

            // Alice attempts duplicate enrollment
            whenLearnerEnrolls("alice");
            thenEnrollmentStatusShouldBe(EnrollmentStatus.ALREADY_ENROLLED);

            // David attempts duplicate waitlist enrollment
            whenLearnerEnrolls("david");
            thenEnrollmentStatusShouldBe(EnrollmentStatus.ALREADY_WAITLISTED);
            assertEquals(1, lastEnrollmentResult.getWaitlistPosition());

            thenEnrolledCountShouldBe(2);
            assertEquals(List.of("david"), service.getSnapshot().getWaitlist());
        }
    }

    @Nested
    @DisplayName("Scenario: Dropping an enrolled learner promotes the earliest waitlisted learner in FIFO order")
    class ScenarioFifoPromotionUponDrop {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");
            givenLearnerIsWaitlisted("david", 1);
            givenLearnerIsWaitlisted("eve", 2);

            whenLearnerDrops("alice");

            thenDropStatusShouldBe(DropStatus.DROPPED_AND_PROMOTED);
            assertEquals("alice", lastDropResult.getDroppedLearnerId());
            assertEquals("david", lastDropResult.getPromotedLearnerId());

            assertEquals(List.of("bob", "david"), service.getSnapshot().getEnrolledLearners());
            assertEquals(List.of("eve"), service.getSnapshot().getWaitlist());
            thenAvailableSeatsShouldBe(0);
        }
    }

    @Nested
    @DisplayName("Scenario: Ineligible waitlisted learner is skipped for promotion but retained in queue (Skip-and-Retain)")
    class ScenarioSkipAndRetainPolicy {
        @Test
        void execute() throws Exception {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");

            // Setup: frank (ineligible) is at position 1, david (eligible) is at position 2
            givenLearnerIsWaitlisted("david", 1);
            Field waitlistField = CourseWaitlistService.class.getDeclaredField("waitlist");
            waitlistField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> rawWaitlist = (List<String>) waitlistField.get(service);
            rawWaitlist.add(0, "frank"); // Inject frank at head of waitlist

            assertEquals(List.of("frank", "david"), service.getSnapshot().getWaitlist());

            whenLearnerDrops("alice");

            thenDropStatusShouldBe(DropStatus.DROPPED_AND_PROMOTED);
            assertEquals("david", lastDropResult.getPromotedLearnerId()); // David promoted!
            assertEquals(List.of("frank"), service.getSnapshot().getWaitlist()); // Frank retained at position 1!
            assertEquals(List.of("bob", "david"), service.getSnapshot().getEnrolledLearners());
        }
    }

    @Nested
    @DisplayName("Scenario: Dropping an enrolled learner when waitlist is empty opens an available seat")
    class ScenarioDropWithEmptyWaitlist {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");

            whenLearnerDrops("alice");

            thenDropStatusShouldBe(DropStatus.DROPPED);
            assertTrue(lastDropResult.isSeatOpened());
            thenEnrolledCountShouldBe(1);
            thenAvailableSeatsShouldBe(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Repeated drop of an already dropped learner does not leak capacity")
    class ScenarioRepeatedDropNoLeak {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");

            whenLearnerDrops("alice");
            thenDropStatusShouldBe(DropStatus.DROPPED);
            thenAvailableSeatsShouldBe(2);

            whenLearnerDrops("alice");
            thenDropStatusShouldBe(DropStatus.NOT_ENROLLED);
            thenAvailableSeatsShouldBe(2); // Invariant maintained: Available seats strictly 2
        }
    }

    @Nested
    @DisplayName("Scenario: Dropping directly from the waitlist removes learner without opening an enrolled seat")
    class ScenarioDropFromWaitlist {
        @Test
        void execute() {
            givenLearnerIsEnrolled("alice");
            givenLearnerIsEnrolled("bob");
            givenLearnerIsWaitlisted("david", 1);

            whenLearnerDrops("david");

            thenDropStatusShouldBe(DropStatus.DROPPED_FROM_WAITLIST);
            assertFalse(lastDropResult.isSeatOpened());
            thenEnrolledCountShouldBe(2);
            assertEquals(List.of(), service.getSnapshot().getWaitlist());
        }
    }
}

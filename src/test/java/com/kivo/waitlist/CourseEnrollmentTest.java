package com.kivo.waitlist;

import com.kivo.waitlist.model.CourseOffering;
import com.kivo.waitlist.model.EnrollmentResult;
import com.kivo.waitlist.model.EnrollmentStatus;
import com.kivo.waitlist.model.Learner;
import com.kivo.waitlist.model.OfferingSnapshot;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Course Enrollment & Prerequisite Tests")
class CourseEnrollmentTest {

    private CourseOffering offering;
    private CourseWaitlistService service;

    @BeforeEach
    void setUp() {
        offering = new CourseOffering("MATH200", "Calculus II", 2, Set.of("MATH100"));
        service = new CourseWaitlistService(offering);

        service.registerLearner(new Learner("L1", "Alice", Set.of("MATH100")));
        service.registerLearner(new Learner("L2", "Bob", Set.of())); // Missing MATH100
        service.registerLearner(new Learner("L3", "Charlie", Set.of("MATH100", "PHYS100")));
    }

    @Test
    @DisplayName("Enrollment succeeds when capacity remains and learner meets prerequisites")
    void testSuccessfulEnrollmentWithPrerequisite() {
        EnrollmentResult res = service.enroll("L1");
        assertEquals(EnrollmentStatus.ENROLLED, res.getStatus());
        assertTrue(res.getMessage().contains("Successfully enrolled"));

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(1, snap.getEnrolledCount());
        assertEquals(1, snap.getAvailableSeats());
        assertTrue(snap.getEnrolledLearners().contains("L1"));
    }

    @Test
    @DisplayName("Enrollment fails immediately with PREREQUISITE_NOT_MET when requirements are not satisfied")
    void testEnrollmentRejectedWhenPrerequisiteMissing() {
        EnrollmentResult res = service.enroll("L2");
        assertEquals(EnrollmentStatus.PREREQUISITE_NOT_MET, res.getStatus());
        assertTrue(res.getMessage().contains("missing prerequisite"));

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(0, snap.getEnrolledCount());
        assertEquals(2, snap.getAvailableSeats());
        assertFalse(snap.getEnrolledLearners().contains("L2"));
        assertFalse(snap.getWaitlist().contains("L2"));
    }

    @Test
    @DisplayName("Course offering with multiple prerequisites requires all to be fulfilled")
    void testEnrollmentWithMultiplePrerequisites() {
        CourseOffering advancedOffering = new CourseOffering(
                "CS301",
                "Advanced Algorithms",
                3,
                Set.of("CS101", "MATH100")
        );
        CourseWaitlistService advService = new CourseWaitlistService(advancedOffering);

        // Learner with partial prerequisites
        advService.registerLearner(new Learner("P1", "Partial", Set.of("CS101")));
        EnrollmentResult resPartial = advService.enroll("P1");
        assertEquals(EnrollmentStatus.PREREQUISITE_NOT_MET, resPartial.getStatus());

        // Learner with complete prerequisites
        advService.registerLearner(new Learner("C1", "Complete", Set.of("CS101", "MATH100", "PHYS100")));
        EnrollmentResult resComplete = advService.enroll("C1");
        assertEquals(EnrollmentStatus.ENROLLED, resComplete.getStatus());
    }

    @Test
    @DisplayName("Enrolling an already enrolled learner returns ALREADY_ENROLLED without modifying state")
    void testDuplicateEnrollmentIsRejectedIdempotently() {
        EnrollmentResult res1 = service.enroll("L1");
        assertEquals(EnrollmentStatus.ENROLLED, res1.getStatus());

        EnrollmentResult res2 = service.enroll("L1");
        assertEquals(EnrollmentStatus.ALREADY_ENROLLED, res2.getStatus());
        assertTrue(res2.getMessage().contains("already enrolled"));

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(1, snap.getEnrolledCount());
        assertEquals(1, snap.getEnrolledLearners().size());
    }

    @Test
    @DisplayName("Invalid or empty learner IDs return INVALID_REQUEST without corrupting state")
    void testInvalidLearnerIdHandling() {
        String[] invalidIds = new String[]{"", "   ", null};
        for (String badId : invalidIds) {
            EnrollmentResult res = service.enroll(badId);
            assertEquals(EnrollmentStatus.INVALID_REQUEST, res.getStatus());
        }

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(0, snap.getEnrolledCount());
        assertEquals(2, snap.getAvailableSeats());
    }

    @Test
    @DisplayName("Enrollment attempt by an unknown learner returns INVALID_REQUEST")
    void testUnregisteredLearnerHandling() {
        EnrollmentResult res = service.enroll("ghost_student_999");
        assertEquals(EnrollmentStatus.INVALID_REQUEST, res.getStatus());
        assertTrue(res.getMessage().contains("not registered"));
    }

    @Test
    @DisplayName("CourseOffering initialization rejects zero or negative capacity")
    void testOfferingCapacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new CourseOffering("ERR1", "Invalid", 0));
        assertThrows(IllegalArgumentException.class, () -> new CourseOffering("ERR2", "Invalid", -5));
    }
}

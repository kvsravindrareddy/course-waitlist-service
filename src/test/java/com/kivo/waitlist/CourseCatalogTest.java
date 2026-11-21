package com.kivo.waitlist;

import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Multi-Course Catalog & Offering Isolation Tests")
class CourseCatalogTest {

    private CourseWaitlistService service;

    @BeforeEach
    void setUp() {
        service = new CourseWaitlistService();
    }

    @Test
    @DisplayName("Switching between course offerings strictly preserves individual enrollment and waitlist rosters")
    void testSwitchOfferingPreservesEnrollmentsAndIsolatedRosters() {
        // Active course starts as CS201 (requires CS101)
        assertEquals("CS201", service.getActiveOfferingId());

        // Enroll alice & bob into CS201
        EnrollmentResult r1 = service.enroll("alice");
        EnrollmentResult r2 = service.enroll("bob");
        assertEquals(EnrollmentStatus.ENROLLED, r1.getStatus());
        assertEquals(EnrollmentStatus.ENROLLED, r2.getStatus());

        // Waitlist david into CS201
        EnrollmentResult r3 = service.enroll("david");
        assertEquals(EnrollmentStatus.WAITLISTED, r3.getStatus());
        assertEquals(1, r3.getWaitlistPosition());

        OfferingSnapshot cs201Snap = service.getSnapshot("CS201");
        assertEquals(2, cs201Snap.getEnrolledCount());
        assertEquals(List.of("alice", "bob"), cs201Snap.getEnrolledLearners());
        assertEquals(List.of("david"), cs201Snap.getWaitlist());

        // Switch to CS301 (requires CS201 & CS101, capacity 3)
        service.switchOffering("CS301");
        assertEquals("CS301", service.getActiveOfferingId());

        OfferingSnapshot cs301SnapInitial = service.getSnapshot("CS301");
        assertEquals(0, cs301SnapInitial.getEnrolledCount());
        assertEquals(0, cs301SnapInitial.getWaitlist().size());

        // Eve satisfies CS101 + CS201 -> enrolls into CS301
        EnrollmentResult rEve = service.enroll("eve");
        assertEquals(EnrollmentStatus.ENROLLED, rEve.getStatus());

        OfferingSnapshot cs301SnapAfter = service.getSnapshot("CS301");
        assertEquals(1, cs301SnapAfter.getEnrolledCount());
        assertEquals(List.of("eve"), cs301SnapAfter.getEnrolledLearners());

        // Switch back to CS201 -> rosters must be completely intact and uncorrupted
        service.switchOffering("CS201");
        OfferingSnapshot cs201Restored = service.getSnapshot("CS201");
        assertEquals(2, cs201Restored.getEnrolledCount());
        assertEquals(List.of("alice", "bob"), cs201Restored.getEnrolledLearners());
        assertEquals(List.of("david"), cs201Restored.getWaitlist());

        // Switch back to CS301 -> CS301 roster intact
        service.switchOffering("CS301");
        OfferingSnapshot cs301Restored = service.getSnapshot("CS301");
        assertEquals(1, cs301Restored.getEnrolledCount());
        assertEquals(List.of("eve"), cs301Restored.getEnrolledLearners());
    }

    @Test
    @DisplayName("Direct enrollment and drop specifying offeringId isolates course states")
    void testDirectEnrollmentAndDropWithOfferingId() {
        // Enroll into WEB101 directly without manual switch (WEB101 has 0 prereqs, cap 3)
        EnrollmentResult resWeb = service.enroll("WEB101", "carol");
        assertEquals(EnrollmentStatus.ENROLLED, resWeb.getStatus());
        assertEquals("WEB101", resWeb.getOfferingId());

        // Enroll into CS201 directly
        EnrollmentResult resCs = service.enroll("CS201", "alice");
        assertEquals(EnrollmentStatus.ENROLLED, resCs.getStatus());
        assertEquals("CS201", resCs.getOfferingId());

        // Drop from WEB101 directly
        DropResult dropWeb = service.drop("WEB101", "carol");
        assertEquals(DropStatus.DROPPED, dropWeb.getStatus());
        assertTrue(dropWeb.isSeatOpened());

        OfferingSnapshot snapWeb = service.getSnapshot("WEB101");
        assertEquals(0, snapWeb.getEnrolledCount());

        OfferingSnapshot snapCs = service.getSnapshot("CS201");
        assertEquals(1, snapCs.getEnrolledCount());
        assertEquals(List.of("alice"), snapCs.getEnrolledLearners());
    }

    @Test
    @DisplayName("Enroll and drop against non-existent offering IDs return INVALID_REQUEST")
    void testInvalidOfferingIdHandling() {
        EnrollmentResult enrollRes = service.enroll("NON_EXISTENT_999", "alice");
        assertEquals(EnrollmentStatus.INVALID_REQUEST, enrollRes.getStatus());
        assertTrue(enrollRes.getMessage().contains("not found in catalog"));

        DropResult dropRes = service.drop("NON_EXISTENT_999", "alice");
        assertEquals(DropStatus.INVALID_REQUEST, dropRes.getStatus());
        assertTrue(dropRes.getMessage().contains("not found in catalog"));
    }

    @Test
    @DisplayName("Registering and creating custom offerings allows dynamic enrollment")
    void testRegisterCustomOffering() {
        CourseOffering cloudCourse = new CourseOffering("CLOUD401", "Cloud Native Architectures", 1, Set.of("CS201"));
        service.registerOffering(cloudCourse);

        assertNotNull(service.getOffering("CLOUD401"));

        // Alice has CS201
        EnrollmentResult rAlice = service.enroll("CLOUD401", "alice");
        assertEquals(EnrollmentStatus.ENROLLED, rAlice.getStatus());

        // Eve also has CS201 -> capacity full -> joins waitlist
        EnrollmentResult rEve = service.enroll("CLOUD401", "eve");
        assertEquals(EnrollmentStatus.WAITLISTED, rEve.getStatus());
        assertEquals(1, rEve.getWaitlistPosition());

        // Drop Alice -> Eve promoted
        DropResult dropRes = service.drop("CLOUD401", "alice");
        assertEquals(DropStatus.DROPPED_AND_PROMOTED, dropRes.getStatus());
        assertEquals("eve", dropRes.getPromotedLearnerId());

        OfferingSnapshot snap = service.getSnapshot("CLOUD401");
        assertEquals(1, snap.getEnrolledCount());
        assertEquals(List.of("eve"), snap.getEnrolledLearners());
        assertEquals(0, snap.getWaitlist().size());
    }
}

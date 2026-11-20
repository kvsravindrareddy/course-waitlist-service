package com.kivo.waitlist;

import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Concurrency & Thread-Safety Tests")
class CourseConcurrencyTest {

    @Test
    @DisplayName("30 concurrent threads competing for 5 seats results in exactly 5 enrolled and 25 waitlisted without race conditions")
    void testConcurrentEnrollmentContention() throws Exception {
        int capacity = 5;
        int totalLearners = 30;

        CourseOffering offering = new CourseOffering(
                "CONC101",
                "Concurrent Systems",
                capacity,
                Set.of("PREREQ_CONC")
        );
        CourseWaitlistService service = new CourseWaitlistService(offering);

        for (int i = 0; i < totalLearners; i++) {
            String lid = String.format("T_%02d", i);
            service.registerLearner(new Learner(lid, "Thread Student " + i, Set.of("PREREQ_CONC")));
        }

        CyclicBarrier barrier = new CyclicBarrier(totalLearners);
        ExecutorService executor = Executors.newFixedThreadPool(totalLearners);
        List<Future<EnrollmentResult>> futures = new ArrayList<>();

        for (int i = 0; i < totalLearners; i++) {
            String lid = String.format("T_%02d", i);
            futures.add(executor.submit(() -> {
                barrier.await(); // Synchronize all 30 threads to fire at the exact same instant
                return service.enroll(lid);
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        AtomicInteger enrolledCount = new AtomicInteger(0);
        AtomicInteger waitlistedCount = new AtomicInteger(0);

        for (Future<EnrollmentResult> f : futures) {
            EnrollmentResult res = f.get();
            if (res.getStatus() == EnrollmentStatus.ENROLLED) {
                enrolledCount.incrementAndGet();
            } else if (res.getStatus() == EnrollmentStatus.WAITLISTED) {
                waitlistedCount.incrementAndGet();
            }
        }

        assertEquals(capacity, enrolledCount.get(), "Exactly capacity (5) enrollments must succeed");
        assertEquals(totalLearners - capacity, waitlistedCount.get(), "Remaining (25) must be waitlisted");

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(capacity, snap.getEnrolledCount());
        assertEquals(0, snap.getAvailableSeats());
        assertEquals(capacity, snap.getEnrolledLearners().size());
        assertEquals(totalLearners - capacity, snap.getWaitlist().size());

        // Invariant: Sets are strictly disjoint
        Set<String> enrolledSet = new HashSet<>(snap.getEnrolledLearners());
        Set<String> waitlistSet = new HashSet<>(snap.getWaitlist());
        enrolledSet.retainAll(waitlistSet);
        assertTrue(enrolledSet.isEmpty(), "No learner can be both enrolled and waitlisted");
    }

    @Test
    @DisplayName("Concurrent drops from multiple enrolled learners safely promote waitlisted learners without over-allocation")
    void testConcurrentDropsAndPromotions() throws Exception {
        int capacity = 5;
        int waitlistSize = 15;

        CourseOffering offering = new CourseOffering("CONC202", "Distributed Systems", capacity, Set.of());
        CourseWaitlistService service = new CourseWaitlistService(offering);

        for (int i = 0; i < capacity + waitlistSize; i++) {
            String lid = String.format("D_%02d", i);
            service.registerLearner(new Learner(lid, "Student " + i));
            service.enroll(lid);
        }

        OfferingSnapshot initialSnap = service.getSnapshot();
        assertEquals(capacity, initialSnap.getEnrolledCount());
        assertEquals(waitlistSize, initialSnap.getWaitlist().size());

        List<String> enrolledToDrop = new ArrayList<>(initialSnap.getEnrolledLearners());
        CyclicBarrier barrier = new CyclicBarrier(enrolledToDrop.size());
        ExecutorService executor = Executors.newFixedThreadPool(enrolledToDrop.size());
        List<Future<DropResult>> futures = new ArrayList<>();

        for (String lid : enrolledToDrop) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return service.drop(lid);
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        List<String> promotedIds = new ArrayList<>();
        for (Future<DropResult> f : futures) {
            DropResult res = f.get();
            assertEquals(DropStatus.DROPPED_AND_PROMOTED, res.getStatus());
            promotedIds.add(res.getPromotedLearnerId());
        }

        // All promoted learners must be distinct
        assertEquals(promotedIds.size(), new HashSet<>(promotedIds).size(), "Promotions must not duplicate");

        OfferingSnapshot finalSnap = service.getSnapshot();
        assertEquals(capacity, finalSnap.getEnrolledCount(), "Enrolled count must remain full at 5");
        assertEquals(0, finalSnap.getAvailableSeats());
        assertEquals(waitlistSize - capacity, finalSnap.getWaitlist().size(), "Waitlist must be reduced by 5");
    }
}

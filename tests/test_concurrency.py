"""Concurrency test suite verifying thread-safety and ACID-like invariants under high contention."""

import threading
import unittest
from typing import List

from src.models import CourseOffering, DropStatus, EnrollmentStatus, Learner
from src.service import CourseWaitlistService


class TestCourseConcurrency(unittest.TestCase):
    def test_concurrent_enrollment_contention(self):
        """30 concurrent threads competing for 5 seats must result in exactly 5 enrolled and 25 waitlisted without race conditions."""
        capacity = 5
        total_learners = 30

        offering = CourseOffering(
            id="CONC101",
            title="Concurrent Systems",
            capacity=capacity,
            prerequisites={"PREREQ_CONC"},
        )
        service = CourseWaitlistService(offering)

        # Register 30 eligible learners
        for i in range(total_learners):
            service.register_learner(
                Learner(id=f"T_{i:02d}", name=f"Thread Learner {i}", completed_courses={"PREREQ_CONC"})
            )

        start_gate = threading.Barrier(total_learners)
        results = [None] * total_learners

        def worker(index: int):
            learner_id = f"T_{index:02d}"
            # Synchronize all threads so they strike the enroll method at the exact same instant
            start_gate.wait()
            res = service.enroll(learner_id)
            results[index] = res

        threads: List[threading.Thread] = []
        for i in range(total_learners):
            t = threading.Thread(target=worker, args=(i,))
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        # Analyze results
        enrolled_results = [r for r in results if r.status == EnrollmentStatus.ENROLLED]
        waitlisted_results = [r for r in results if r.status == EnrollmentStatus.WAITLISTED]

        self.assertEqual(len(enrolled_results), capacity, "Exactly 'capacity' enrollments must succeed")
        self.assertEqual(len(waitlisted_results), total_learners - capacity, "Remaining learners must be waitlisted")

        snap = service.get_snapshot()
        self.assertEqual(snap.enrolled_count, capacity)
        self.assertEqual(snap.available_seats, 0)
        self.assertEqual(len(snap.enrolled_learners), capacity)
        self.assertEqual(len(snap.waitlist), total_learners - capacity)

        # Invariant: Disjoint sets
        enrolled_set = set(snap.enrolled_learners)
        waitlist_set = set(snap.waitlist)
        self.assertEqual(len(enrolled_set.intersection(waitlist_set)), 0, "No learner can be both enrolled and waitlisted")
        self.assertEqual(len(enrolled_set) + len(waitlist_set), total_learners)

    def test_concurrent_drops_and_promotions(self):
        """Concurrent drops from multiple enrolled learners must safely promote waitlisted learners without exceeding capacity."""
        capacity = 5
        waitlist_size = 15

        offering = CourseOffering(
            id="CONC202",
            title="Distributed Systems",
            capacity=capacity,
            prerequisites=set(),
        )
        service = CourseWaitlistService(offering)

        # Pre-enroll 5 learners and waitlist 15 learners
        for i in range(capacity + waitlist_size):
            lid = f"D_{i:02d}"
            service.register_learner(Learner(id=lid, name=f"Student {i}"))
            service.enroll(lid)

        snap_initial = service.get_snapshot()
        self.assertEqual(snap_initial.enrolled_count, capacity)
        self.assertEqual(len(snap_initial.waitlist), waitlist_size)

        enrolled_to_drop = list(snap_initial.enrolled_learners)  # 5 enrolled students
        start_gate = threading.Barrier(len(enrolled_to_drop))
        drop_results = [None] * len(enrolled_to_drop)

        def drop_worker(index: int, learner_id: str):
            start_gate.wait()
            res = service.drop(learner_id)
            drop_results[index] = res

        threads: List[threading.Thread] = []
        for i, lid in enumerate(enrolled_to_drop):
            t = threading.Thread(target=drop_worker, args=(i, lid))
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        # All 5 drops must result in DROPPED_AND_PROMOTED because waitlist had 15 learners
        promoted_ids = []
        for r in drop_results:
            self.assertEqual(r.status, DropStatus.DROPPED_AND_PROMOTED)
            promoted_ids.append(r.promoted_learner_id)

        # All promoted IDs must be unique
        self.assertEqual(len(promoted_ids), len(set(promoted_ids)))

        snap_final = service.get_snapshot()
        self.assertEqual(snap_final.enrolled_count, capacity, "Enrolled count must remain at full capacity after promotions")
        self.assertEqual(snap_final.available_seats, 0)
        self.assertEqual(len(snap_final.waitlist), waitlist_size - capacity, "Waitlist must be reduced by 5")


if __name__ == "__main__":
    unittest.main()

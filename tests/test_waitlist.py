"""Unit tests covering waitlist behavior, capacity limits, and duplicate waitlist handling."""

import unittest

from src.models import CourseOffering, DropStatus, EnrollmentStatus, Learner
from src.service import CourseWaitlistService


class TestCourseWaitlist(unittest.TestCase):
    def setUp(self):
        self.offering = CourseOffering(
            id="BIO101",
            title="General Biology",
            capacity=2,
            prerequisites={"CHEM100"},
        )
        self.service = CourseWaitlistService(self.offering)

        # Register learners
        self.learners = [
            Learner(id=f"S{i}", name=f"Student {i}", completed_courses={"CHEM100"})
            for i in range(1, 6)
        ]
        for l in self.learners:
            self.service.register_learner(l)

        self.ineligible = Learner(id="INELIGIBLE", name="No Chem", completed_courses=set())
        self.service.register_learner(self.ineligible)

    def test_waitlist_activation_upon_reaching_capacity(self):
        """When full, subsequent eligible learners are placed onto waitlist with incremental positions."""
        # Fill seats (capacity 2)
        r1 = self.service.enroll("S1")
        r2 = self.service.enroll("S2")
        self.assertEqual(r1.status, EnrollmentStatus.ENROLLED)
        self.assertEqual(r2.status, EnrollmentStatus.ENROLLED)

        # Third learner joins waitlist #1
        r3 = self.service.enroll("S3")
        self.assertEqual(r3.status, EnrollmentStatus.WAITLISTED)
        self.assertEqual(r3.waitlist_position, 1)

        # Fourth learner joins waitlist #2
        r4 = self.service.enroll("S4")
        self.assertEqual(r4.status, EnrollmentStatus.WAITLISTED)
        self.assertEqual(r4.waitlist_position, 2)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 2)
        self.assertEqual(snap.available_seats, 0)
        self.assertEqual(snap.waitlist, ["S3", "S4"])

    def test_duplicate_waitlist_attempt_is_rejected_idempotently(self):
        """Duplicate enrollment requests from a waitlisted learner return ALREADY_WAITLISTED without shifting position."""
        self.service.enroll("S1")
        self.service.enroll("S2")
        r3 = self.service.enroll("S3")
        self.assertEqual(r3.waitlist_position, 1)

        self.service.enroll("S4")  # S4 at position #2

        # S3 attempts to enroll again
        dup_r3 = self.service.enroll("S3")
        self.assertEqual(dup_r3.status, EnrollmentStatus.ALREADY_WAITLISTED)
        self.assertEqual(dup_r3.waitlist_position, 1)

        # Verify waitlist was not mutated or duplicated
        snap = self.service.get_snapshot()
        self.assertEqual(snap.waitlist, ["S3", "S4"])

    def test_enrolled_learner_cannot_join_waitlist(self):
        """A learner who is already enrolled cannot join the waitlist."""
        self.service.enroll("S1")
        self.service.enroll("S2")

        # S1 attempts to enroll again when course is full
        dup_enrolled = self.service.enroll("S1")
        self.assertEqual(dup_enrolled.status, EnrollmentStatus.ALREADY_ENROLLED)

        snap = self.service.get_snapshot()
        self.assertNotIn("S1", snap.waitlist)
        self.assertIn("S1", snap.enrolled_learners)

    def test_ineligible_learner_cannot_join_waitlist(self):
        """Learner lacking prerequisites is rejected from waitlist when course is full."""
        self.service.enroll("S1")
        self.service.enroll("S2")

        res = self.service.enroll("INELIGIBLE")
        self.assertEqual(res.status, EnrollmentStatus.PREREQUISITE_NOT_MET)
        self.assertIn("CHEM100", res.message)

        snap = self.service.get_snapshot()
        self.assertNotIn("INELIGIBLE", snap.waitlist)

    def test_dropping_directly_from_waitlist(self):
        """A learner can drop from the waitlist, which frees up their waitlist slot without opening an enrolled seat."""
        self.service.enroll("S1")
        self.service.enroll("S2")
        self.service.enroll("S3")
        self.service.enroll("S4")

        # S3 drops from waitlist
        drop_res = self.service.drop("S3")
        self.assertEqual(drop_res.status, DropStatus.DROPPED_FROM_WAITLIST)
        self.assertEqual(drop_res.dropped_learner_id, "S3")
        self.assertFalse(drop_res.seat_opened)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 2)
        self.assertEqual(snap.waitlist, ["S4"])


if __name__ == "__main__":
    unittest.main()

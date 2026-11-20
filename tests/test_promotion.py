"""Unit tests covering FIFO waitlist promotion and the Skip-and-Retain eligibility policy."""

import unittest

from src.models import CourseOffering, DropStatus, EnrollmentStatus, Learner
from src.service import CourseWaitlistService


class TestWaitlistPromotion(unittest.TestCase):
    def setUp(self):
        self.offering = CourseOffering(
            id="ENG205",
            title="Advanced Composition",
            capacity=2,
            prerequisites={"ENG101"},
        )
        self.service = CourseWaitlistService(self.offering)

        # Register learners
        self.learners = [
            Learner(id=f"L{i}", name=f"Learner {i}", completed_courses={"ENG101"})
            for i in range(1, 6)
        ]
        for l in self.learners:
            self.service.register_learner(l)

    def test_fifo_promotion_upon_drop(self):
        """When an enrolled learner drops, the earliest eligible waitlisted learner is promoted."""
        # Fill capacity (L1, L2)
        self.service.enroll("L1")
        self.service.enroll("L2")

        # Waitlist (L3 first, then L4)
        r3 = self.service.enroll("L3")
        r4 = self.service.enroll("L4")
        self.assertEqual(r3.waitlist_position, 1)
        self.assertEqual(r4.waitlist_position, 2)

        # L1 drops -> L3 should be promoted
        drop_res = self.service.drop("L1")
        self.assertEqual(drop_res.status, DropStatus.DROPPED_AND_PROMOTED)
        self.assertEqual(drop_res.dropped_learner_id, "L1")
        self.assertEqual(drop_res.promoted_learner_id, "L3")
        self.assertFalse(drop_res.seat_opened)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 2)
        self.assertEqual(snap.available_seats, 0)
        self.assertEqual(snap.enrolled_learners, ["L2", "L3"])
        self.assertEqual(snap.waitlist, ["L4"])

    def test_skip_and_retain_policy_for_ineligible_waitlisted_learner(self):
        """An ineligible waitlisted learner is skipped (not promoted) but retained in queue, and the next eligible learner is promoted."""
        self.service.enroll("L1")
        self.service.enroll("L2")

        # Create learner with missing prerequisite
        ineligible_learner = Learner(id="INELIGIBLE_1", name="No English 101", completed_courses=set())
        self.service.register_learner(ineligible_learner)

        # Waitlist L3 (eligible)
        self.service.enroll("L3")

        # Manually simulate ineligible learner ahead in queue (e.g. prerequisite revoked while waiting)
        self.service._waitlist.insert(0, "INELIGIBLE_1")
        # Current waitlist: ["INELIGIBLE_1", "L3"]
        self.assertEqual(self.service.get_snapshot().waitlist, ["INELIGIBLE_1", "L3"])

        # L1 drops
        drop_res = self.service.drop("L1")

        # INELIGIBLE_1 cannot be promoted; L3 must be promoted instead
        self.assertEqual(drop_res.status, DropStatus.DROPPED_AND_PROMOTED)
        self.assertEqual(drop_res.dropped_learner_id, "L1")
        self.assertEqual(drop_res.promoted_learner_id, "L3")

        snap = self.service.get_snapshot()
        # Enrolled: L2 and L3
        self.assertEqual(snap.enrolled_learners, ["L2", "L3"])
        # INELIGIBLE_1 is retained in the waitlist at head!
        self.assertEqual(snap.waitlist, ["INELIGIBLE_1"])
        self.assertNotIn("INELIGIBLE_1", snap.enrolled_learners)

    def test_dropping_when_waitlist_is_empty_opens_seat(self):
        """When an enrolled learner drops and waitlist is empty, capacity opens and no promotion occurs."""
        self.service.enroll("L1")
        self.service.enroll("L2")

        snap1 = self.service.get_snapshot()
        self.assertEqual(snap1.available_seats, 0)

        # L1 drops with empty waitlist
        drop_res = self.service.drop("L1")
        self.assertEqual(drop_res.status, DropStatus.DROPPED)
        self.assertEqual(drop_res.dropped_learner_id, "L1")
        self.assertIsNone(drop_res.promoted_learner_id)
        self.assertTrue(drop_res.seat_opened)

        snap2 = self.service.get_snapshot()
        self.assertEqual(snap2.enrolled_count, 1)
        self.assertEqual(snap2.available_seats, 1)
        self.assertEqual(snap2.enrolled_learners, ["L2"])

    def test_sequential_drops_and_promotions_until_waitlist_exhaustion(self):
        """Sequential drops promote sequential waitlisted learners until waitlist becomes empty."""
        self.service.enroll("L1")
        self.service.enroll("L2")
        self.service.enroll("L3")  # waitlist #1
        self.service.enroll("L4")  # waitlist #2

        # First drop: L1 drops -> L3 promoted
        d1 = self.service.drop("L1")
        self.assertEqual(d1.promoted_learner_id, "L3")
        self.assertEqual(self.service.get_snapshot().waitlist, ["L4"])

        # Second drop: L2 drops -> L4 promoted
        d2 = self.service.drop("L2")
        self.assertEqual(d2.promoted_learner_id, "L4")
        self.assertEqual(self.service.get_snapshot().waitlist, [])

        # Third drop: L3 drops -> No waitlist, seat opens
        d3 = self.service.drop("L3")
        self.assertEqual(d3.status, DropStatus.DROPPED)
        self.assertIsNone(d3.promoted_learner_id)
        self.assertTrue(d3.seat_opened)
        self.assertEqual(self.service.get_snapshot().available_seats, 1)


if __name__ == "__main__":
    unittest.main()

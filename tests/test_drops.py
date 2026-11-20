"""Unit tests covering drop operations, edge cases, invalid inputs, and duplicate drop attempts."""

import unittest

from src.models import CourseOffering, DropStatus, Learner
from src.service import CourseWaitlistService


class TestCourseDrops(unittest.TestCase):
    def setUp(self):
        self.offering = CourseOffering(
            id="HIST101",
            title="World History",
            capacity=2,
            prerequisites=set(),  # No prerequisites
        )
        self.service = CourseWaitlistService(self.offering)

        # Register learners
        for i in range(1, 6):
            self.service.register_learner(Learner(id=f"H{i}", name=f"History Student {i}"))

    def test_drop_non_enrolled_learner_returns_not_enrolled(self):
        """Dropping a learner who was never enrolled returns NOT_ENROLLED with no side effects."""
        res = self.service.drop("H1")
        self.assertEqual(res.status, DropStatus.NOT_ENROLLED)
        self.assertEqual(res.dropped_learner_id, "H1")
        self.assertFalse(res.seat_opened)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 0)
        self.assertEqual(snap.available_seats, 2)

    def test_repeated_drop_of_enrolled_learner(self):
        """Dropping an already dropped learner returns NOT_ENROLLED without corrupting capacity or waitlist."""
        self.service.enroll("H1")
        self.service.enroll("H2")

        # First drop succeeds
        drop1 = self.service.drop("H1")
        self.assertEqual(drop1.status, DropStatus.DROPPED)
        self.assertTrue(drop1.seat_opened)
        self.assertEqual(self.service.get_snapshot().available_seats, 1)

        # Second drop of the exact same learner
        drop2 = self.service.drop("H1")
        self.assertEqual(drop2.status, DropStatus.NOT_ENROLLED)
        self.assertFalse(drop2.seat_opened)

        # Third drop of the exact same learner
        drop3 = self.service.drop("H1")
        self.assertEqual(drop3.status, DropStatus.NOT_ENROLLED)

        # Verify state: available seats must strictly be 1, NOT incremented phantomly
        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 1)
        self.assertEqual(snap.available_seats, 1)
        self.assertEqual(snap.enrolled_learners, ["H2"])

    def test_invalid_learner_id_on_drop(self):
        """Invalid or whitespace learner IDs on drop return INVALID_REQUEST."""
        for invalid in [None, "", "   "]:
            res = self.service.drop(invalid)
            self.assertEqual(res.status, DropStatus.INVALID_REQUEST)

    def test_drop_from_waitlist_preserves_remaining_waitlist_order(self):
        """Dropping an intermediate waitlist learner removes them while preserving relative FIFO order of others."""
        self.service.enroll("H1")
        self.service.enroll("H2")
        self.service.enroll("H3")  # waitlist #1
        self.service.enroll("H4")  # waitlist #2
        self.service.enroll("H5")  # waitlist #3

        self.assertEqual(self.service.get_snapshot().waitlist, ["H3", "H4", "H5"])

        # H4 (middle waitlist position) drops
        res = self.service.drop("H4")
        self.assertEqual(res.status, DropStatus.DROPPED_FROM_WAITLIST)
        self.assertEqual(res.dropped_learner_id, "H4")

        # Remaining waitlist must preserve strict FIFO relative order
        self.assertEqual(self.service.get_snapshot().waitlist, ["H3", "H5"])


if __name__ == "__main__":
    unittest.main()

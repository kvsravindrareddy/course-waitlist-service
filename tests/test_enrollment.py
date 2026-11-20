"""Unit tests covering course enrollment, prerequisite enforcement, and capacity checks."""

import unittest

from src.models import CourseOffering, EnrollmentStatus, Learner
from src.service import CourseWaitlistService


class TestCourseEnrollment(unittest.TestCase):
    def setUp(self):
        self.offering = CourseOffering(
            id="MATH200",
            title="Calculus II",
            capacity=2,
            prerequisites={"MATH100"},
        )
        self.service = CourseWaitlistService(self.offering)

        # Register test learners
        self.learner_eligible = Learner(id="L1", name="Alice", completed_courses={"MATH100"})
        self.learner_ineligible = Learner(id="L2", name="Bob", completed_courses=set())
        self.learner_multi_prereq = Learner(id="L3", name="Charlie", completed_courses={"MATH100", "PHYS100"})

        self.service.register_learner(self.learner_eligible)
        self.service.register_learner(self.learner_ineligible)
        self.service.register_learner(self.learner_multi_prereq)

    def test_successful_enrollment_with_prerequisite(self):
        """Enrollment succeeds when capacity remains and learner meets prerequisites."""
        res = self.service.enroll("L1")
        self.assertEqual(res.status, EnrollmentStatus.ENROLLED)
        self.assertIn("Successfully enrolled", res.message)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 1)
        self.assertEqual(snap.available_seats, 1)
        self.assertIn("L1", snap.enrolled_learners)

    def test_enrollment_rejected_when_prerequisite_missing(self):
        """Enrollment fails immediately with PREREQUISITE_NOT_MET when requirements are not satisfied."""
        res = self.service.enroll("L2")
        self.assertEqual(res.status, EnrollmentStatus.PREREQUISITE_NOT_MET)
        self.assertIn("missing prerequisite", res.message)

        # State must remain pristine
        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 0)
        self.assertEqual(snap.available_seats, 2)
        self.assertNotIn("L2", snap.enrolled_learners)
        self.assertNotIn("L2", snap.waitlist)

    def test_enrollment_with_multiple_prerequisites(self):
        """Course offering with multiple prerequisites requires all to be fulfilled."""
        advanced_offering = CourseOffering(
            id="CS301",
            title="Advanced Algorithms",
            capacity=3,
            prerequisites={"CS101", "MATH100"},
        )
        service = CourseWaitlistService(advanced_offering)

        # Learner with partial prerequisites
        partial = Learner(id="P1", name="Partial", completed_courses={"CS101"})  # Missing MATH100
        service.register_learner(partial)
        res_partial = service.enroll("P1")
        self.assertEqual(res_partial.status, EnrollmentStatus.PREREQUISITE_NOT_MET)

        # Learner with complete prerequisites
        complete = Learner(id="C1", name="Complete", completed_courses={"CS101", "MATH100", "PHYS100"})
        service.register_learner(complete)
        res_complete = service.enroll("C1")
        self.assertEqual(res_complete.status, EnrollmentStatus.ENROLLED)

    def test_duplicate_enrollment_is_rejected_idempotently(self):
        """Enrolling an already enrolled learner returns ALREADY_ENROLLED without modifying state."""
        res1 = self.service.enroll("L1")
        self.assertEqual(res1.status, EnrollmentStatus.ENROLLED)

        # Attempt duplicate enrollment
        res2 = self.service.enroll("L1")
        self.assertEqual(res2.status, EnrollmentStatus.ALREADY_ENROLLED)
        self.assertIn("already enrolled", res2.message)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 1)
        self.assertEqual(snap.enrolled_learners, ["L1"])

    def test_invalid_learner_id_handling(self):
        """Invalid or empty learner IDs return INVALID_REQUEST without corrupting state."""
        invalid_ids = ["", "   ", None]
        for bad_id in invalid_ids:
            res = self.service.enroll(bad_id)
            self.assertEqual(res.status, EnrollmentStatus.INVALID_REQUEST)

        snap = self.service.get_snapshot()
        self.assertEqual(snap.enrolled_count, 0)
        self.assertEqual(snap.available_seats, 2)

    def test_unregistered_learner_handling(self):
        """Enrollment attempt by an unknown learner returns INVALID_REQUEST."""
        res = self.service.enroll("ghost_student_999")
        self.assertEqual(res.status, EnrollmentStatus.INVALID_REQUEST)
        self.assertIn("not registered", res.message)

    def test_offering_capacity_must_be_positive(self):
        """CourseOffering initialization rejects zero or negative capacity."""
        with self.assertRaises(ValueError):
            CourseOffering(id="ERR1", title="Invalid", capacity=0)
        with self.assertRaises(ValueError):
            CourseOffering(id="ERR2", title="Invalid", capacity=-5)


if __name__ == "__main__":
    unittest.main()

"""Thread-safe Course Waitlist Service.

Implements enrollment, dropping, and FIFO waitlist promotion with strict
capacity invariants and prerequisite enforcement.
"""

import threading
from typing import Dict, List, Optional, Set

from src.models import (
    CourseOffering,
    DropResult,
    DropStatus,
    EnrollmentResult,
    EnrollmentStatus,
    Learner,
    OfferingSnapshot,
    PromotionPolicy,
)


class CourseWaitlistService:
    """Service slice managing enrollment, drops, and FIFO waitlist promotions for a single course offering."""

    def __init__(
        self,
        offering: CourseOffering,
        promotion_policy: PromotionPolicy = PromotionPolicy.SKIP_AND_RETAIN,
    ):
        self.offering = offering
        self.promotion_policy = promotion_policy

        # In-memory storage guarded by re-entrant lock
        self._lock = threading.RLock()
        self._learners: Dict[str, Learner] = {}
        self._enrolled: List[str] = []   # Order of direct enrollments / promotions
        self._waitlist: List[str] = []   # Strict FIFO waitlist queue

    # ── Learner Registry Management ───────────────────────────────────────────

    def register_learner(self, learner: Learner) -> None:
        """Register or update a learner's profile in the in-memory registry."""
        if not learner.id or not learner.id.strip():
            raise ValueError("Learner ID cannot be empty.")
        with self._lock:
            self._learners[learner.id] = learner

    def get_learner(self, learner_id: str) -> Optional[Learner]:
        """Retrieve a learner from the registry."""
        with self._lock:
            return self._learners.get(learner_id)

    # ── Enrollment Logic ──────────────────────────────────────────────────────

    def enroll(self, learner_id: str) -> EnrollmentResult:
        """Attempt to enroll a learner in the course offering.

        - If capacity is available and prerequisites are met: ENROLLED.
        - If capacity is full and prerequisites are met: WAITLISTED (FIFO).
        - If prerequisites are not met: PREREQUISITE_NOT_MET.
        - Duplicate requests are rejected idempotently without state corruption.
        """
        if not learner_id or not str(learner_id).strip():
            return EnrollmentResult(
                status=EnrollmentStatus.INVALID_REQUEST,
                message="Learner ID must be a non-empty string.",
                offering_id=self.offering.id,
            )

        clean_id = str(learner_id).strip()

        with self._lock:
            learner = self._learners.get(clean_id)
            if not learner:
                return EnrollmentResult(
                    status=EnrollmentStatus.INVALID_REQUEST,
                    message=f"Learner '{clean_id}' is not registered in the system.",
                    offering_id=self.offering.id,
                    learner_id=clean_id,
                )

            # Check duplicate enrollment
            if clean_id in self._enrolled:
                return EnrollmentResult(
                    status=EnrollmentStatus.ALREADY_ENROLLED,
                    message=f"Learner '{clean_id}' is already enrolled in {self.offering.title}.",
                    offering_id=self.offering.id,
                    learner_id=clean_id,
                )

            # Check duplicate waitlist
            if clean_id in self._waitlist:
                position = self._waitlist.index(clean_id) + 1
                return EnrollmentResult(
                    status=EnrollmentStatus.ALREADY_WAITLISTED,
                    message=f"Learner '{clean_id}' is already on the waitlist at position #{position}.",
                    waitlist_position=position,
                    offering_id=self.offering.id,
                    learner_id=clean_id,
                )

            # Prerequisite verification
            is_eligible = learner.has_prerequisites(self.offering.prerequisites)

            # Case 1: Available capacity
            if len(self._enrolled) < self.offering.capacity:
                if not is_eligible:
                    missing = sorted(list(self.offering.prerequisites - learner.completed_courses))
                    return EnrollmentResult(
                        status=EnrollmentStatus.PREREQUISITE_NOT_MET,
                        message=f"Cannot enroll: missing prerequisite course(s): {', '.join(missing)}.",
                        offering_id=self.offering.id,
                        learner_id=clean_id,
                    )

                self._enrolled.append(clean_id)
                return EnrollmentResult(
                    status=EnrollmentStatus.ENROLLED,
                    message=f"Successfully enrolled in {self.offering.title}.",
                    offering_id=self.offering.id,
                    learner_id=clean_id,
                )

            # Case 2: Full capacity -> Waitlist
            if not is_eligible:
                missing = sorted(list(self.offering.prerequisites - learner.completed_courses))
                return EnrollmentResult(
                    status=EnrollmentStatus.PREREQUISITE_NOT_MET,
                    message=f"Cannot join waitlist: missing prerequisite course(s): {', '.join(missing)}.",
                    offering_id=self.offering.id,
                    learner_id=clean_id,
                )

            self._waitlist.append(clean_id)
            position = len(self._waitlist)
            return EnrollmentResult(
                status=EnrollmentStatus.WAITLISTED,
                message=f"Course is at capacity ({self.offering.capacity}/{self.offering.capacity}). Placed on waitlist at position #{position}.",
                waitlist_position=position,
                offering_id=self.offering.id,
                learner_id=clean_id,
            )

    # ── Drop Logic & Waitlist Promotion ───────────────────────────────────────

    def drop(self, learner_id: str) -> DropResult:
        """Drop a learner from the course offering or waitlist.

        If an enrolled learner drops, a seat opens and the earliest eligible
        learner on the waitlist is automatically promoted according to the
        configured promotion policy (FIFO Skip-and-Retain).
        """
        if not learner_id or not str(learner_id).strip():
            return DropResult(
                status=DropStatus.INVALID_REQUEST,
                message="Learner ID must be a non-empty string.",
            )

        clean_id = str(learner_id).strip()

        with self._lock:
            # Case 1: Dropping from waitlist
            if clean_id in self._waitlist:
                self._waitlist.remove(clean_id)
                return DropResult(
                    status=DropStatus.DROPPED_FROM_WAITLIST,
                    message=f"Learner '{clean_id}' was removed from the waitlist.",
                    dropped_learner_id=clean_id,
                    seat_opened=False,
                )

            # Case 2: Learner neither enrolled nor waitlisted
            if clean_id not in self._enrolled:
                return DropResult(
                    status=DropStatus.NOT_ENROLLED,
                    message=f"Learner '{clean_id}' is neither enrolled nor waitlisted in {self.offering.title}.",
                    dropped_learner_id=clean_id,
                    seat_opened=False,
                )

            # Case 3: Dropping an enrolled learner -> Open seat
            self._enrolled.remove(clean_id)

            # FIFO Promotion: Find the earliest eligible waitlisted learner
            promoted_id = self._promote_next_eligible()

            if promoted_id:
                return DropResult(
                    status=DropStatus.DROPPED_AND_PROMOTED,
                    message=f"Learner '{clean_id}' dropped. Seat filled by waitlisted learner '{promoted_id}'.",
                    dropped_learner_id=clean_id,
                    promoted_learner_id=promoted_id,
                    seat_opened=False,
                )

            return DropResult(
                status=DropStatus.DROPPED,
                message=f"Learner '{clean_id}' dropped. 1 seat opened (no eligible waitlisted learners available).",
                dropped_learner_id=clean_id,
                seat_opened=True,
            )

    def _promote_next_eligible(self) -> Optional[str]:
        """Internal helper to promote the earliest eligible waitlisted learner.

        Assumes caller holds self._lock.
        Follows SKIP_AND_RETAIN policy:
        - Scans waitlist in FIFO order.
        - If learner satisfies prerequisites: remove from waitlist, enroll, return id.
        - If learner does NOT satisfy prerequisites: skip (retain in queue), continue scan.
        """
        for i, candidate_id in enumerate(self._waitlist):
            candidate = self._learners.get(candidate_id)
            if candidate and candidate.has_prerequisites(self.offering.prerequisites):
                # Found earliest eligible learner
                self._waitlist.pop(i)
                self._enrolled.append(candidate_id)
                return candidate_id

        return None

    # ── State Inspection ──────────────────────────────────────────────────────

    def get_snapshot(self) -> OfferingSnapshot:
        """Return a thread-safe snapshot of current enrollment and waitlist state."""
        with self._lock:
            enrolled_copy = list(self._enrolled)
            waitlist_copy = list(self._waitlist)
            return OfferingSnapshot(
                offering_id=self.offering.id,
                title=self.offering.title,
                capacity=self.offering.capacity,
                enrolled_count=len(enrolled_copy),
                available_seats=max(0, self.offering.capacity - len(enrolled_copy)),
                enrolled_learners=enrolled_copy,
                waitlist=waitlist_copy,
            )

"""Domain models and data transfer objects for Course Waitlist service."""

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional, Set


class EnrollmentStatus(str, Enum):
    """Result status codes for enrollment attempts."""
    ENROLLED = "ENROLLED"
    WAITLISTED = "WAITLISTED"
    ALREADY_ENROLLED = "ALREADY_ENROLLED"
    ALREADY_WAITLISTED = "ALREADY_WAITLISTED"
    PREREQUISITE_NOT_MET = "PREREQUISITE_NOT_MET"
    INVALID_REQUEST = "INVALID_REQUEST"


class DropStatus(str, Enum):
    """Result status codes for drop attempts."""
    DROPPED = "DROPPED"
    DROPPED_AND_PROMOTED = "DROPPED_AND_PROMOTED"
    DROPPED_FROM_WAITLIST = "DROPPED_FROM_WAITLIST"
    NOT_ENROLLED = "NOT_ENROLLED"
    INVALID_REQUEST = "INVALID_REQUEST"


class PromotionPolicy(str, Enum):
    """Waitlist promotion eligibility policy."""
    SKIP_AND_RETAIN = "SKIP_AND_RETAIN"  # Ineligible waitlisted learners are skipped for current promotion but retain queue rank.
    SKIP_AND_EJECT = "SKIP_AND_EJECT"    # Ineligible waitlisted learners are disqualified and removed.


@dataclass
class Learner:
    """Represents a student/learner in the system."""
    id: str
    name: str
    completed_courses: Set[str] = field(default_factory=set)

    def has_prerequisites(self, required_prereqs: Set[str]) -> bool:
        """Verify whether learner satisfies the given prerequisite course set."""
        return required_prereqs.issubset(self.completed_courses)


@dataclass
class CourseOffering:
    """Represents a bounded single course offering."""
    id: str
    title: str
    capacity: int
    prerequisites: Set[str] = field(default_factory=set)

    def __post_init__(self):
        if self.capacity <= 0:
            raise ValueError(f"Capacity must be positive (> 0), received: {self.capacity}")
        if not self.id or not self.id.strip():
            raise ValueError("Course offering id cannot be empty.")


@dataclass
class EnrollmentResult:
    """Result object returned by an enrollment operation."""
    status: EnrollmentStatus
    message: str
    waitlist_position: Optional[int] = None
    offering_id: Optional[str] = None
    learner_id: Optional[str] = None


@dataclass
class DropResult:
    """Result object returned by a drop operation."""
    status: DropStatus
    message: str
    dropped_learner_id: Optional[str] = None
    promoted_learner_id: Optional[str] = None
    seat_opened: bool = False


@dataclass
class OfferingSnapshot:
    """Thread-safe snapshot of offering status."""
    offering_id: str
    title: str
    capacity: int
    enrolled_count: int
    available_seats: int
    enrolled_learners: List[str]
    waitlist: List[str]

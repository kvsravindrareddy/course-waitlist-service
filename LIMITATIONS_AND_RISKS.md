# Limitations and Follow-Up Risks

This document highlights architectural limitations and production follow-up risks identified within the bounded scope of the Course Waitlist Service.

---

## 1. Process-Local In-Memory Persistence
- **Limitation**: The current implementation stores offering state, enrollments, and waitlists in JVM memory (`ArrayList`, `ConcurrentHashMap`). A process termination or container restart clears all state.
- **Risk**: Unacceptable for production registrar systems where enrollment state must survive crashes and deployments.
- **Follow-up Recommendation**: Map domain models to a persistent transactional store (e.g., PostgreSQL with Spring Data JPA). Enforce capacity invariants at the database layer using `SELECT ... FOR UPDATE` row-level locks or transactional check constraints (`CHECK (enrolled_count <= capacity)`).

---

## 2. Single-Instance Concurrency vs. Distributed Scaling
- **Limitation**: Concurrency control relies on Java's process-local `java.util.concurrent.locks.ReentrantLock(true)`.
- **Risk**: In a horizontally scaled cloud environment with multiple Spring Boot API replicas behind a load balancer, JVM-local locks cannot prevent cross-pod over-subscription (two instances could simultaneously enroll learners into the last remaining seat).
- **Follow-up Recommendation**: Use distributed locking (e.g., Redis Redlock / ShedLock with leases) or database row locks on the `course_offerings` record during transactional enrollment.

---

## 3. Waitlist Head Scan Performance under Persistent Ineligibility
- **Limitation**: Under the **Skip-and-Retain** policy, ineligible waitlisted learners remain in the queue. A drop requires an $O(W)$ linear scan across $W$ waitlisted learners to find the first eligible student.
- **Risk**: If many learners remain ineligible indefinitely, scanning costs grow and head-of-line clutter increases.
- **Follow-up Recommendation**:
  - Introduce an eligibility expiration window (e.g., learners must fulfill prerequisites within 7 days of waitlisting or be demoted).
  - Index the waitlist by prerequisite fulfillment status to achieve $O(1)$ lookup for the earliest eligible candidate.

---

## 4. Immediate Auto-Promotion vs. Seat Acceptance Timers
- **Limitation**: When a seat opens, the waitlisted learner is immediately promoted and enrolled.
- **Risk**: In higher education, auto-enrolling a student without consent may trigger unexpected tuition charges, financial aid adjustments, or schedule conflicts with other courses.
- **Follow-up Recommendation**: Implement a two-phase reservation model: when a seat opens, the earliest eligible learner is placed into `OFFERED_SEAT` state with a 24–48 hour expiration timer. If the learner does not accept within the window, the reservation expires and the seat is offered to the next waitlisted candidate.

---

## 5. Single Offering Boundary
- **Limitation**: The service manages a single offering in isolation.
- **Risk**: Real-world registrations enforce global constraints: credit hour caps (e.g., max 18 credits per semester), time-slot collision detection, and corequisite course requirements.
- **Follow-up Recommendation**: Wrap this offering slice in an orchestration service (`RegistrationCoordinator`) that validates student credit limits and cross-course conflicts prior to calling `CourseWaitlistService.enroll()`.

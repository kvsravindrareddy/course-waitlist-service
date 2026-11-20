# Course Waitlist Service — Design Specification

## 1. Executive Summary & Service Boundary
This document specifies the design of a bounded, reliable service slice for **course enrollment, dropping, and FIFO waitlist promotion** for a single course offering.

The service manages:
- Verification of learner prerequisites.
- Direct enrollment into a course offering up to a fixed maximum capacity.
- Insertion into a First-In-First-Out (FIFO) waitlist once capacity is reached.
- Automatic promotion of waitlisted learners when an enrolled learner drops.
- A consistent, documented eligibility policy handling waitlisted learners whose prerequisites are not met.
- Robust handling of duplicate, invalid, or concurrent operations without state corruption.

### Out of Scope (Explicit Boundaries)
- Multi-course catalogs, shopping carts, or multi-term scheduling.
- Billing, tuition payments, or identity/SSO integrations.
- Persistent database servers (RDBMS/NoSQL) — an in-memory, thread-safe transactional store is used.
- User interface (web/mobile) — interaction is provided via programmatic API and interactive CLI.

---

## 2. Domain Data Model & Invariants

```
+--------------------------------------------------------------+
|                        CourseOffering                        |
+--------------------------------------------------------------+
| - id: str                                                    |
| - title: str                                                 |
| - capacity: int (> 0)                                        |
| - prerequisites: Set[str]                                    |
| - enrolled_learners: List[str]  (ordered by enrollment time) |
| - waitlist: List[str]           (strict FIFO order)          |
+--------------------------------------------------------------+
                               |
                               | registers
                               v
+--------------------------------------------------------------+
|                           Learner                            |
+--------------------------------------------------------------+
| - id: str                                                    |
| - name: str                                                  |
| - completed_courses: Set[str]                                |
+--------------------------------------------------------------+
```

### Core Invariants
1. **Capacity Invariant**: `0 <= len(enrolled_learners) <= capacity` at all times.
2. **Mutual Exclusion Invariant**: An individual learner $L$ can exist in at most one state for a given offering:
   - $L \in \text{enrolled\_learners}$, OR
   - $L \in \text{waitlist}$, OR
   - $L \notin \text{offering}$.
   Under no circumstances can a learner be both enrolled and waitlisted simultaneously.
3. **Waitlist Uniqueness Invariant**: No duplicate entries are permitted on the waitlist. If $L \in \text{waitlist}$, subsequent enrollment requests by $L$ return `ALREADY_WAITLISTED` and preserve their original queue position.
4. **FIFO Order Invariant**: Learners on the waitlist are stored in the exact chronological order of their waitlist placement.
5. **Prerequisite Invariant**: A learner $L$ cannot be moved to `enrolled_learners` (either via initial enrollment or promotion) unless $\text{prerequisites} \subseteq L.\text{completed\_courses}$.

---

## 3. Prerequisite & Waitlist Eligibility Policy

### The Ineligible Waitlist Dilemma
When learners waitlist, weeks may elapse before a seat opens. During this time:
- A learner might not yet have completed the prerequisite when joining the waitlist, hoping to finish it before class starts.
- A prerequisite could be revoked or re-evaluated.
- When an enrolled learner drops and a seat opens, the learner at the head of the waitlist might be **ineligible**.

### Policy Decision: "Skip and Retain"
We adopt the **Skip-and-Retain Policy** (standard in academic registrar systems):
1. **At Drop / Promotion Time**:
   - The queue is inspected sequentially from head to tail (FIFO).
   - The earliest learner who **satisfies all prerequisites** is promoted to `enrolled_learners` and removed from `waitlist`.
   - Any learner ahead of them who is **ineligible is skipped**: they are **not promoted**, but they **retain their position** in the waitlist.
   - Rationale: Disqualifying and ejecting a student from the waitlist because their grade hasn't posted yet is overly punitive; retaining their position gives them an opportunity to qualify for later drops while ensuring the immediately opening seat is never wasted or given to an unqualified learner.
2. **At Initial Enrollment Time**:
   - When capacity is available, a learner who lacks prerequisites is **rejected immediately** with `PREREQUISITE_NOT_MET`. They cannot occupy a seat.
   - When capacity is full, a learner may join the waitlist only if eligible, or optionally register interest; our default baseline enforces that learners joining the waitlist must satisfy prerequisites at entry, while promotion re-verifies eligibility dynamically at promotion time to guard against state drift.

---

## 4. API & Operations Specification

### 4.1 `enroll(learner: Learner) -> EnrollmentResult`
- **Preconditions**: `learner.id` is non-empty; offering exists.
- **Algorithm**:
  1. Acquire re-entrant lock.
  2. If `learner.id` in `enrolled_learners` $\rightarrow$ Return `ALREADY_ENROLLED`.
  3. If `learner.id` in `waitlist` $\rightarrow$ Return `ALREADY_WAITLISTED`.
  4. If `len(enrolled_learners) < capacity`:
     - If $\text{prerequisites} \not\subseteq \text{learner.completed\_courses}$ $\rightarrow$ Return `PREREQUISITE_NOT_MET`.
     - Else: Add `learner.id` to `enrolled_learners` $\rightarrow$ Return `ENROLLED`.
  5. Else (`len(enrolled_learners) == capacity`):
     - If $\text{prerequisites} \not\subseteq \text{learner.completed\_courses}$ $\rightarrow$ Return `PREREQUISITE_NOT_MET`.
     - Else: Append `learner.id` to `waitlist` $\rightarrow$ Return `WAITLISTED(position=len(waitlist))`.

### 4.2 `drop(learner_id: str, learner_lookup: Callable) -> DropResult`
- **Preconditions**: `learner_id` is non-empty.
- **Algorithm**:
  1. Acquire re-entrant lock.
  2. If `learner_id` in `waitlist`:
     - Remove `learner_id` from `waitlist`.
     - Return `DROPPED_FROM_WAITLIST`.
  3. If `learner_id` not in `enrolled_learners`:
     - Return `NOT_ENROLLED` (idempotent, no side effects).
  4. Remove `learner_id` from `enrolled_learners`. (Seat opens).
  5. **FIFO Promotion Evaluation**:
     - Iterate through `waitlist` in FIFO index order:
       - Retrieve waitlisted learner via `learner_lookup(waitlist_id)`.
       - Check if waitlisted learner satisfies `prerequisites`.
       - If eligible:
         - Remove from `waitlist`.
         - Append to `enrolled_learners`.
         - Return `DROPPED_AND_PROMOTED(dropped=learner_id, promoted=waitlist_id)`.
       - If ineligible:
         - Skip this learner (leave in waitlist at current rank).
         - Continue to next waitlisted learner.
     - If no eligible candidate found on waitlist:
       - Available seats increment by 1.
       - Return `DROPPED_NO_PROMOTION(dropped=learner_id)`.

---

## 5. Concurrency & Synchronization Architecture
To guarantee ACID invariants in a multi-threaded service environment without a database:
- Each `CourseOffering` is guarded by an internal re-entrant lock (`threading.RLock`).
- Atomic read-modify-write blocks encapsulate all enrollment, drop, and promotion transactions.
- Fine-grained per-offering locking ensures that operations on different offerings do not bottleneck one another.
- Concurrency testing verifies that 20+ threads competing for limited capacity cannot breach `capacity` or produce inconsistent waitlist orders.

---

## 6. Tradeoffs & Design Decisions

| Decision | Alternative Considered | Selected Rationale |
| :--- | :--- | :--- |
| **Skip and Retain** | Drop ineligible learners from queue | Preserves learner queue tenure while ensuring active seats are filled only by qualified candidates. |
| **In-Memory Store with RLock** | SQLite / External RDBMS | Scope boundaries strictly forbid extra database setup unless required; standard library locking provides 100% thread safety and zero configuration. |
| **Dynamic Prerequisite Verification** | Static check at queue join only | Guarantees that if a learner's status changes while waiting, ineligible learners are never promoted into a seat. |
| **Idempotent Drop & Duplicate Protection** | Raise generic runtime exceptions | Returning explicit result enums (`NOT_ENROLLED`, `ALREADY_ENROLLED`) ensures clients receive deterministic responses without corrupting state. |

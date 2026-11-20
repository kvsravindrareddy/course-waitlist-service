# AI Prompt and Use Record

This document records the engineering activities performed with AI assistance during the design, implementation, and verification of the Course Waitlist Service.

---

## Engineering Activity 1: Domain Modeling & Concurrency Strategy

### 1. Prompt Used
> "Design a minimal, thread-safe Python service slice for single course offering registration with capacity limits, prerequisite verification, and FIFO waitlist promotion. What data structures and concurrency primitives best enforce zero capacity leakage without external database dependencies?"

### 2. AI Response Summary
The AI recommended:
- Representing `CourseOffering` with capacity, prerequisites, an `enrolled` list, and a `waitlist` deque/list.
- Using `threading.RLock` to serialize read-modify-write transactions across `enroll()` and `drop()`.
- Returning structured result dataclasses (`EnrollmentResult`, `DropResult`) with typed status enums rather than throwing raw runtime exceptions.

### 3. Review, Decision & Modifications
- **Accepted**:
  - The use of `threading.RLock` for atomic updates.
  - Typed result dataclasses with domain enums (`EnrollmentStatus`, `DropStatus`).
- **Edited / Enhanced**:
  - The AI initially suggested allowing a learner to be on the waitlist while enrolled. **Rejected this proposal**; established a strict **Mutual Exclusion Invariant**: a learner can exist in at most one state (`enrolled`, `waitlisted`, or `unregistered`).
  - Implemented idempotent re-enrollment guards (`ALREADY_ENROLLED` and `ALREADY_WAITLISTED`) so duplicate calls never alter queue ordering.

### 4. Verification Evidence
- Verified via [test_enrollment.py](file:///Users/veera/work/code/interview/charles/tests/test_enrollment.py) (`test_duplicate_enrollment_is_rejected_idempotently`) and [test_waitlist.py](file:///Users/veera/work/code/interview/charles/tests/test_waitlist.py) (`test_enrolled_learner_cannot_join_waitlist`).

---

## Engineering Activity 2: Ineligible Waitlist Promotion Policy

### 1. Prompt Used
> "When a seat opens upon an enrolled learner dropping, what are the architectural tradeoffs between 'Skip and Retain' vs 'Skip and Eject' for a waitlisted learner who currently does not satisfy prerequisites? How should this be implemented in the promotion loop?"

### 2. AI Response Summary
The AI outlined two strategies:
1. *Skip and Eject*: Remove the ineligible learner from the waitlist and promote the next candidate.
2. *Skip and Retain*: Leave the ineligible learner at their current position in the queue, skip them for the current seat, and promote the earliest eligible learner behind them.

### 3. Review, Decision & Modifications
- **Decision**: Adopted **Skip and Retain**.
- **Rationale**: In real-world academic institutions, students frequently join waitlists while prerequisite course grades are pending. Ejecting a learner entirely is excessively punitive and creates administrative overhead; retaining their queue seniority allows them to claim subsequent seats if they satisfy prerequisites before future drops, while immediately offering the open seat to the next qualified applicant.
- **Code Realization**: Implemented `_promote_next_eligible()` in `src/service.py` to scan index-by-index, promoting the first candidate meeting prerequisites without altering the relative ordering of skipped candidates.

### 4. Verification Evidence
- Verified via [test_promotion.py](file:///Users/veera/work/code/interview/charles/tests/test_promotion.py) (`test_skip_and_retain_policy_for_ineligible_waitlisted_learner`).

---

## Engineering Activity 3: High-Contention Concurrency Test Design

### 1. Prompt Used
> "Write a focused concurrency test in Python unittest to prove that 30 concurrent threads competing for 5 seats under strict FIFO waitlist promotion cannot breach capacity, duplicate learners, or leave orphaned seats."

### 2. AI Response Summary
The AI provided a test harness using `threading.Barrier` to synchronize worker threads and initiate simultaneous calls to `service.enroll()`.

### 3. Review, Decision & Modifications
- **Accepted**: `threading.Barrier(total_learners)` pattern to maximize race condition probability.
- **Edited / Enhanced**:
  - The AI script only asserted `len(enrolled) == capacity`.
  - Added mathematical set assertions:
    - $\text{enrolled} \cap \text{waitlist} = \emptyset$ (strictly disjoint).
    - $|\text{enrolled}| + |\text{waitlist}| = \text{total\_learners}$.
  - Added a second concurrent test `test_concurrent_drops_and_promotions` to stress concurrent drops and promotions under active contention.

### 4. Verification Evidence
- Executed `CourseConcurrencyTest`:
  ```
  testConcurrentEnrollmentContention ... PASSED
  testConcurrentDropsAndPromotions ... PASSED
  ```
  Passed with zero race conditions or capacity leaks.

---

## Engineering Activity 4: OpenAPI 3.0 Contract-First API & Code Generation

### 1. Prompt Used
> "Configure OpenAPI Generator Maven Plugin to generate Spring Boot 3 server interfaces and model DTOs directly from openapi.yaml. Ensure controllers implement generated interfaces without manual DTO duplication."

### 2. AI Response Summary
The AI suggested configuring `openapi-generator-maven-plugin` with `interfaceOnly=true`, `useSpringBoot3=true`, `openApiNullable=false`, and `useTags=true`.

### 3. Review, Decision & Modifications
- **Accepted**: Using `interfaceOnly=true` so `WaitlistApiController` implements `WaitlistApi`, preserving single-source-of-truth.
- **Enhanced**: Mapped generated DTOs cleanly to core domain models in `WaitlistApiController` to isolate core business rules from transport serialization concerns.

### 4. Verification Evidence
- Executed `./mvnw compile`: OpenAPI plugin generated all models and `WaitlistApi.java` into `target/generated-sources/openapi/` and compiled without warnings.

---

## Engineering Activity 5: Java 25 & Virtual Threads Concurrency

### 1. Prompt Used
> "Enable Java 25 Virtual Threads in Spring Boot 3.4.5 and create a high-throughput concurrency test with 100+ virtual threads contending for seats."

### 2. AI Response Summary
The AI suggested enabling `spring.threads.virtual.enabled: true` in `application.yml` and using `Executors.newVirtualThreadPerTaskExecutor()`.

### 3. Review, Decision & Modifications
- **Accepted**: Virtual threads configuration and per-task virtual executor pattern.
- **Enhanced**: Added `assertTrue(Thread.currentThread().isVirtual())` inside the task to explicitly verify execution on Loom virtual threads.

### 4. Verification Evidence
- Executed `./mvnw test`: All 32 tests passed including `testVirtualThreadContention` in 2.4 seconds under OpenJDK 25.0.5.1.

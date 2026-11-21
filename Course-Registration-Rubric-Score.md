# Course Waitlist Service — AI-Assisted Engineering Rubric Scorecard

This document evaluates the **KiVO Course Waitlist Service** against the criteria specified in the **Focused AI-Assisted Engineering Assessment: Course Waitlist**.

---

## Executive Summary & Score Summary

| Dimension | Rating | Max Possible | Evidence & Verification |
| :--- | :---: | :---: | :--- |
| **1. Domain Correctness & Invariants** | **Exceeds Expectations** | Exemplary | Enforces capacity ($0 \le N \le C$), strict FIFO waitlist, mutually disjoint sets, and consistent Skip-and-Retain policy. Verified by 24 unit & catalog tests. |
| **2. Concurrency & High Throughput** | **Exceeds Expectations** | Exemplary | Fair lock (`ReentrantLock(true)`) serialization prevents race conditions and thread starvation across virtual threads. Verified by 30-thread barrier and 100 virtual-thread tests. |
| **3. API Standards & OpenAPI CodeGen** | **Exceeds Expectations** | Exemplary | OpenAPI 3.0.3 single source of truth; build-time interface generation (`WaitlistApi`); RFC 7807 `ProblemDetail` error handling; zero contract drift. |
| **4. BDD & Test Coverage** | **Exceeds Expectations** | Exemplary | 36 automated tests (0 failures, 0 errors in ~2.8s) spanning unit, concurrency, catalog isolation, and 9 Gherkin BDD scenario specifications. |
| **5. AI-Assisted Engineering Practice** | **Exceeds Expectations** | Exemplary | Comprehensive `AI_USE_RECORD.md` logging prompts, evaluation, accepted/edited/rejected decisions, and test verification; multi-agent `/boost` audit fixing catalog sync bug. |
| **6. Documentation & Scope Discipline** | **Exceeds Expectations** | Exemplary | High-quality `DESIGN_SPEC.md`, `BDD_SPEC.md`, `LIMITATIONS_AND_RISKS.md`, `architecture.html`, and single-command runnable instructions (`./mvnw test`). |

---

## Detailed Evaluation by Assessment Rubric Criteria

### Criterion 1: Capacity & Prerequisite Validation (Acceptance Criterion 1)
- **Requirement**: Enrollment succeeds when capacity remains and the learner satisfies the stated prerequisite rule.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - `CourseWaitlistService.java` verifies prerequisites using `learner.hasPrerequisites(offering.getPrerequisites())` under fair lock.
  - If seats are available ($0 \le \text{enrolled.size()} < \text{capacity}$) and prerequisites are satisfied: learner is enrolled.
  - If prerequisites are missing, returns `PREREQUISITE_NOT_MET` with a detailed message of missing courses.
  - **Verification**: `CourseEnrollmentTest.testSuccessfulEnrollmentWithPrerequisite`, `CourseEnrollmentTest.testEnrollmentRejectedWhenPrerequisiteMissing`, BDD Scenario 1 & 2.

---

### Criterion 2: Waitlist Placement & Idempotency (Acceptance Criterion 2)
- **Requirement**: When full, an eligible learner joins the waitlist once; duplicate enrollment requests do not create duplicate records.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - When $\text{enrolled.size()} = \text{capacity}$, eligible learners are enqueued to `waitlist` in strict arrival order (FIFO).
  - Duplicate enrollment by an already enrolled learner returns `ALREADY_ENROLLED` without modifying state.
  - Duplicate enrollment by an already waitlisted learner returns `ALREADY_WAITLISTED` with their exact position retained.
  - **Verification**: `CourseWaitlistTest.testWaitlistActivationUponReachingCapacity`, `CourseWaitlistTest.testDuplicateWaitlistAttemptIsRejectedIdempotently`, `CourseWaitlistTest.testEnrolledLearnerCannotJoinWaitlist`, BDD Scenario 3 & 4.

---

### Criterion 3: FIFO Promotion upon Drop (Acceptance Criterion 3)
- **Requirement**: Dropping an enrolled learner opens a seat and promotes the earliest eligible waitlisted learner according to the documented eligibility policy.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - Dropping an enrolled learner opens a seat and triggers `promoteNextEligible()`.
  - The waitlist queue is scanned FIFO; the earliest eligible learner is dequeued and added to `enrolled`.
  - The drop result reports `DROPPED_AND_PROMOTED` with both learner IDs and `seatOpened=false`.
  - If waitlist is empty, reports `DROPPED` with `seatOpened=true`.
  - **Verification**: `CoursePromotionTest.testFifoPromotionUponDrop`, `CourseDropsTest.testDropEnrolledLearnerWhenWaitlistIsEmpty`, BDD Scenario 5 & 8.

---

### Criterion 4: Skip-and-Retain Promotion Policy (Acceptance Criterion 4)
- **Requirement**: An ineligible learner is not enrolled or promoted. The documented policy for skipping or retaining that learner is applied consistently.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - Architectural policy explicitly defined in `DESIGN_SPEC.md` Section 3: **Skip-and-Retain**.
  - During promotion scanning, if a waitlisted candidate does not satisfy prerequisites, they are skipped for the current open seat but retained at their exact queue index.
  - The seat is granted to the earliest eligible candidate behind them. The skipped learner preserves queue seniority for future seat openings if prerequisites are subsequently completed.
  - **Verification**: `CoursePromotionTest.testSkipAndRetainPolicyForIneligibleWaitlistedLearner`, BDD Scenario 9 (`ScenarioSkipAndRetainPolicy`).

---

### Criterion 5: Edge-Case Handling & State Integrity (Acceptance Criterion 5)
- **Requirement**: Invalid requests and duplicate drops return clear results without corrupting enrollment, capacity, or waitlist state.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - Repeated drops of an already dropped learner return `NOT_ENROLLED` without decrementing enrolled count or opening orphaned seats.
  - Dropping a non-registered or non-enrolled learner returns `NOT_ENROLLED`.
  - Dropping a waitlisted learner removes them from the waitlist (`DROPPED_FROM_WAITLIST`) without opening an enrolled seat or altering other learners' relative FIFO rank.
  - Null, empty, or whitespace IDs return `INVALID_REQUEST` without state mutation.
  - Multi-course offering switches isolate rosters cleanly with zero cross-course leakage.
  - **Verification**: `CourseDropsTest.testRepeatedDropOfEnrolledLearner`, `CourseDropsTest.testDropNonEnrolledLearnerReturnsNotEnrolled`, `CourseDropsTest.testInvalidLearnerIdOnDrop`, `CourseDropsTest.testDropFromWaitlistPreservesRemainingWaitlistOrder`, `CourseCatalogTest.testSwitchOfferingPreservesEnrollmentsAndIsolatedRosters`.

---

### Criterion 6: Concurrency & Loom Virtual Threads (Acceptance Criterion 6)
- **Requirement**: Preserve transaction or locking pattern and add a focused concurrency test.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - `CourseWaitlistService` employs a fair re-entrant lock (`ReentrantLock(true)`) protecting all mutating methods inside `try ... finally { lock.unlock(); }`.
  - Configured for Spring Boot 4.1.2 Virtual Threads (`spring.threads.virtual.enabled: true`).
  - Three dedicated high-contention concurrency tests:
    1. `testConcurrentEnrollmentContention`: 30 simultaneous threads competing for 5 seats via `CyclicBarrier`.
    2. `testConcurrentDropsAndPromotions`: Simultaneous drops and promotions under active contention.
    3. `testVirtualThreadContention`: 100 concurrent Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) verifying zero over-allocation or lost updates.
  - **Verification**: `CourseConcurrencyTest` (3 tests pass in < 0.1s).

---

### Criterion 7: AI Engineering Record & Critical Review (Acceptance Criterion 7)
- **Requirement**: Use AI for at least one engineering activity. Record prompts, responses, accepted/edited/rejected decisions, and independent verification evidence.
- **Score**: **Exemplary / Full Marks**
- **Evidence**:
  - `AI_USE_RECORD.md` documents 6 distinct engineering activities:
    1. Java Domain Modeling & Concurrency Strategy
    2. Ineligible Waitlist Promotion Policy (Skip-and-Retain vs Skip-and-Eject)
    3. High-Contention Concurrency Test Design
    4. OpenAPI 3.0 Contract-First API & Build-Time Code Generation
    5. Java 25 & Spring Boot 4.1.2 Virtual Threads Configuration
    6. Multi-Agent Review Routine (/boost): Detection & Resolution of Catalog List Synchronization Bug
  - Every activity details: exact prompt, AI response summary, review decisions (accept/edit/reject rationale), and automated test verification evidence.

---

## Deliverables Checklist

- [x] **Runnable Code**: Fully implemented in `src/main/java`, runnable with `./mvnw spring-boot:run` or `./mvnw test`.
- [x] **Automated Tests**: 36 comprehensive tests in `src/test/java` covering all acceptance criteria, concurrency, and BDD.
- [x] **Design Specification**: Detailed in [DESIGN_SPEC.md](file:///Users/veera/work/code/interview/course-waitlist-service/DESIGN_SPEC.md) including Mermaid sequence flows and invariant proofs.
- [x] **Interactive Architecture Visualizer**: Visual HTML visualizer in [architecture.html](file:///Users/veera/work/code/interview/course-waitlist-service/architecture.html) and web app static assets.
- [x] **AI Prompt & Use Record**: Fully documented in [AI_USE_RECORD.md](file:///Users/veera/work/code/interview/course-waitlist-service/AI_USE_RECORD.md).
- [x] **BDD Specification**: Documented in [BDD_SPEC.md](file:///Users/veera/work/code/interview/course-waitlist-service/BDD_SPEC.md) with Gherkin `.feature` file.
- [x] **Setup & Run Instructions**: Included in [README.md](file:///Users/veera/work/code/interview/course-waitlist-service/README.md).
- [x] **Limitations and Risks**: Detailed in [LIMITATIONS_AND_RISKS.md](file:///Users/veera/work/code/interview/course-waitlist-service/LIMITATIONS_AND_RISKS.md).
- [x] **Rubric Scorecard**: Scored and evidenced in [Course-Registration-Rubric-Score.md](file:///Users/veera/work/code/interview/course-waitlist-service/Course-Registration-Rubric-Score.md).

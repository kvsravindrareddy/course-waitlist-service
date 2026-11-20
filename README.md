# Course Waitlist Service — Engineering Assessment

A bounded, thread-safe, and self-verifying implementation of a **Course Enrollment, Drop, and FIFO Waitlist Promotion** service for a single course offering.

Built with Python 3 standard library (zero external dependencies).

---

## 1. Quick Start & Setup

### Java Fullstack (Spring Boot 3 + Interactive UI + JUnit 5)
- **Prerequisites**: JDK 17+
- **Run Tests** (22 JUnit 5 tests covering capacity, duplicates, FIFO promotion, prerequisites, drops, and high concurrency):
  ```bash
  cd /Users/veera/work/code/interview/charles
  ./mvnw test
  ```
- **Run Web Server & Fullstack UI**:
  ```bash
  ./mvnw spring-boot:run
  ```
  Then open [http://localhost:8080](http://localhost:8080) to interact with the visual dashboard, test personas, and live acceptance demo.

---

### Python Prototype (Standard Library)
- **Prerequisites**: Python 3.8+ (zero third-party dependencies)
- **Run Tests**:
  ```bash
  python3 -m unittest discover -v tests
  ```
- **Run Automated Acceptance Demo**:
  ```bash
  python3 -m src.cli --demo
  ```
- **Interactive CLI**:
  ```bash
  python3 -m src.cli
  ```

---

## 2. Project Structure

```
/Users/veera/work/code/interview/charles/
├── DESIGN_SPEC.md              # Complete design specification & invariants
├── AI_USE_RECORD.md            # AI prompts, decisions, evaluations, and verification
├── LIMITATIONS_AND_RISKS.md    # Production follow-up risks and architectural limitations
├── README.md                   # Setup, execution instructions, and acceptance mapping
├── src/
│   ├── __init__.py             # Package exports
│   ├── models.py               # Domain dataclasses & typed status enums
│   ├── service.py              # Thread-safe CourseWaitlistService with Skip-and-Retain policy
│   └── cli.py                  # Interactive REPL & automated demonstration runner
└── tests/
    ├── __init__.py             # Test package initialization
    ├── test_enrollment.py      # Available capacity, prerequisites, and duplicate enrollments
    ├── test_waitlist.py        # Full capacity, FIFO queue, duplicate waitlist prevention
    ├── test_promotion.py       # FIFO promotion, Skip-and-Retain policy, seat opening
    ├── test_drops.py           # Valid drop, repeated/duplicate drop, drop from waitlist
    └── test_concurrency.py     # Multi-threaded contention & concurrent drop/promotions
```

---

## 3. Acceptance Criteria Mapping

| # | Acceptance Criterion | Implementation & Test Evidence |
| :--- | :--- | :--- |
| **1** | **Enrollment succeeds** when capacity remains and learner meets prerequisites. | Enforces prerequisite checks in `service.enroll()`. Covered by `test_successful_enrollment_with_prerequisite` in `test_enrollment.py`. |
| **2** | **When full, eligible learner joins waitlist once**; duplicate enrollment requests do not create duplicate records. | Returns `WAITLISTED` with position tracking. Duplicate calls return `ALREADY_WAITLISTED`. Covered by `test_waitlist_activation_upon_reaching_capacity` and `test_duplicate_waitlist_attempt_is_rejected_idempotently` in `test_waitlist.py`. |
| **3** | **Dropping an enrolled learner opens a seat and promotes the earliest eligible learner** according to the documented eligibility policy. | `service.drop()` removes enrolled learner and invokes `_promote_next_eligible()`. Covered by `test_fifo_promotion_upon_drop` in `test_promotion.py`. |
| **4** | **Ineligible learner is not enrolled or promoted**; documented policy for skipping or retaining is applied consistently. | **Skip-and-Retain Policy** implemented: ineligible waitlisted learners are skipped during promotion but retain their queue seniority. Covered by `test_skip_and_retain_policy_for_ineligible_waitlisted_learner` in `test_promotion.py`. |
| **5** | **Invalid requests and duplicate drops** return clear results without corrupting enrollment, capacity, or waitlist state. | Returns `NOT_ENROLLED` or `INVALID_REQUEST` without modifying counters. Covered by `test_repeated_drop_of_enrolled_learner` and `test_invalid_learner_id_on_drop` in `test_drops.py`. |
| **6** | **Tests cover all required scenarios including concurrency**. | 22 comprehensive tests covering capacity, duplicates, FIFO promotion, prerequisites, invalid drops, and multi-threaded barrier contention (`test_concurrency.py`). |
| **7** | **AI used for at least one engineering activity** with complete prompt record, review, and verification evidence. | Fully documented in `AI_USE_RECORD.md` with prompt records, critical evaluations, rejected suggestions, and verification logs. |

---

## 4. Key Architectural Invariants

1. **Capacity Invariant**: $0 \le |\text{enrolled\_learners}| \le \text{capacity}$ at all times.
2. **Mutual Exclusion Invariant**: $\text{enrolled\_learners} \cap \text{waitlist} = \emptyset$. A learner can never be both enrolled and waitlisted simultaneously.
3. **Queue Integrity Invariant**: The waitlist strictly preserves FIFO placement order. Duplicate registration attempts do not reset queue seniority.
4. **Prerequisite Invariant**: A learner cannot enter `enrolled_learners` (either via direct enrollment or waitlist promotion) unless $\text{prerequisites} \subseteq \text{completed\_courses}$.

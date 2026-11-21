# Course Waitlist Service — BDD Specification Document

## 1. Overview & Methodology

This document defines the **Behavior-Driven Development (BDD)** specification for the **KiVO Course Waitlist Service**.

BDD establishes a ubiquitous domain language bridging product requirements, acceptance criteria, and automated test execution. Each scenario is authored in standard **Gherkin syntax** (`Given`, `When`, `Then`, `And`) and mapped directly to executable Java BDD test cases.

The source Gherkin feature file is located at:
[src/test/resources/features/course_waitlist.feature](file:///Users/veera/work/code/interview/course-waitlist-service/src/test/resources/features/course_waitlist.feature)

---

## 2. User Persona & Feature Narrative

```gherkin
Feature: Course Enrollment, Drop, and FIFO Waitlist Promotion

  As a learner at KiVO
  I want to register for a course offering, join a FIFO waitlist if the course is full, and be promoted automatically when seats open
  So that registration is fair, transparent, and strictly respects capacity and prerequisites.
```

### Primary Personas
- **Alice / Bob / David / Eve**: Eligible learners who have completed prerequisite `CS101`.
- **Carol**: Ineligible learner who has completed no prerequisites.
- **Frank**: Ineligible waitlisted learner (simulating a learner with incomplete grades awaiting prerequisite review).
- **Registrar Administrator**: Ensures capacity limits are never breached and that waitlist ordering is mathematically fair.

---

## 3. Acceptance Criteria to BDD Scenario Traceability Matrix

| Acceptance Criteria | Gherkin Scenario Name | Verified By |
| :--- | :--- | :--- |
| **AC 1: Available Capacity & Prerequisite Validation** | Scenario: Eligible learner enrolls successfully when capacity is available<br>Scenario: Ineligible learner is rejected when prerequisite is missing | `testEligibleLearnerEnrollsSuccessfully`<br>`testIneligibleLearnerRejectedWhenPrerequisiteMissing` |
| **AC 2: Full Capacity & Duplicate Rejections** | Scenario: Course reaches full capacity and subsequent eligible learner joins waitlist<br>Scenario: Duplicate enrollment requests for enrolled and waitlisted learners are rejected idempotently | `testCourseReachesFullCapacityAndLearnerJoinsWaitlist`<br>`testDuplicateEnrollmentRejectedIdempotently` |
| **AC 3: FIFO Promotion upon Drop** | Scenario: Dropping an enrolled learner promotes the earliest eligible waitlisted learner in FIFO order | `testDroppingEnrolledLearnerPromotesFifoWaitlist` |
| **AC 4: Skip-and-Retain Eligibility Policy** | Scenario: Ineligible waitlisted learner is skipped for promotion but retained in queue, and next eligible learner is promoted | `testSkipAndRetainPolicyForIneligibleWaitlist` |
| **AC 5: Empty Waitlist & Duplicate Drops** | Scenario: Dropping an enrolled learner when waitlist is empty opens an available seat<br>Scenario: Dropping a non-enrolled learner returns NOT_ENROLLED without state corruption<br>Scenario: Repeated drop of an already dropped learner does not leak capacity<br>Scenario: Dropping directly from the waitlist removes learner without opening an enrolled seat | `testDroppingWhenWaitlistIsEmptyOpensSeat`<br>`testDroppingNonEnrolledLearnerReturnsNotEnrolled`<br>`testRepeatedDropDoesNotLeakCapacity`<br>`testDroppingDirectlyFromWaitlist` |
| **AC 6: Concurrency & Thread-Safety** | Multi-threaded barrier contention test (30 threads) & Java 25 Virtual Threads stress test (100 virtual threads) | `CourseConcurrencyTest.testConcurrentEnrollmentContention`<br>`CourseConcurrencyTest.testVirtualThreadContention` |

---

## 4. Gherkin Scenarios Specification

### Scenario 1: Eligible learner enrolls successfully when capacity is available
```gherkin
Given a course offering "CS201" titled "Data Structures & Algorithms" with capacity 2
And the course offering requires prerequisite "CS101"
And registered learner "alice" has completed "CS101"
When "alice" enrolls in the course
Then the enrollment status should be "ENROLLED"
And the enrolled count should be 1
And the available seats should be 1
And "alice" should be in the enrolled roster
```

### Scenario 2: Ineligible learner is rejected when prerequisite is missing
```gherkin
Given a course offering "CS201" requiring prerequisite "CS101"
And registered learner "carol" has completed no courses
When "carol" enrolls in the course
Then the enrollment status should be "PREREQUISITE_NOT_MET"
And the enrolled count should be 0
And the available seats should be 2
And "carol" should not be in the enrolled roster
And "carol" should not be on the waitlist
```

### Scenario 3: Course reaches full capacity and subsequent eligible learner joins waitlist
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
When "david" enrolls in the course
Then the enrollment status should be "WAITLISTED"
And the waitlist position should be 1
And the enrolled count should be 2
And the available seats should be 0
And "david" should be at position 1 on the waitlist
```

### Scenario 4: Duplicate enrollment requests are rejected idempotently
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
And "david" has joined the waitlist at position 1
When "alice" attempts to enroll again
Then the enrollment status should be "ALREADY_ENROLLED"
When "david" attempts to enroll again
Then the enrollment status should be "ALREADY_WAITLISTED"
And the waitlist position should remain 1
And the enrolled count should remain 2
And the waitlist should remain ["david"]
```

### Scenario 5: Dropping an enrolled learner promotes the earliest waitlisted learner in FIFO order
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
And "david" has joined the waitlist at position 1
And "eve" has joined the waitlist at position 2
When "alice" drops the course
Then the drop status should be "DROPPED_AND_PROMOTED"
And the dropped learner should be "alice"
And the promoted learner should be "david"
And the enrolled roster should contain ["bob", "david"]
And the waitlist should contain ["eve"]
And the available seats should be 0
```

### Scenario 6: Ineligible waitlisted learner is skipped for promotion but retained in queue (Skip-and-Retain)
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
And ineligible learner "frank" with missing prerequisite "CS101" is at waitlist position 1
And eligible learner "david" is at waitlist position 2
When "alice" drops the course
Then the drop status should be "DROPPED_AND_PROMOTED"
And the promoted learner should be "david"
And "frank" should be retained on the waitlist at position 1
And "frank" should not be enrolled
And the enrolled roster should contain ["bob", "david"]
```

### Scenario 7: Dropping an enrolled learner when waitlist is empty opens an available seat
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
And the waitlist is empty
When "alice" drops the course
Then the drop status should be "DROPPED"
And an enrolled seat should open
And the enrolled count should be 1
And the available seats should be 1
```

### Scenario 8: Repeated drop of an already dropped learner does not leak capacity
```gherkin
Given "alice" has enrolled in the course
When "alice" drops the course
Then the drop status should be "DROPPED"
And the available seats should be 2
When "alice" drops the course again
Then the drop status should be "NOT_ENROLLED"
And the available seats should strictly remain 2
```

### Scenario 9: Dropping directly from the waitlist
```gherkin
Given "alice" has enrolled in the course
And "bob" has enrolled in the course
And "david" has joined the waitlist at position 1
When "david" drops the course
Then the drop status should be "DROPPED_FROM_WAITLIST"
And no enrolled seat should open
And the waitlist should be empty
And the enrolled count should remain 2
```

---

## 5. Automated Execution

The BDD test suite is implemented in Java and executes automatically with JUnit 5:

```bash
cd /Users/veera/work/code/interview/course-waitlist-service
./mvnw test -Dtest=CourseWaitlistBddTest
```

Test reports and assertions verify that all Gherkin Given/When/Then conditions hold true across both the service layer and the REST controller.

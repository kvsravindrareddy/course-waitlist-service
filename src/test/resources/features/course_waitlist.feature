Feature: Course Enrollment, Drop, and FIFO Waitlist Promotion

  As a learner at KiVO
  I want to register for a course offering, join a FIFO waitlist if the course is full, and be promoted automatically when seats open
  So that registration is fair, transparent, and strictly respects capacity and prerequisites.

  Background:
    Given a course offering "CS201" titled "Data Structures & Algorithms" with capacity 2
    And the course offering requires prerequisite "CS101"
    And registered learner "alice" has completed "CS101"
    And registered learner "bob" has completed "CS101"
    And registered learner "carol" has completed no courses
    And registered learner "david" has completed "CS101"
    And registered learner "eve" has completed "CS101"

  # Acceptance Criteria 1: Available Capacity & Prerequisite Validation
  Scenario: Eligible learner enrolls successfully when capacity is available
    When "alice" enrolls in the course
    Then the enrollment status should be "ENROLLED"
    And the enrolled count should be 1
    And the available seats should be 1
    And "alice" should be in the enrolled roster

  Scenario: Ineligible learner is rejected when prerequisite is missing
    When "carol" enrolls in the course
    Then the enrollment status should be "PREREQUISITE_NOT_MET"
    And the enrolled count should be 0
    And the available seats should be 2
    And "carol" should not be in the enrolled roster
    And "carol" should not be on the waitlist

  # Acceptance Criteria 2: Full Capacity & Duplicate Rejections
  Scenario: Course reaches full capacity and subsequent eligible learner joins waitlist
    Given "alice" has enrolled in the course
    And "bob" has enrolled in the course
    When "david" enrolls in the course
    Then the enrollment status should be "WAITLISTED"
    And the waitlist position should be 1
    And the enrolled count should be 2
    And the available seats should be 0
    And "david" should be at position 1 on the waitlist

  Scenario: Duplicate enrollment requests for enrolled and waitlisted learners are rejected idempotently
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

  # Acceptance Criteria 3: FIFO Promotion upon Drop
  Scenario: Dropping an enrolled learner promotes the earliest eligible waitlisted learner in FIFO order
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

  # Acceptance Criteria 4: Skip-and-Retain Eligibility Policy
  Scenario: Ineligible waitlisted learner is skipped for promotion but retained in queue, and next eligible learner is promoted
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

  # Acceptance Criteria 5: Empty Waitlist Drop & Invalid/Duplicate Drops
  Scenario: Dropping an enrolled learner when waitlist is empty opens an available seat
    Given "alice" has enrolled in the course
    And "bob" has enrolled in the course
    And the waitlist is empty
    When "alice" drops the course
    Then the drop status should be "DROPPED"
    And an enrolled seat should open
    And the enrolled count should be 1
    And the available seats should be 1

  Scenario: Dropping a non-enrolled learner returns NOT_ENROLLED without state corruption
    When "david" drops the course
    Then the drop status should be "NOT_ENROLLED"
    And the enrolled count should remain 0
    And the available seats should remain 2

  Scenario: Repeated drop of an already dropped learner does not leak capacity
    Given "alice" has enrolled in the course
    When "alice" drops the course
    Then the drop status should be "DROPPED"
    And the available seats should be 2
    When "alice" drops the course again
    Then the drop status should be "NOT_ENROLLED"
    And the available seats should strictly remain 2

  Scenario: Dropping directly from the waitlist removes learner without opening an enrolled seat
    Given "alice" has enrolled in the course
    And "bob" has enrolled in the course
    And "david" has joined the waitlist at position 1
    When "david" drops the course
    Then the drop status should be "DROPPED_FROM_WAITLIST"
    And no enrolled seat should open
    And the waitlist should be empty
    And the enrolled count should remain 2

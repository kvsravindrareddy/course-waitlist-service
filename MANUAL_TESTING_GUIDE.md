# Manual Verification & Evidence Capture Guide

This guide provides step-by-step manual test scenarios to verify the **KiVO Course Waitlist Service** through the interactive web dashboard ([http://localhost:8080](http://localhost:8080)).

Use this guide to test each requirement, observe expected UI/log transitions, and capture screenshots for assessment submission evidence.

---

## Prerequisites & Launch

1. Start the Spring Boot application:
   ```bash
   cd /Users/veera/work/code/interview/course-waitlist-service
   ./mvnw spring-boot:run
   ```
2. Open your browser to: **[http://localhost:8080](http://localhost:8080)**.
3. If at any point you want to start fresh, click the **"↺ Reset Course State"** button in the top navigation bar.

---

## Scenario 1: Eligible Learner Enrolls Successfully (Available Capacity)

- **Acceptance Criterion**: AC 1 (Enrollment succeeds when capacity remains and prerequisites are met).
- **Description**: Verifies that an eligible student with required prerequisites successfully claims an available seat.
- **Initial State**: Click **"↺ Reset Course State"** (CS201 has capacity 2, prerequisite `CS101`, 0 enrolled, 2 available seats).

### Steps
1. Under **"Test Personas"**, locate **Alice Chen (alice)**. Notice her badge says `✓ Eligible [CS101, CS201]`.
2. Click the blue **"Enroll"** button next to Alice.

### Expected Result
- **Hero Card**: Enrolled count increments from `0` to `1`; Available seats decrement to `1`. Capacity progress bar fills to 50%.
- **Enrolled Roster Card**: Alice Chen appears under **Seat #1**.
- **Audit Log**: Green badge `[ENROLLED] Successfully enrolled in Data Structures & Algorithms.`
- 📸 **Screenshot 1**: Capture the dashboard showing Alice in Seat #1 and the green `[ENROLLED]` audit log entry.

---

## Scenario 2: Ineligible Learner Rejection (Prerequisite Missing)

- **Acceptance Criterion**: AC 1 & AC 4 (Ineligible learner is rejected without state corruption).
- **Description**: Verifies that Carol Danvers (who has completed 0 courses) is strictly rejected when attempting to enroll in CS201 (requires `CS101`).
- **Initial State**: Continuing from Scenario 1 (Alice is in Seat #1, 1 seat remaining).

### Steps
1. In the **"Enroll from List"** card:
   - Select **Carol Danvers (carol) — ⚠ Missing: CS101** from the dropdown.
   - Ensure the checkbox **"Auto-fulfill course prerequisites if missing"** is **UNCHECKED**.
   - Click **"Enroll Selected Learner"**.
   - *(Alternative)*: Click the blue **"Enroll"** button on Carol's row under **Test Personas**.

### Expected Result
- **Roster & Capacity**: Capacity remains strictly at `1/2` (1 available seat).
- **Carol's State**: Carol is **NOT** added to the enrolled roster and **NOT** added to the waitlist.
- **Audit Log**: Red badge:
  ```
  [PREREQUISITE_NOT_MET] Cannot enroll: missing prerequisite course(s): CS101.
  ```
- 📸 **Screenshot 2**: Capture the red `[PREREQUISITE_NOT_MET]` audit log entry showing Carol was rejected and 1 seat remains open.

---

## Scenario 3: Capacity Saturation & FIFO Waitlist Queue

- **Acceptance Criterion**: AC 2 (When full, eligible learners join the FIFO waitlist).
- **Description**: Verifies that filling the final seat closes available capacity, and subsequent eligible applicants join the waitlist at sequential FIFO ranks.
- **Initial State**: Continuing from Scenario 2 (Alice in Seat #1, 1 seat remaining).

### Steps
1. Under **"Test Personas"**, click **"Enroll"** on **Bob Smith (bob)** (has `CS101`).
2. Observe Bob takes **Seat #2**; Course capacity is now full (`2/2`, Available Seats: `0`, progress bar turns amber/red).
3. Click **"Enroll"** on **David Miller (david)** (has `CS101`).
4. Click **"Enroll"** on **Eve Johnson (eve)** (has `CS101`, `CS201`).

### Expected Result
- **Enrolled Roster**: Alice (Seat #1), Bob (Seat #2).
- **FIFO Waitlist Card**:
  - **FIFO #1**: David Miller (`✓ Eligible for promotion`).
  - **FIFO #2**: Eve Johnson (`✓ Eligible for promotion`).
- **Audit Log**:
  - `[ENROLLED] Successfully enrolled in Data Structures & Algorithms.` (Bob)
  - `[WAITLISTED] Course is at capacity (2/2). Placed on waitlist at position #1.` (David)
  - `[WAITLISTED] Course is at capacity (2/2). Placed on waitlist at position #2.` (Eve)
- 📸 **Screenshot 3**: Capture both rosters full: 2 Enrolled (Alice, Bob) and 2 Waitlisted (David #1, Eve #2).

---

## Scenario 4: Idempotent Rejections for Duplicate Requests

- **Acceptance Criterion**: AC 2 & AC 5 (Duplicate enrollment requests do not create duplicate records or alter queue rank).
- **Description**: Verifies that duplicate requests from both enrolled and waitlisted students are safely rejected.
- **Initial State**: Continuing from Scenario 3 (Alice & Bob enrolled; David & Eve waitlisted).

### Steps
1. In the **"Enroll from List"** card, select **Alice Chen (alice)** and click **"Enroll Selected Learner"**.
2. Select **David Miller (david)** and click **"Enroll Selected Learner"**.

### Expected Result
- **For Alice**: Warning badge `[ALREADY_ENROLLED] Learner 'alice' is already enrolled in Data Structures & Algorithms.`
- **For David**: Warning badge `[ALREADY_WAITLISTED] Learner 'david' is already on the waitlist at position #1.`
- **Roster & Queue Integrity**: David remains at rank `#1`, Eve at `#2`. No duplicates appear in either list.
- 📸 **Screenshot 4**: Capture the audit log displaying both `[ALREADY_ENROLLED]` and `[ALREADY_WAITLISTED]` rejections without queue changes.

---

## Scenario 5: Seat Release & FIFO Automatic Promotion

- **Acceptance Criterion**: AC 3 (Dropping an enrolled learner opens a seat and promotes the earliest eligible waitlisted learner).
- **Description**: Verifies that when Alice drops her seat, David (FIFO #1) is atomically promoted, leaving capacity at 2/2 and moving Eve to FIFO #1.
- **Initial State**: Continuing from Scenario 4 (Enrolled: Alice, Bob; Waitlist: David #1, Eve #2).

### Steps
1. In the **Enrolled Learners** card, click the red **"Drop"** button next to **Alice Chen (alice)**.

### Expected Result
- **Promotion Outcome**: Blue badge `[DROPPED_AND_PROMOTED] Learner 'alice' dropped. Seat filled by waitlisted learner 'david'.`
- **Enrolled Roster**: Now contains **Bob** (Seat #1) and **David** (Seat #2).
- **FIFO Waitlist**: David is removed; **Eve** is now at **FIFO #1**.
- **Capacity**: Remains full at `2/2` (0 available seats). Zero capacity leakage.
- 📸 **Screenshot 5**: Capture the updated enrolled roster showing David in Seat #2 and Eve at Waitlist #1.

---

## Scenario 6: "Skip-and-Retain" Promotion Policy

- **Acceptance Criterion**: AC 4 (Ineligible waitlisted learner is skipped for promotion but retained in queue; next eligible learner is promoted).
- **Description**: Demonstrates that if an ineligible student is at the head of the waitlist when a seat opens, the system skips them to promote the next qualified applicant, while preserving the skipped student's queue seniority.
- **Initial State**: Click **"↺ Reset Course State"** to start fresh.

### Option A: 1-Click Interactive Demo
1. In the right-hand panel, click the orange button:
   ```
   ▶ Demo Scenario 6: Skip & Retain Policy
   ```
2. The UI automatically runs the setup and displays:
   - Alice & Bob enrolled (`2/2`).
   - Frank placed on waitlist at FIFO #1 with pending prerequisites (`Missing CS101`).
   - Eve joins waitlist at FIFO #2 (`Eligible with CS101`).
   - Alice drops: Frank is **skipped & retained** at FIFO #1; Eve is **promoted** to Seat #1!

---

### Option B: Step-by-Step Manual Actions
1. Click **"↺ Reset Course State"** at the top (ensures all learners are at default baseline: Frank has 0 prerequisites).
2. Enroll **Alice** and **Bob** to fill capacity (`2/2`).
3. Place **Frank Wright (frank)** on the waitlist with pending prerequisites:
   - In the **"Enroll from List"** card, select **Frank Wright (frank)** from the dropdown.
   - Click the bright orange button: **`+ Waitlist (Pending)`** (right next to "Enroll Selected").
   - *(Alternative)*: Click the orange **`+ Waitlist (Pending)`** button on Frank's row under **Test Personas**.
   - *Observation*: Frank joins the waitlist at **FIFO #1** with badge: `⚠ Missing: CS101 (Skip & Retain)`. (Frank maintains 0 prerequisites).
4. Under **"Test Personas"**, click **"Enroll"** on **Eve Johnson (eve)** (has `CS101`).
   - *Observation*: Eve joins the waitlist at **FIFO #2** with badge: `✓ Eligible for promotion`.
5. In the **Enrolled Learners** card, click the red **"Drop"** button next to **Alice Chen (alice)**.

### Expected Result & Policy Contrast
- **Why Eve is Promoted**: Eve satisfies the prerequisite `CS101` and is the earliest eligible learner in line.
- **Why Frank is Skipped & Retained**: Frank lacks `CS101`. If Frank had `CS101`, promoting him would be standard FIFO (Scenario 5). But because he is currently ineligible, the Skip-and-Retain policy skips him, **retains Frank at FIFO #1** for future seat openings once he completes `CS101`, and promotes Eve!
- **Enrolled Roster**: Shows **Bob** (Seat #1) and **Eve** (Seat #2).
- **FIFO Waitlist**: Shows **Frank** alone at **FIFO #1** (`⚠ Missing: CS101 (Skip & Retain)`).
- **Audit Log**:
  ```
  [DROPPED_AND_PROMOTED] Learner 'alice' dropped. Seat filled by waitlisted learner 'eve'.
  ```
- 📸 **Screenshot 6**: Capture the Enrolled Roster showing Eve promoted and the FIFO Waitlist showing Frank retained at position #1.

---

## Scenario 7: Drop Directly from Waitlist

- **Acceptance Criterion**: AC 5 (Dropping a waitlisted learner removes them without opening an enrolled seat).
- **Description**: Verifies that dropping an applicant from the waitlist removes them from the queue without impacting enrolled seats.
- **Initial State**: Continuing from Scenario 6 (Frank is at Waitlist #1).

### Steps
1. In the **FIFO Waitlist** card, click the grey **"Remove"** button next to Frank.

### Expected Result
- **Waitlist**: Frank is removed; waitlist is now empty (`0`).
- **Enrolled Roster**: Bob and Eve remain enrolled (`2/2`). Available seats remain `0`.
- **Audit Log**: `[DROPPED_FROM_WAITLIST] Learner 'frank' was removed from the waitlist.`
- 📸 **Screenshot 7**: Capture the empty waitlist and audit log confirming `[DROPPED_FROM_WAITLIST]`.

---

## Scenario 8: Invalid Requests & Repeated Drops

- **Acceptance Criterion**: AC 5 (Repeated drops of already dropped learners return NOT_ENROLLED without state corruption).
- **Description**: Verifies that dropping Bob when waitlist is empty opens Seat #1, and immediately dropping Bob a second time returns `NOT_ENROLLED` without leaking additional seats.
- **Initial State**: Continuing from Scenario 7 (Bob & Eve enrolled, waitlist empty).

### Steps
1. Drop **Bob** from Enrolled roster.
2. In the **"Test Personas"** card, click **"Drop"** on Bob again (or select Bob from list).

### Expected Result
- **First Drop**: Status `[DROPPED] Learner 'bob' dropped. 1 seat opened.` (Available seats: 1).
- **Second Drop**: Red badge:
  ```
  [NOT_ENROLLED] Learner 'bob' is neither enrolled nor waitlisted in Data Structures & Algorithms.
  ```
- **Capacity**: Available seats remains strictly `1` (does not increment to 2 or corrupt state).
- 📸 **Screenshot 8**: Capture the audit log showing `[NOT_ENROLLED]` on repeated drop and available seats remaining strictly 1.

---

## Scenario 9: Multi-Course Catalog Selection & Offering Isolation

- **Acceptance Criterion**: Custom multi-course offering isolation and dynamic prerequisite verification.
- **Description**: Verifies switching between courses in the catalog and confirms each course maintains its own isolated roster, capacity, and prerequisites.
- **Initial State**: Open dashboard.

### Steps
1. In the top course selection dropdown **"Active Course Offering"**, select **CS301 — Advanced Algorithms & Systems [Prereq: CS201, CS101 | Cap: 3]**.
2. Observe the hero banner updates to `CS301` with capacity `3` and prerequisites `CS201, CS101`.
3. Under **"Test Personas"**:
   - Bob (has `CS101`, missing `CS201`) shows `⚠ Missing: CS201`. Clicking Enroll rejects Bob!
   - Eve (has `CS101`, `CS201`) shows `✓ Eligible`. Click **"Enroll"** on Eve -> successfully enrolls in Seat #1 of CS301.
4. Switch the course dropdown back to **CS201**.
5. Observe CS201 restores its exact previous state without any data bleeding into or from CS301!

### Expected Result
- Complete state and prerequisite isolation across distinct course offerings.
- 📸 **Screenshot 9**: Capture the CS301 course view showing capacity 3 and prerequisites `CS201, CS101`.

---

## Scenario 10: Automated 10-Step Acceptance Criteria Demo

- **Acceptance Criterion**: AC 1–AC 6 comprehensive end-to-end automated verification.
- **Description**: Executes the automated verification suite built directly into the UI dashboard, running 10 sequential actions with visual audit badges.

### Steps
1. In the right-hand panel, click the purple button:
   ```
   ▶ Run Acceptance Verification Demo
   ```
2. Watch the live execution stream across all 10 steps.

### Expected Result
- Step 1: Alice enrolls -> Seat 1/2 (`[ENROLLED]`)
- Step 2: Bob enrolls -> Seat 2/2, full capacity (`[ENROLLED]`)
- Step 3: Alice duplicate enrollment -> Rejected (`[ALREADY_ENROLLED]`)
- Step 4: Carol enrolls missing CS101 -> Rejected (`[PREREQUISITE_NOT_MET]`)
- Step 5: David enrolls -> Joins waitlist #1 (`[WAITLISTED]`)
- Step 6: Eve enrolls -> Joins waitlist #2 (`[WAITLISTED]`)
- Step 7: David duplicate waitlist -> Rejected (`[ALREADY_WAITLISTED]`)
- Step 8: Alice drops -> David promoted FIFO (`[DROPPED_AND_PROMOTED]`)
- Step 9: Alice drops again -> Rejected idempotently (`[NOT_ENROLLED]`)
- Step 10: Bob drops -> Eve promoted FIFO (`[DROPPED_AND_PROMOTED]`)
- Final message: `✓ All Acceptance Criteria successfully verified!`
- 📸 **Screenshot 10**: Capture the completed audit log showing the 10 green checkmark steps and `DEMO_COMPLETE`.

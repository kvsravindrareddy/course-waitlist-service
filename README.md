# Course Waitlist Service — Java 25 Enterprise Architecture

A high-reliability, thread-safe service slice implementing **course enrollment, dropping, and FIFO waitlist promotion** for a single course offering.

Built with **Java 25**, **Spring Boot 4.1.2**, **Java Virtual Threads (Project Loom)**, **OpenAPI 3.0 Contract-First Code Generation**, and **Cucumber-Style BDD Verification**.

---

## 1. Quick Start & Setup

### Environment Requirements
- **JDK 25** (`openjdk@25` installed at `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`)
- The included Maven Wrapper `./mvnw` is pre-configured to detect and use Java 25 automatically.

### Run All Automated Tests (36 Tests)
Runs all unit tests, virtual thread concurrency stress tests, multi-course catalog tests, and BDD scenario tests:
```bash
cd /Users/veera/work/code/interview/course-waitlist-service
./mvnw test
```

### Run Web Application & Interactive Fullstack UI
```bash
./mvnw spring-boot:run
```
- **Interactive UI Dashboard**: [http://localhost:8080](http://localhost:8080)
- **Manual Testing & Screenshot Evidence Guide**: [MANUAL_TESTING_GUIDE.md](file:///Users/veera/work/code/interview/course-waitlist-service/MANUAL_TESTING_GUIDE.md)
- **Architecture & Sequence Flow Visualizer**: [http://localhost:8080/architecture.html](http://localhost:8080/architecture.html) (or open local [architecture.html](file:///Users/veera/work/code/interview/course-waitlist-service/architecture.html))
- **OpenAPI 3.0 Specification**: [http://localhost:8080/openapi.yaml](http://localhost:8080/openapi.yaml) or [openapi.yaml](file:///Users/veera/work/code/interview/course-waitlist-service/openapi.yaml)
- **Swagger UI Interactive API Docs**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 2. Architecture & Design Highlights

1. **Java 25 & Virtual Threads**:
   - `spring.threads.virtual.enabled: true` in `application.yml` provisions lightweight virtual threads for high concurrency without thread pool starvation.
   - Guarded by fair re-entrant locking (`ReentrantLock(true)`) preventing thread starvation and race conditions during high-volume spikes.

2. **OpenAPI 3.0 Contract-First**:
   - Contract defined in `openapi.yaml`.
   - `openapi-generator-maven-plugin` (7.1.0) generates `WaitlistApi` server interface and typed DTOs into `target/generated-sources/openapi/`.
   - `WaitlistApiController` implements `WaitlistApi` directly, ensuring zero contract drift.

3. **Behavior-Driven Development (BDD)**:
   - Full Gherkin feature file in `src/test/resources/features/course_waitlist.feature`.
   - Traceability matrix and persona narratives documented in `BDD_SPEC.md`.
   - Step definitions and execution verified via JUnit 5 in `CourseWaitlistBddTest.java`.

4. **Skip-and-Retain Waitlist Policy**:
   - If the front-of-queue learner does not satisfy prerequisites at the time a seat opens, the system **skips** them to inspect the next earliest learner, while **retaining** the ineligible learner in their position so their seniority is preserved once they meet the prerequisites.

---

## 3. Project Structure

```
/Users/veera/work/code/interview/course-waitlist-service/
├── pom.xml                                      # Maven POM: Java 25, Spring Boot 4.1.2, OpenAPI plugin
├── openapi.yaml                                 # OpenAPI 3.0.3 Contract Specification
├── architecture.html                            # Interactive Architecture Blueprint & Sequence Flow Visualizer
├── DESIGN_SPEC.md                               # Comprehensive Architecture, Sequence Diagrams & Invariants
├── BDD_SPEC.md                                  # BDD Feature Specification & Traceability Matrix
├── AI_USE_RECORD.md                             # AI Prompt Record, Evaluation, and Verification Audit
├── Course-Registration-Rubric-Score.md          # Evaluation Scorecard against AI-Assisted Assessment Rubric
├── LIMITATIONS_AND_RISKS.md                     # Production Follow-up Risks & In-Memory Boundaries
├── mvnw                                         # Self-contained Maven Wrapper (Java 25 configured)
├── src/
│   ├── main/
│   │   ├── java/com/kivo/waitlist/
│   │   │   ├── CourseWaitlistApplication.java   # Spring Boot 4.1.2 Entrypoint
│   │   │   ├── controller/
│   │   │   │   ├── WaitlistApiController.java   # Implements OpenAPI-generated WaitlistApi
│   │   │   │   └── GlobalExceptionHandler.java  # RFC 7807 ProblemDetail error handler
│   │   │   ├── service/
│   │   │   │   └── CourseWaitlistService.java   # Thread-safe core domain service
│   │   │   └── model/
│   │   │       ├── CourseOffering.java          # Offering aggregate root
│   │   │       ├── Learner.java                 # Learner profile & completed prerequisites
│   │   │       ├── EnrollmentResult.java        # Typed enrollment response
│   │   │       ├── DropResult.java              # Typed drop response
│   │   │       └── OfferingSnapshot.java        # Point-in-time immutable snapshot
│   │   └── resources/
│   │       ├── application.yml                  # Config (Virtual Threads enabled, CS201 defaults)
│   │       └── static/
│   │           ├── index.html                   # Interactive Single-Page Application (SPA)
│   │           ├── architecture.html            # Visual Architecture Blueprint & Flow Diagrams
│   │           ├── app.js                       # Real-time state management & API client
│   │           ├── style.css                    # Responsive modern dashboard styles
│   │           └── openapi.yaml                 # Static OpenAPI contract
│   └── test/
│       ├── java/com/kivo/waitlist/
│       │   ├── CourseEnrollmentTest.java        # Capacity & prerequisite unit tests (7 tests)
│       │   ├── CourseWaitlistTest.java          # FIFO waitlist & idempotency unit tests (5 tests)
│       │   ├── CoursePromotionTest.java         # Drop & Skip-and-Retain unit tests (4 tests)
│       │   ├── CourseDropsTest.java             # Repeated drop & non-member drop tests (4 tests)
│       │   ├── CourseCatalogTest.java           # Multi-course catalog & state isolation tests (4 tests)
│       │   ├── CourseConcurrencyTest.java       # Virtual threads & barrier concurrency tests (3 tests)
│       │   └── bdd/
│       │       └── CourseWaitlistBddTest.java   # 9 BDD scenario test executions
│       └── resources/features/
│           └── course_waitlist.feature          # Gherkin BDD specification
```

---

## 4. Acceptance Criteria Verification Matrix

| # | Criterion | Implementation Detail | Test Verification |
| :--- | :--- | :--- | :--- |
| **1** | **Enrollment succeeds** with capacity and prerequisites | Prerequisite checking against `learner.completedCourses` under lock | `CourseEnrollmentTest`, BDD `@enrollment` |
| **2** | **Full capacity triggers FIFO waitlist once** without duplicates | Enqueues eligible learner; rejects duplicates idempotently | `CourseWaitlistTest`, BDD `@waitlist` |
| **3** | **Drop opens seat & promotes earliest eligible learner** | Removes learner and promotes head of waitlist | `CoursePromotionTest`, BDD `@drop` |
| **4** | **Skip-and-Retain policy applied consistently** | Ineligible waitlisted learner is skipped during promotion but retains seniority | `CoursePromotionTest`, BDD `@promotion` |
| **5** | **Invalid requests & duplicate drops handled safely** | Returns clear `NOT_ENROLLED` without modifying state or leaking seats | `CourseDropsTest`, BDD `@edge-cases` |
| **6** | **High concurrency & virtual threads verification** | Multi-threaded barrier tests & Java 25 virtual thread stress tests | `CourseConcurrencyTest` (100 virtual threads) |
| **7** | **AI Engineering Prompt & Audit Record** | Complete prompt trail, decisions, edits, and verification evidence | `AI_USE_RECORD.md` |

---

## 5. Invariants Maintained

1. **Capacity Invariant**: $0 \le |\text{enrolled\_learners}| \le \text{capacity}$ at all times.
2. **Mutual Exclusion Invariant**: $\text{enrolled\_learners} \cap \text{waitlist} = \emptyset$.
3. **Queue Integrity Invariant**: The waitlist strictly preserves FIFO placement order.
4. **Prerequisite Invariant**: A learner cannot enter `enrolled_learners` unless prerequisites are met.

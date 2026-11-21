package com.kivo.waitlist.service;

import com.kivo.waitlist.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe business service managing enrollment, drop, and FIFO waitlist promotion.
 * Supports multiple course offerings with dynamic catalog selection.
 */
@Service
public class CourseWaitlistService {

    public static class CourseOfferingState {
        private CourseOffering offering;
        private final List<String> enrolled;
        private final List<String> waitlist;

        public CourseOfferingState(CourseOffering offering, List<String> enrolled, List<String> waitlist) {
            this.offering = Objects.requireNonNull(offering, "Offering cannot be null");
            this.enrolled = enrolled != null ? enrolled : new ArrayList<>();
            this.waitlist = waitlist != null ? waitlist : new ArrayList<>();
        }

        public CourseOffering getOffering() { return offering; }
        public void setOffering(CourseOffering offering) { this.offering = offering; }
        public List<String> getEnrolled() { return enrolled; }
        public List<String> getWaitlist() { return waitlist; }
    }

    private CourseOffering offering;
    private final PromotionPolicy promotionPolicy;

    private final ReentrantLock lock = new ReentrantLock(true); // Fair lock for strict FIFO fairness

    private final Map<String, Learner> learners = new ConcurrentHashMap<>();
    private final List<String> enrolled = new ArrayList<>();
    private final List<String> waitlist = new ArrayList<>();

    private final Map<String, CourseOfferingState> catalog = new LinkedHashMap<>();

    public CourseWaitlistService() {
        this(
                new CourseOffering("CS201", "Data Structures & Algorithms", 2, Set.of("CS101")),
                PromotionPolicy.SKIP_AND_RETAIN
        );
        initDefaultCatalog();
        initDefaultLearners();
    }

    public CourseWaitlistService(CourseOffering offering) {
        this(offering, PromotionPolicy.SKIP_AND_RETAIN);
    }

    public CourseWaitlistService(CourseOffering offering, PromotionPolicy promotionPolicy) {
        this.offering = Objects.requireNonNull(offering, "Offering must not be null.");
        this.promotionPolicy = Objects.requireNonNull(promotionPolicy, "PromotionPolicy must not be null.");
        this.catalog.put(offering.getId(), new CourseOfferingState(offering, new ArrayList<>(), new ArrayList<>()));
    }

    private void syncActiveStateToCatalog() {
        if (offering != null) {
            CourseOfferingState state = catalog.get(offering.getId());
            if (state != null) {
                state.getEnrolled().clear();
                state.getEnrolled().addAll(this.enrolled);
                state.getWaitlist().clear();
                state.getWaitlist().addAll(this.waitlist);
            }
        }
    }

    private void initDefaultCatalog() {
        registerOffering(new CourseOffering("CS201", "Data Structures & Algorithms", 2, Set.of("CS101")));
        registerOffering(new CourseOffering("CS301", "Advanced Algorithms & Systems", 3, Set.of("CS201", "CS101")));
        registerOffering(new CourseOffering("AI101", "Artificial Intelligence & ML", 2, Set.of("CS101", "MATH101")));
        registerOffering(new CourseOffering("MATH201", "Linear Algebra & Calculus", 2, Set.of("MATH101")));
        registerOffering(new CourseOffering("WEB101", "Fullstack Web Systems", 3, Collections.emptySet()));
    }

    private void initDefaultLearners() {
        registerLearner(new Learner("alice", "Alice Chen", Set.of("CS101", "CS201")));
        registerLearner(new Learner("bob", "Bob Smith", Set.of("CS101", "MATH101")));
        registerLearner(new Learner("carol", "Carol Danvers", Collections.emptySet()));
        registerLearner(new Learner("david", "David Miller", Set.of("CS101")));
        registerLearner(new Learner("eve", "Eve Johnson", Set.of("CS101", "MATH101", "CS201")));
        registerLearner(new Learner("frank", "Frank Wright", Collections.emptySet()));
    }

    public void registerLearner(Learner learner) {
        if (learner == null) {
            throw new IllegalArgumentException("Learner cannot be null.");
        }
        learners.put(learner.getId(), learner);
    }

    public Optional<Learner> getLearner(String learnerId) {
        if (learnerId == null) return Optional.empty();
        return Optional.ofNullable(learners.get(learnerId.trim()));
    }

    public Collection<Learner> getAllLearners() {
        return Collections.unmodifiableCollection(learners.values());
    }

    public CourseOffering getOffering() {
        return offering;
    }

    public CourseOffering getOffering(String offeringId) {
        lock.lock();
        try {
            CourseOfferingState state = catalog.get(offeringId);
            return state != null ? state.getOffering() : null;
        } finally {
            lock.unlock();
        }
    }

    public String getActiveOfferingId() {
        return offering != null ? offering.getId() : null;
    }

    public void switchOffering(String offeringId) {
        if (offeringId == null || offeringId.trim().isEmpty()) {
            throw new IllegalArgumentException("Offering ID must be provided.");
        }
        lock.lock();
        try {
            String cleanId = offeringId.trim();
            CourseOfferingState target = catalog.get(cleanId);
            if (target == null) {
                throw new IllegalArgumentException("Course offering '" + cleanId + "' not found in catalog.");
            }
            if (offering != null && offering.getId().equals(cleanId)) {
                return;
            }
            // Save current offering state into catalog
            syncActiveStateToCatalog();

            // Switch to target offering
            this.offering = target.getOffering();
            this.enrolled.clear();
            this.enrolled.addAll(target.getEnrolled());
            this.waitlist.clear();
            this.waitlist.addAll(target.getWaitlist());
        } finally {
            lock.unlock();
        }
    }

    public void registerOffering(CourseOffering newOffering) {
        if (newOffering == null) {
            throw new IllegalArgumentException("Offering cannot be null.");
        }
        lock.lock();
        try {
            if (offering != null && offering.getId().equals(newOffering.getId())) {
                this.offering = newOffering;
            }
            CourseOfferingState existing = catalog.get(newOffering.getId());
            if (existing != null) {
                existing.setOffering(newOffering);
            } else {
                catalog.put(newOffering.getId(), new CourseOfferingState(newOffering, new ArrayList<>(), new ArrayList<>()));
            }
        } finally {
            lock.unlock();
        }
    }

    public List<OfferingSnapshot> getAllOfferings() {
        lock.lock();
        try {
            syncActiveStateToCatalog();
            return catalog.keySet().stream()
                    .map(this::getSnapshot)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public PromotionPolicy getPromotionPolicy() {
        return promotionPolicy;
    }

    // ── Core Enrollment Method ────────────────────────────────────────────────

    public EnrollmentResult enroll(String learnerId) {
        return enroll(null, learnerId);
    }

    public EnrollmentResult enroll(String offeringId, String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) {
            return EnrollmentResult.invalidRequest(
                    offering != null ? offering.getId() : null,
                    null,
                    "Learner ID must be a non-empty string."
            );
        }

        String cleanId = learnerId.trim();

        lock.lock();
        try {
            if (offeringId != null && !offeringId.trim().isEmpty()) {
                String cleanOfferingId = offeringId.trim();
                if (!catalog.containsKey(cleanOfferingId)) {
                    return EnrollmentResult.invalidRequest(
                            cleanOfferingId,
                            cleanId,
                            "Course offering '" + cleanOfferingId + "' not found in catalog."
                    );
                }
                if (!cleanOfferingId.equals(offering.getId())) {
                    switchOffering(cleanOfferingId);
                }
            }

            Learner learner = learners.get(cleanId);
            if (learner == null) {
                return EnrollmentResult.invalidRequest(
                        offering.getId(),
                        cleanId,
                        "Learner '" + cleanId + "' is not registered in the system."
                );
            }

            // 1. Idempotency check: Already enrolled
            if (enrolled.contains(cleanId)) {
                return EnrollmentResult.alreadyEnrolled(offering.getId(), cleanId, offering.getTitle());
            }

            // 2. Idempotency check: Already waitlisted
            if (waitlist.contains(cleanId)) {
                int position = waitlist.indexOf(cleanId) + 1;
                return EnrollmentResult.alreadyWaitlisted(offering.getId(), cleanId, position);
            }

            boolean isEligible = learner.hasPrerequisites(offering.getPrerequisites());

            // 3. Case A: Available Capacity
            if (enrolled.size() < offering.getCapacity()) {
                if (!isEligible) {
                    String missing = getMissingPrerequisites(learner, offering);
                    return EnrollmentResult.prerequisiteNotMet(offering.getId(), cleanId, missing, false);
                }

                enrolled.add(cleanId);
                syncActiveStateToCatalog();
                return EnrollmentResult.enrolled(offering.getId(), cleanId, offering.getTitle());
            }

            // 4. Case B: Full Capacity -> Waitlist
            if (!isEligible) {
                String missing = getMissingPrerequisites(learner, offering);
                return EnrollmentResult.prerequisiteNotMet(offering.getId(), cleanId, missing, true);
            }

            waitlist.add(cleanId);
            syncActiveStateToCatalog();
            int position = waitlist.size();
            return EnrollmentResult.waitlisted(offering.getId(), cleanId, offering.getCapacity(), position);

        } finally {
            lock.unlock();
        }
    }

    /**
     * Queues a learner directly onto the waitlist with pending prerequisite verification
     * (used to test and verify the Skip-and-Retain promotion policy without synthetic reflection).
     */
    public EnrollmentResult queueWaitlistPending(String learnerId) {
        return queueWaitlistPending(null, learnerId);
    }

    public EnrollmentResult queueWaitlistPending(String offeringId, String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) {
            return EnrollmentResult.invalidRequest(
                    offering != null ? offering.getId() : null,
                    null,
                    "Learner ID must be a non-empty string."
            );
        }

        String cleanId = learnerId.trim();

        lock.lock();
        try {
            if (offeringId != null && !offeringId.trim().isEmpty()) {
                String cleanOfferingId = offeringId.trim();
                if (!catalog.containsKey(cleanOfferingId)) {
                    return EnrollmentResult.invalidRequest(
                            cleanOfferingId,
                            cleanId,
                            "Course offering '" + cleanOfferingId + "' not found in catalog."
                    );
                }
                if (!cleanOfferingId.equals(offering.getId())) {
                    switchOffering(cleanOfferingId);
                }
            }

            Learner learner = learners.get(cleanId);
            if (learner == null) {
                return EnrollmentResult.invalidRequest(
                        offering.getId(),
                        cleanId,
                        "Learner '" + cleanId + "' is not registered in the system."
                );
            }

            if (enrolled.contains(cleanId)) {
                return EnrollmentResult.alreadyEnrolled(offering.getId(), cleanId, offering.getTitle());
            }

            if (waitlist.contains(cleanId)) {
                int position = waitlist.indexOf(cleanId) + 1;
                return EnrollmentResult.alreadyWaitlisted(offering.getId(), cleanId, position);
            }

            waitlist.add(cleanId);
            syncActiveStateToCatalog();
            int position = waitlist.size();
            return EnrollmentResult.waitlisted(offering.getId(), cleanId, offering.getCapacity(), position);

        } finally {
            lock.unlock();
        }
    }

    // ── Core Drop Method ──────────────────────────────────────────────────────

    public DropResult drop(String learnerId) {
        return drop(null, learnerId);
    }

    public DropResult drop(String offeringId, String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) {
            return DropResult.invalidRequest("Learner ID must be a non-empty string.");
        }

        String cleanId = learnerId.trim();

        lock.lock();
        try {
            if (offeringId != null && !offeringId.trim().isEmpty()) {
                String cleanOfferingId = offeringId.trim();
                if (!catalog.containsKey(cleanOfferingId)) {
                    return DropResult.invalidRequest("Course offering '" + cleanOfferingId + "' not found in catalog.");
                }
                if (!cleanOfferingId.equals(offering.getId())) {
                    switchOffering(cleanOfferingId);
                }
            }

            // Case 1: Dropping from waitlist
            if (waitlist.contains(cleanId)) {
                waitlist.remove(cleanId);
                syncActiveStateToCatalog();
                return DropResult.droppedFromWaitlist(cleanId);
            }

            // Case 2: Neither enrolled nor waitlisted
            if (!enrolled.contains(cleanId)) {
                return DropResult.notEnrolled(cleanId, offering.getTitle());
            }

            // Case 3: Dropping an enrolled learner -> seat opens
            enrolled.remove(cleanId);

            // FIFO Promotion Scan
            String promotedId = promoteNextEligible();
            syncActiveStateToCatalog();

            if (promotedId != null) {
                return DropResult.droppedAndPromoted(cleanId, promotedId);
            }

            return DropResult.droppedSeatOpened(cleanId);

        } finally {
            lock.unlock();
        }
    }

    private String promoteNextEligible() {
        for (int i = 0; i < waitlist.size(); i++) {
            String candidateId = waitlist.get(i);
            Learner candidate = learners.get(candidateId);

            if (candidate != null && candidate.hasPrerequisites(offering.getPrerequisites())) {
                // Found earliest eligible candidate
                waitlist.remove(i);
                enrolled.add(candidateId);
                return candidateId;
            }
            // If ineligible under SKIP_AND_RETAIN: continue scan, leaving candidate in queue
        }
        return null;
    }

    private String getMissingPrerequisites(Learner learner, CourseOffering offering) {
        Set<String> missing = new TreeSet<>(offering.getPrerequisites());
        missing.removeAll(learner.getCompletedCourses());
        return String.join(", ", missing);
    }

    // ── Inspection & Management ───────────────────────────────────────────────

    public OfferingSnapshot getSnapshot() {
        lock.lock();
        try {
            List<String> enrolledCopy = new ArrayList<>(enrolled);
            List<String> waitlistCopy = new ArrayList<>(waitlist);
            int available = Math.max(0, offering.getCapacity() - enrolledCopy.size());

            return new OfferingSnapshot(
                    offering.getId(),
                    offering.getTitle(),
                    offering.getCapacity(),
                    enrolledCopy.size(),
                    available,
                    enrolledCopy,
                    waitlistCopy,
                    new ArrayList<>(offering.getPrerequisites())
            );
        } finally {
            lock.unlock();
        }
    }

    public OfferingSnapshot getSnapshot(String offeringId) {
        lock.lock();
        try {
            if (offeringId == null || offeringId.trim().isEmpty() || (offering != null && offering.getId().equals(offeringId.trim()))) {
                return getSnapshot();
            }
            CourseOfferingState state = catalog.get(offeringId.trim());
            if (state == null) {
                throw new NoSuchElementException("Course offering '" + offeringId + "' not found.");
            }
            CourseOffering off = state.getOffering();
            List<String> enrolledCopy = new ArrayList<>(state.getEnrolled());
            List<String> waitlistCopy = new ArrayList<>(state.getWaitlist());
            int available = Math.max(0, off.getCapacity() - enrolledCopy.size());

            return new OfferingSnapshot(
                    off.getId(),
                    off.getTitle(),
                    off.getCapacity(),
                    enrolledCopy.size(),
                    available,
                    enrolledCopy,
                    waitlistCopy,
                    new ArrayList<>(off.getPrerequisites())
            );
        } finally {
            lock.unlock();
        }
    }

    public void reset(CourseOffering newOffering) {
        lock.lock();
        try {
            this.enrolled.clear();
            this.waitlist.clear();
            if (newOffering != null) {
                this.offering = newOffering;
                CourseOfferingState state = catalog.get(newOffering.getId());
                if (state != null) {
                    state.setOffering(newOffering);
                    state.getEnrolled().clear();
                    state.getWaitlist().clear();
                } else {
                    catalog.put(newOffering.getId(), new CourseOfferingState(newOffering, new ArrayList<>(), new ArrayList<>()));
                }
            } else if (this.offering != null) {
                CourseOfferingState state = catalog.get(this.offering.getId());
                if (state != null) {
                    state.getEnrolled().clear();
                    state.getWaitlist().clear();
                }
            }
            initDefaultLearners();
        } finally {
            lock.unlock();
        }
    }
}

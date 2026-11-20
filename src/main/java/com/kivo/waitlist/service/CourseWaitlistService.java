package com.kivo.waitlist.service;

import com.kivo.waitlist.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe business service managing enrollment, drop, and FIFO waitlist promotion.
 */
@Service
public class CourseWaitlistService {

    private CourseOffering offering;
    private final PromotionPolicy promotionPolicy;

    private final ReentrantLock lock = new ReentrantLock(true); // Fair lock for strict FIFO fairness

    private final Map<String, Learner> learners = new ConcurrentHashMap<>();
    private final List<String> enrolled = new ArrayList<>();
    private final List<String> waitlist = new ArrayList<>();

    public CourseWaitlistService() {
        this(
                new CourseOffering("CS201", "Data Structures & Algorithms", 2, Set.of("CS101")),
                PromotionPolicy.SKIP_AND_RETAIN
        );
        initDefaultLearners();
    }

    public CourseWaitlistService(CourseOffering offering) {
        this(offering, PromotionPolicy.SKIP_AND_RETAIN);
    }

    public CourseWaitlistService(CourseOffering offering, PromotionPolicy promotionPolicy) {
        this.offering = Objects.requireNonNull(offering, "Offering must not be null.");
        this.promotionPolicy = Objects.requireNonNull(promotionPolicy, "PromotionPolicy must not be null.");
    }

    private void initDefaultLearners() {
        registerLearner(new Learner("alice", "Alice Chen", Set.of("CS101")));
        registerLearner(new Learner("bob", "Bob Smith", Set.of("CS101")));
        registerLearner(new Learner("carol", "Carol Danvers", Collections.emptySet())); // Missing CS101
        registerLearner(new Learner("david", "David Miller", Set.of("CS101")));
        registerLearner(new Learner("eve", "Eve Johnson", Set.of("CS101")));
        registerLearner(new Learner("frank", "Frank Wright", Collections.emptySet())); // Missing CS101
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

    public PromotionPolicy getPromotionPolicy() {
        return promotionPolicy;
    }

    // ── Core Enrollment Method ────────────────────────────────────────────────

    public EnrollmentResult enroll(String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) {
            return EnrollmentResult.invalidRequest(
                    offering.getId(),
                    null,
                    "Learner ID must be a non-empty string."
            );
        }

        String cleanId = learnerId.trim();

        lock.lock();
        try {
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
                    String missing = getMissingPrerequisites(learner);
                    return EnrollmentResult.prerequisiteNotMet(offering.getId(), cleanId, missing, false);
                }

                enrolled.add(cleanId);
                return EnrollmentResult.enrolled(offering.getId(), cleanId, offering.getTitle());
            }

            // 4. Case B: Full Capacity -> Waitlist
            if (!isEligible) {
                String missing = getMissingPrerequisites(learner);
                return EnrollmentResult.prerequisiteNotMet(offering.getId(), cleanId, missing, true);
            }

            waitlist.add(cleanId);
            int position = waitlist.size();
            return EnrollmentResult.waitlisted(offering.getId(), cleanId, offering.getCapacity(), position);

        } finally {
            lock.unlock();
        }
    }

    // ── Core Drop Method ──────────────────────────────────────────────────────

    public DropResult drop(String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) {
            return DropResult.invalidRequest("Learner ID must be a non-empty string.");
        }

        String cleanId = learnerId.trim();

        lock.lock();
        try {
            // Case 1: Dropping from waitlist
            if (waitlist.contains(cleanId)) {
                waitlist.remove(cleanId);
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

    private String getMissingPrerequisites(Learner learner) {
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
                    waitlistCopy
            );
        } finally {
            lock.unlock();
        }
    }

    public void reset(CourseOffering newOffering) {
        lock.lock();
        try {
            if (newOffering != null) {
                this.offering = newOffering;
            }
            this.enrolled.clear();
            this.waitlist.clear();
        } finally {
            lock.unlock();
        }
    }
}

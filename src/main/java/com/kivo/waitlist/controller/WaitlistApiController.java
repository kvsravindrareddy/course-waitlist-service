package com.kivo.waitlist.controller;

import com.kivo.waitlist.api.WaitlistApi;
import com.kivo.waitlist.model.gen.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST API Controller implementing the OpenAPI-generated WaitlistApi interface.
 */
@RestController
@CrossOrigin(origins = "*")
public class WaitlistApiController implements WaitlistApi {

    private final CourseWaitlistService waitlistService;

    public WaitlistApiController(CourseWaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @Override
    public ResponseEntity<OfferingSnapshot> getOfferingSnapshot() {
        com.kivo.waitlist.model.OfferingSnapshot domainSnap = waitlistService.getSnapshot();
        return ResponseEntity.ok(mapSnapshot(domainSnap));
    }

    @Override
    public ResponseEntity<List<OfferingSnapshot>> getAllOfferings() {
        List<OfferingSnapshot> list = waitlistService.getAllOfferings().stream()
                .map(this::mapSnapshot)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<OfferingSnapshot> createOffering(CreateOfferingRequest request) {
        Set<String> prereqs = request.getPrerequisites() != null
                ? new HashSet<>(request.getPrerequisites())
                : Collections.emptySet();
        com.kivo.waitlist.model.CourseOffering newOffering = new com.kivo.waitlist.model.CourseOffering(
                request.getId(),
                request.getTitle(),
                request.getCapacity(),
                prereqs
        );
        waitlistService.registerOffering(newOffering);
        waitlistService.switchOffering(newOffering.getId());
        return ResponseEntity.ok(mapSnapshot(waitlistService.getSnapshot()));
    }

    @Override
    public ResponseEntity<OfferingSnapshot> switchOffering(SwitchOfferingRequest request) {
        waitlistService.switchOffering(request.getOfferingId());
        return ResponseEntity.ok(mapSnapshot(waitlistService.getSnapshot()));
    }

    @Override
    public ResponseEntity<List<Learner>> getAllLearners() {
        List<Learner> list = waitlistService.getAllLearners().stream()
                .map(this::mapLearner)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<Learner> registerLearner(RegisterLearnerRequest request) {
        Set<String> courses = request.getCompletedCourses() != null
                ? new HashSet<>(request.getCompletedCourses())
                : Set.of();
        com.kivo.waitlist.model.Learner domainLearner = new com.kivo.waitlist.model.Learner(
                request.getId(),
                request.getName(),
                courses
        );
        waitlistService.registerLearner(domainLearner);
        return ResponseEntity.ok(mapLearner(domainLearner));
    }

    @Override
    public ResponseEntity<EnrollmentResult> enrollLearner(EnrollRequest request) {
        com.kivo.waitlist.model.EnrollmentResult domainRes = waitlistService.enroll(
                request.getOfferingId(),
                request.getLearnerId()
        );
        EnrollmentResult apiRes = new EnrollmentResult()
                .status(EnrollmentStatus.fromValue(domainRes.getStatus().name()))
                .message(domainRes.getMessage())
                .waitlistPosition(domainRes.getWaitlistPosition())
                .offeringId(domainRes.getOfferingId())
                .learnerId(domainRes.getLearnerId());
        return ResponseEntity.ok(apiRes);
    }

    @Override
    public ResponseEntity<DropResult> dropLearner(DropRequest request) {
        com.kivo.waitlist.model.DropResult domainRes = waitlistService.drop(
                request.getOfferingId(),
                request.getLearnerId()
        );
        DropResult apiRes = new DropResult()
                .status(DropStatus.fromValue(domainRes.getStatus().name()))
                .message(domainRes.getMessage())
                .droppedLearnerId(domainRes.getDroppedLearnerId())
                .promotedLearnerId(domainRes.getPromotedLearnerId())
                .seatOpened(domainRes.isSeatOpened());
        return ResponseEntity.ok(apiRes);
    }

    @Override
    public ResponseEntity<OfferingSnapshot> resetOffering() {
        waitlistService.reset(new com.kivo.waitlist.model.CourseOffering(
                "CS201", "Data Structures & Algorithms", 2, Set.of("CS101")
        ));
        return ResponseEntity.ok(mapSnapshot(waitlistService.getSnapshot()));
    }

    @Override
    public ResponseEntity<EnrollmentResult> queueWaitlistPending(EnrollRequest request) {
        com.kivo.waitlist.model.EnrollmentResult domainRes = waitlistService.queueWaitlistPending(
                request.getOfferingId(),
                request.getLearnerId()
        );
        EnrollmentResult apiRes = new EnrollmentResult()
                .status(EnrollmentStatus.fromValue(domainRes.getStatus().name()))
                .message(domainRes.getMessage())
                .waitlistPosition(domainRes.getWaitlistPosition())
                .offeringId(domainRes.getOfferingId())
                .learnerId(domainRes.getLearnerId());
        return ResponseEntity.ok(apiRes);
    }

    private OfferingSnapshot mapSnapshot(com.kivo.waitlist.model.OfferingSnapshot domain) {
        return new OfferingSnapshot()
                .offeringId(domain.getOfferingId())
                .title(domain.getTitle())
                .capacity(domain.getCapacity())
                .enrolledCount(domain.getEnrolledCount())
                .availableSeats(domain.getAvailableSeats())
                .enrolledLearners(domain.getEnrolledLearners())
                .waitlist(domain.getWaitlist())
                .prerequisites(new ArrayList<>(domain.getPrerequisites()));
    }

    private Learner mapLearner(com.kivo.waitlist.model.Learner domain) {
        return new Learner()
                .id(domain.getId())
                .name(domain.getName())
                .completedCourses(new ArrayList<>(domain.getCompletedCourses()));
    }
}

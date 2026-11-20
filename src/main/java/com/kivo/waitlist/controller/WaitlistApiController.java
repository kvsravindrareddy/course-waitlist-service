package com.kivo.waitlist.controller;

import com.kivo.waitlist.dto.DropRequest;
import com.kivo.waitlist.dto.EnrollRequest;
import com.kivo.waitlist.dto.RegisterLearnerRequest;
import com.kivo.waitlist.model.*;
import com.kivo.waitlist.service.CourseWaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class WaitlistApiController {

    private final CourseWaitlistService waitlistService;

    public WaitlistApiController(CourseWaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @GetMapping("/offering")
    public ResponseEntity<OfferingSnapshot> getOfferingSnapshot() {
        return ResponseEntity.ok(waitlistService.getSnapshot());
    }

    @GetMapping("/learners")
    public ResponseEntity<Collection<Learner>> getAllLearners() {
        return ResponseEntity.ok(waitlistService.getAllLearners());
    }

    @PostMapping("/learners")
    public ResponseEntity<Learner> registerLearner(@Valid @RequestBody RegisterLearnerRequest request) {
        Learner learner = new Learner(
                request.getId(),
                request.getName(),
                request.getCompletedCourses()
        );
        waitlistService.registerLearner(learner);
        return ResponseEntity.ok(learner);
    }

    @PostMapping("/enroll")
    public ResponseEntity<EnrollmentResult> enroll(@Valid @RequestBody EnrollRequest request) {
        EnrollmentResult result = waitlistService.enroll(request.getLearnerId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/drop")
    public ResponseEntity<DropResult> drop(@Valid @RequestBody DropRequest request) {
        DropResult result = waitlistService.drop(request.getLearnerId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reset")
    public ResponseEntity<OfferingSnapshot> reset() {
        waitlistService.reset(new CourseOffering("CS201", "Data Structures & Algorithms", 2, java.util.Set.of("CS101")));
        return ResponseEntity.ok(waitlistService.getSnapshot());
    }
}

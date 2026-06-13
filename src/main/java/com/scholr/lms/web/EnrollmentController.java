package com.scholr.lms.web;

import java.util.UUID;

import com.scholr.lms.enrollment.EnrollmentService;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.Enrollment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollment;

    public EnrollmentController(EnrollmentService enrollment) {
        this.enrollment = enrollment;
    }

    public record CreateCohort(UUID courseId, int capacity) {
    }

    public record CohortView(UUID id, UUID courseId, int capacity, int seatsRemaining) {
    }

    @PostMapping("/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    public CohortView createCohort(@RequestBody CreateCohort request) {
        Cohort cohort = enrollment.createCohort(request.courseId(), request.capacity());
        return new CohortView(cohort.id(), cohort.courseId(), cohort.capacity(), cohort.seatsRemaining());
    }

    public record EnrollRequest(UUID learnerId) {
    }

    public record EnrollmentView(UUID id, UUID cohortId, UUID learnerId) {
    }

    /** Idempotent: re-POSTing the same learner returns the existing enrollment. */
    @PostMapping("/cohorts/{cohortId}/enrollments")
    public EnrollmentView enroll(@PathVariable UUID cohortId, @RequestBody EnrollRequest request) {
        Enrollment result = enrollment.enroll(cohortId, request.learnerId());
        return new EnrollmentView(result.id(), result.cohortId(), result.learnerId());
    }
}

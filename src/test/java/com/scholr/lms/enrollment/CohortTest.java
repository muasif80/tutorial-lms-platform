package com.scholr.lms.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.scholr.lms.catalog.domain.CourseId;
import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.CohortFullException;
import com.scholr.lms.enrollment.domain.CohortId;
import com.scholr.lms.enrollment.domain.LearnerId;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.Test;

class CohortTest {

    private Cohort cohortWithCapacity(int capacity) {
        return new Cohort(CohortId.random(), TenantId.random(), CourseId.random(), capacity);
    }

    @Test
    void enrolls_learners_up_to_capacity() {
        Cohort cohort = cohortWithCapacity(2);

        cohort.enroll(LearnerId.random());
        cohort.enroll(LearnerId.random());

        assertEquals(0, cohort.seatsRemaining());
        assertEquals(2, cohort.enrolledCount());
    }

    @Test
    void never_oversells_a_cohort() {
        Cohort cohort = cohortWithCapacity(1);
        cohort.enroll(LearnerId.random());

        assertThrows(CohortFullException.class, () -> cohort.enroll(LearnerId.random()));
    }
}

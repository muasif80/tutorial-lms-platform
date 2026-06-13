package com.scholr.lms.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.scholr.lms.enrollment.domain.Cohort;
import com.scholr.lms.enrollment.domain.CohortFullException;
import org.junit.jupiter.api.Test;

/** The seat invariant from Part 1, carried forward onto the now-persistent aggregate. */
class CohortTest {

    private Cohort cohortWithCapacity(int capacity) {
        return Cohort.create(UUID.randomUUID(), capacity);
    }

    @Test
    void enrolls_learners_up_to_capacity() {
        Cohort cohort = cohortWithCapacity(2);

        cohort.enroll(UUID.randomUUID());
        cohort.enroll(UUID.randomUUID());

        assertEquals(0, cohort.seatsRemaining());
        assertEquals(2, cohort.enrolledCount());
    }

    @Test
    void never_oversells_a_cohort() {
        Cohort cohort = cohortWithCapacity(1);
        cohort.enroll(UUID.randomUUID());

        assertThrows(CohortFullException.class, () -> cohort.enroll(UUID.randomUUID()));
    }
}

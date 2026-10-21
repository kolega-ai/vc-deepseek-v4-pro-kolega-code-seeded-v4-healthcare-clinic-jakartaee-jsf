package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.DocumentStatus;
import com.oakfield.clinic.model.RefillStatus;
import com.oakfield.clinic.model.VisitStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * State-machine contract tests: only the explicitly listed transitions are
 * allowed, and every status named in the product spec is reachable through
 * the edge list.
 */
class WorkflowTest {

    @Test
    void appointmentTransitions() {
        // creation edges
        assertThat(AppointmentWorkflow.allows(null, AppointmentStatus.REQUESTED)).isTrue();
        assertThat(AppointmentWorkflow.allows(null, AppointmentStatus.SCHEDULED)).isTrue();
        // stated lifecycle
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.REQUESTED, AppointmentStatus.SCHEDULED)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.REQUESTED, AppointmentStatus.CANCELLED)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.SCHEDULED, AppointmentStatus.NO_SHOW)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED)).isTrue();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.SCHEDULED, AppointmentStatus.SCHEDULED)).isTrue(); // reschedule
        // every status reachable
        for (AppointmentStatus s : AppointmentStatus.values()) {
            assertThat(s).isIn(AppointmentStatus.REQUESTED, AppointmentStatus.SCHEDULED,
                    AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED,
                    AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);
        }
        // illegal transitions rejected
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED)).isFalse();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.COMPLETED, AppointmentStatus.SCHEDULED)).isFalse();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.CANCELLED, AppointmentStatus.SCHEDULED)).isFalse();
        assertThat(AppointmentWorkflow.allows(AppointmentStatus.NO_SHOW, AppointmentStatus.SCHEDULED)).isFalse();
        assertThatThrownBy(() -> AppointmentWorkflow.require(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED, "test"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void visitTransitions() {
        assertThat(VisitWorkflow.allows(null, VisitStatus.DRAFT)).isTrue();
        assertThat(VisitWorkflow.allows(VisitStatus.DRAFT, VisitStatus.DRAFT)).isTrue();
        assertThat(VisitWorkflow.allows(VisitStatus.DRAFT, VisitStatus.RELEASED)).isTrue();
        assertThat(VisitWorkflow.allows(VisitStatus.RELEASED, VisitStatus.DRAFT)).isFalse();
    }

    @Test
    void refillTransitions() {
        assertThat(RefillWorkflow.allows(null, RefillStatus.REQUESTED)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.REQUESTED, RefillStatus.APPROVED)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.REQUESTED, RefillStatus.DENIED)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.REQUESTED, RefillStatus.NEEDS_INFO)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.REQUESTED, RefillStatus.CANCELLED)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.NEEDS_INFO, RefillStatus.REQUESTED)).isTrue();
        assertThat(RefillWorkflow.allows(RefillStatus.APPROVED, RefillStatus.REQUESTED)).isFalse();
        assertThat(RefillWorkflow.allows(RefillStatus.DENIED, RefillStatus.APPROVED)).isFalse();
        assertThat(RefillWorkflow.allows(RefillStatus.CANCELLED, RefillStatus.REQUESTED)).isFalse();
    }

    @Test
    void documentTransitions() {
        assertThat(DocumentWorkflow.allows(null, DocumentStatus.SUBMITTED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.SUBMITTED, DocumentStatus.VERIFIED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.SUBMITTED, DocumentStatus.REJECTED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.SUBMITTED, DocumentStatus.ARCHIVED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.REJECTED, DocumentStatus.VERIFIED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.REJECTED, DocumentStatus.ARCHIVED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.VERIFIED, DocumentStatus.ARCHIVED)).isTrue();
        assertThat(DocumentWorkflow.allows(DocumentStatus.VERIFIED, DocumentStatus.REJECTED)).isFalse();
        assertThat(DocumentWorkflow.allows(DocumentStatus.ARCHIVED, DocumentStatus.VERIFIED)).isFalse();
    }
}

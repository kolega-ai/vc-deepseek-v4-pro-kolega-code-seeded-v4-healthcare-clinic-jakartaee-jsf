package com.oakfield.clinic.decision;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authorization context tests: scope enforcement, limit resolution, and the
 * role/scope boundaries that keep patients out of staff actions and front
 * desk out of clinical decisions.
 */
class DecisionContextTest {

    private static DecisionContext ctx(String role, Set<String> scopes, Map<String, String> limits) {
        return new DecisionContext(1L, "a@b.test", "Test User", role, scopes, limits, 1, "tok", "127.0.0.1", "req");
    }

    @Test
    void patientCannotActAsStaff() {
        DecisionContext patient = ctx("PATIENT", Set.of("patient.read.own", "appointment.book"), Map.of());
        assertThatThrownBy(() -> patient.require("appointment.manage"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> patient.require("visit.write"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> patient.require("refill.decide"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> patient.require("audit.read"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(patient.hasScope("appointment.book")).isTrue();
    }

    @Test
    void frontDeskCannotMakeClinicalDecisions() {
        DecisionContext desk = ctx("FRONT_DESK", Set.of("appointment.manage", "document.verify"), Map.of());
        assertThatThrownBy(() -> desk.require("visit.write"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> desk.require("visit.release"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> desk.require("refill.decide"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(desk.hasScope("appointment.manage")).isTrue();
        assertThat(desk.hasScope("document.verify")).isTrue();
    }

    @Test
    void clinicianHasClinicalScopesButNoAdminOnes() {
        DecisionContext doc = ctx("CLINICIAN", Set.of("visit.write", "visit.release", "refill.decide"), Map.of());
        assertThat(doc.hasScope("visit.write")).isTrue();
        assertThat(doc.hasScope("visit.release")).isTrue();
        assertThat(doc.hasScope("refill.decide")).isTrue();
        assertThatThrownBy(() -> doc.require("user.manage"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> doc.require("integration.manage"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void limitsAreReadFromDecisionData() {
        DecisionContext patient = ctx("PATIENT", Set.of("appointment.book"),
                Map.of("appointment.book.max_future_days", "90"));
        assertThat(patient.limitInt("appointment.book.max_future_days", 30)).isEqualTo(90);
        assertThat(patient.limitInt("appointment.cancel.cutoff_hours", 24)).isEqualTo(24);
        assertThat(patient.limit("upload.max_mb", "5")).isEqualTo("5");
    }

    @Test
    void systemContextHasFullScope() {
        DecisionContext system = DecisionContext.system("job");
        assertThat(system.hasScope("anything.at.all")).isTrue();
        assertThat(system.isSystem()).isTrue();
        assertThat(system.isStaff()).isFalse();
    }

    @Test
    void scopesAndLimitsCodecRoundTrip() {
        Set<String> scopes = Set.of("a.read", "b.write");
        Map<String, String> limits = Map.of("upload.max_mb", "10", "export.max_rows", "5000");
        assertThat(DecisionService.decodeScopes(DecisionService.encodeScopes(scopes))).isEqualTo(scopes);
        assertThat(DecisionService.decodeLimits(DecisionService.encodeLimits(limits))).isEqualTo(limits);
    }
}

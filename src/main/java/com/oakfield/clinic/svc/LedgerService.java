package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.VisitStatus;
import com.oakfield.clinic.model.VisitSummary;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

/**
 * Builds the XML ledger of released visit summaries that partner pharmacies and
 * care coordinators pull for reconciliation.
 */
@Stateless
public class LedgerService {

    @PersistenceContext
    private EntityManager em;

    public String releasedLedgerXml() {
        List<VisitSummary> rows = em.createQuery(
                        "select v from VisitSummary v where v.status = :rel order by v.releasedAt desc",
                        VisitSummary.class)
                .setParameter("rel", VisitStatus.RELEASED)
                .setMaxResults(500)
                .getResultList();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ledger>");
        for (VisitSummary v : rows) {
            xml.append("<release chart=\"").append(v.getPatient().getId())
                    .append("\" provider=\"").append(v.getProvider().getId())
                    .append("\" releasedAt=\"").append(v.getReleasedAt()).append("\">")
                    .append("<diagnosis>").append(v.getDiagnosisText() == null ? "" : v.getDiagnosisText())
                    .append("</diagnosis></release>");
        }
        return xml.append("</ledger>").toString();
    }
}

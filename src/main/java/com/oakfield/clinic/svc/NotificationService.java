package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.AppRole;
import com.oakfield.clinic.model.Notification;
import com.oakfield.clinic.model.NotificationType;
import com.oakfield.clinic.model.User;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;

/** In-app notifications for workflow events. */
@Stateless
public class NotificationService {

    @PersistenceContext
    private EntityManager em;

    public void notify(Long userId, NotificationType type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        em.persist(n);
    }

    /** Notify every active staff user except the actor (e.g. "form submitted"). */
    public void notifyStaff(Long actorUserId, NotificationType type, String title, String body, String link) {
        List<User> staff = em.createQuery(
                        "select u from User u where u.role in :roles and u.status = :status", User.class)
                .setParameter("roles", List.of(AppRole.CLINIC_ADMIN, AppRole.CLINICIAN, AppRole.FRONT_DESK))
                .setParameter("status", com.oakfield.clinic.model.UserStatus.ACTIVE)
                .getResultList();
        for (User u : staff) {
            if (actorUserId != null && u.getId().equals(actorUserId)) continue;
            notify(u.getId(), type, title, body, link);
        }
    }

    public List<Notification> forUser(Long userId, int page, int pageSize) {
        return em.createQuery(
                        "select n from Notification n where n.userId = :uid order by n.createdAt desc", Notification.class)
                .setParameter("uid", userId)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public long unreadCount(Long userId) {
        TypedQuery<Long> q = em.createQuery(
                "select count(n) from Notification n where n.userId = :uid and n.readAt is null", Long.class)
                .setParameter("uid", userId);
        Long c = q.getSingleResult();
        return c == null ? 0 : c;
    }

    public long countForUser(Long userId) {
        return em.createQuery("select count(n) from Notification n where n.userId = :uid", Long.class)
                .setParameter("uid", userId)
                .getSingleResult();
    }

    public void markRead(DecisionContext ctx, long notificationId) {
        Notification n = em.find(Notification.class, notificationId);
        if (n == null) throw new NotFoundException("Notification not found.");
        if (!n.getUserId().equals(ctx.getUserId())) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Notifications belong to their recipient only.");
        }
        n.setReadAt(java.time.LocalDateTime.now());
    }

    public void markAllRead(DecisionContext ctx) {
        em.createQuery("update Notification n set n.readAt = :now where n.userId = :uid and n.readAt is null")
                .setParameter("now", java.time.LocalDateTime.now())
                .setParameter("uid", ctx.getUserId())
                .executeUpdate();
    }
}

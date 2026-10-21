# UI implementation guide

Every page is a Facelets composition using the app template:

```xml
<ui:composition template="/WEB-INF/templates/layout.xhtml"
                xmlns:ui="jakarta.faces.facelets"
                xmlns:h="jakarta.faces.html"
                xmlns:f="jakarta.faces.core">
  <ui:param name="pageTitle" value="Appointments" />
  <ui:define name="title">Appointments</ui:define>
  <ui:define name="breadcrumb">Appointments</ui:define>
  <ui:define name="content">
    <f:metadata><f:viewAction action="#{bean.load()}" /></f:metadata>
    ... page content ...
  </ui:define>
</ui:composition>
```

## Backing bean pattern

```java
package com.oakfield.clinic.channel.web.<area>;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.svc.*;           // services
import com.oakfield.clinic.model.*;        // entities/enums
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;

@Named("appointments")   // unique EL name, kebab/camel ok
@ViewScoped
public class AppointmentsBean implements Serializable {
    @Inject private SessionInfo session;
    @Inject private AppointmentService appointments;

    @PostConstruct
    public void load() { ... }

    public void someAction(long id) {
        try {
            appointments.confirm(session.ctx(), id);
            FacesUtil.info("Confirmed", "The appointment was confirmed.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not confirm", e.getMessage());
        }
    }
}
```

Rules:
- ALWAYS pass `session.ctx()` into every service call. Never build a context yourself.
- Catch `ValidationException` and surface `FacesUtil.error(summary, detail)`. Let AccessDenied/NotAuthenticated/NotFound bubble (global handler shows 403 / login).
- ID params: read with `FacesUtil.paramLong("id", -1)`; call `load()` in `@PostConstruct`.
- For forms: bind `h:inputText`/`h:inputSecret`/`h:selectOneMenu`/`h:selectBooleanCheckbox` to bean fields; validate lengths server-side happens in services, add `maxlength` attributes and `required`/`requiredMessage` for obvious cases. Add `<h:message for="..." styleClass="field-error" />` under required inputs.
- Multi-step forms (booking): one `@ViewScoped` bean with fields + `step` int; buttons post back and call methods.
- After mutating actions, re-`load()` and/or `FacesUtil.redirect(ctxPath + path)`.

## Layout vocabulary (all in app.css — do not invent new classes)

- Page head: `<div class="page-head"><div><h1>…</h1><p class="sub">…</p></div><div class="page-actions">buttons</div></div>`
- Cards: `<section class="card"><div class="card-head"><h2>…</h2>…actions…</div><div class="card-body">…</div><div class="card-foot">…</div></section>`
- Stats: `.stat-grid` > `.stat` > `.stat-chip teal|green|amber|red|blue|violet` + `.stat-value` + `.stat-label`
- Grids: `.grid-2`, `.grid-3`, `.stack`, `.row-between`, `.cluster`
- Forms: `.form-grid` (2 col) > `.field` (`--wide` spans both) > label + `.input`/`.select`/`.textarea` + `.help` + `.field-error`; submit row: `<div style="margin-top: 16px;">` with `.btn`
- Filter row: `.filter-bar` containing `.field` (small) + a submit `.btn`; labels required for a11y
- Tables: `.table-wrap` > `<table class="data">` with `thead/th` + `tbody` + `<ui:repeat>`; row actions in `.row-actions` with `.btn .btn--ghost .btn--sm`
- Badges: `<span class="#{ux.appointmentBadge(a.status)}">#{ux.appointmentLabel(a.status)}</span>` (also refillBadge/refillLabel, docBadge/docLabel, visitBadge, userStatusBadge, jobBadge, deliveryBadge). NEVER a bare colored dot.
- Empty state: `<div class="empty-state"><div class="glyph" aria-hidden="true">▦</div><h3>…</h3><p>…</p></div>`
- Alerts via FacesMessages appear automatically in the template (globalOnly).
- Detail: `.detail-grid` > `.kv` > `.k` (label) + `.v` (value)
- Messages timeline: `.timeline` > `.msg` (`.msg--out` for current user's own, `.msg--internal` for internal staff notes) > `.msg-meta` (`.who`, time) + `.msg-body`; internal notes also carry `<span class="internal-tag">Internal</span>`.
- Steps (booking): `.steps` > `.step` with `.active` / `.done` and a `.n` number.
- Confirm dialogs: `xmlns:pt="http://xmlns.jcp.org/jsf/passthrough"` and `pt:data-confirm="Really cancel this appointment?"` on the `h:form` or `h:commandLink` (app.js intercepts it).
- Links to other pages: `<h:link outcome="/app/staff/patients.xhtml">` or plain `<a href="#{request.contextPath}/app/...">`.

## Security rules (non-negotiable)

- User content is rendered ONLY via `h:outputText` (escapes by default) or `#{…}` in text — NEVER `escape="false"`, never `h:outputText escape=...`. Message bodies render with CSS `white-space: pre-wrap` via class `pre-wrap`.
- Every action (confirm/cancel/verify/approve/deny/release/upload/export…) is a POST through `h:form` + bean action calling the SERVICE (which re-checks scope + ownership server-side). Never a GET link for mutations.
- GET links are for navigation/downloads only. Downloads use the authorized servlet URLs.
- No inline `<script>`, no `onclick=` attributes. Use `pt:data-confirm` for confirms.
- All inputs have `maxlength`; textareas add `data-maxlength` for the counter (optional).
- Role-specific fragments use `ui:fragment rendered="#{session.isPatient()}"` etc. NEVER hide sensitive actions with CSS only — the service enforces, the UI just hides.
- Numbers/dates: parse with converters (`<f:convertDateTime pattern="yyyy-MM-dd" type="date" />` for LocalDate inputs of type="date"; `pattern="yyyy-MM-dd'T'HH:mm"` for LocalDateTime).

## Pager pattern (copy this when using PageResult)

```xml
<ui:include src="/WEB-INF/includes/pager.xhtml">
  <ui:param name="pg" value="#{bean.page}" />
  <ui:param name="bean" value="#{bean}" />
</ui:include>
```
The bean must expose `PageResult<X> getPage()`, `void nextPage()`, `void prevPage()`, `int getPageIndex()`
(standard implementation: pageIndex++ / pageIndex-- then refresh()). The include renders the pager.

## File/dir map (create EXACTLY these files, nothing else)

Area pages live under `src/main/webapp/app/...`; beans under `src/main/java/com/oakfield/clinic/channel/web/...`
(see your assignment). Do NOT modify: `WEB-INF/templates/*`, `resources/*`, services, entities, pom, or any file
not in your list. Do NOT run maven — the lead compiles after integration.

# Oakfield Clinic Patient Portal

A realistic clinic patient portal for a small outpatient practice: patient
self-service, front-desk scheduling and form verification, clinician charts
and visit summaries, prescription refill workflow, secure messaging, and
clinic administration — built as a Jakarta EE 10 monolith (JSF + JAX-RS + EJB
+ JPA) deployed on **WildFly** with **PostgreSQL**.

All patient data is generated mock data. No external services are required:
email/SMS are local mocks (outbox table), webhooks deliver to a bundled demo
receiver, and the pharmacy/payer "vendor core" runs as a local stub with its
own credential and error codes.

---

## Quick start (one command)

```bash
docker compose up
```

Wait for the health check to pass, then open **http://localhost:8080/clinic-portal**.
`docker compose up` brings up Postgres, runs the Flyway migrations
(`src/main/resources/db/migration`), starts WildFly with the application, and
loads the demo seed data on first boot.

| Role       | Email                   | Password            |
|------------|-------------------------|---------------------|
| Patient    | `patient@oakfield.local`   | `PatientPortal2026!` |
| Front desk | `frontdesk@oakfield.local` | `PatientPortal2026!` |
| Clinician  | `clinician@oakfield.local` | `PatientPortal2026!` |
| Admin      | `admin@oakfield.local`     | `PatientPortal2026!` |

(Extra seeded accounts follow the same password: `grace.lin@example.test`,
`omar.haddad@example.test`, `chen@oakfield.local`, `jorge@oakfield.local`, …)

The seeded dataset: 12 patients, 5 providers, 30+ appointments across all six
appointment statuses, 11 visit summaries (released and unreleased drafts),
12 prescriptions with refill requests in every workflow state, 25 messages
(including internal staff notes), 20 uploaded documents (including rejected
and archived forms), notifications, an audit trail, and one demo webhook
subscription.

### Stopping / resetting

```bash
docker compose down          # keep data
docker compose down -v       # wipe database + uploaded files (fresh seed on next up)
```

---

## Building and testing without Docker

Requires JDK 21. The committed Maven wrapper downloads Maven on first use.

```bash
./mvnw verify            # compiles + unit tests + packages target/clinic-portal.war
./scripts/smoke.sh       # scripted authorization/workflow checks against a running instance
```

To run the app outside Docker you need WildFly 32+ with a PostgreSQL datasource
bound at `java:jboss/datasources/ClinicDS` (see `docker/configure.cli` for the
exact configuration), plus `DB_URL` / `DB_USER` / `DB_PASSWORD` /
`CLINIC_DATA_DIR` / `DECISION_TOKEN_SECRET` / `CORE_MOCK_API_KEY` environment
variables. Migrations run automatically at application startup (Flyway) and
are idempotent with the compose `migrate` service.

## CI

`Jenkinsfile` (declarative): checkout → `./mvnw clean verify` (build + unit
tests) → archive `target/clinic-portal.war` → JUnit + issues reports.

---

## Architecture and security model

### Tier and trust statement (Shared Contract)

The application is a single deployable WAR containing three clearly separated
tiers:

- **`channel`** (`com.oakfield.clinic.channel.*`) — the user-facing tier:
  JSF server-rendered views, JAX-RS API surface, and authentication. A custom
  Jakarta Security `HttpAuthenticationMechanism` verifies credentials
  (PBKDF2-HMAC-SHA256, 210k iterations) against the account table and
  registers the container identity, so `web.xml` security-constraints and
  `@RolesAllowed` are enforced by the container.
- **`decision`** (`com.oakfield.clinic.decision.*`) — the tier that OWNS
  authorization. At login it mints an **opaque HMAC-signed session token**;
  on **every** request the channel forwards that token and `decision` resolves
  it against **its own tables** (`decision_session`,
  `decision_role_scope`, `decision_scope_limit`), re-checks account status,
  and returns the acting user's role, scopes, and limits. **The channel
  authenticates; the decision tier authorizes.** Client-supplied identity
  attributes (headers, paths, query params, form fields, bodies) are ignored —
  role/scope/limits come exclusively from decision-owned rows.
- **`core-mock`** (`/core/*`) — the vendor-core stub (pharmacy
  clearinghouse / payer eligibility). It behaves like an external system: its
  own network endpoint, its own credential (`X-Core-Key`), and its own error
  codes (`CORE-xxx`). It is always reached with a **scope token minted by the
  decision tier**; it verifies the signature and enforces the scope claim, and
  never trusts raw scope parameters.

### Security controls

- **Authentication**: PBKDF2 hashed passwords (salted, constant-time compare);
  session fixation protection (`changeSessionId`); HttpOnly + SameSite=Lax
  cookies; single-use, 30-minute password-reset tokens; 7-day invite tokens
  (only hashes stored); generic reset responses (no account enumeration).
- **Authorization**: coarse role gates via `web.xml` security-constraints +
  `@RolesAllowed` facades; fine-grained decision-tier **scope** checks plus
  **object ownership** checks in every service (`PatientAccessService` — one
  call frame above the controllers). Patients see only their own records;
  clinicians only charts for assigned patients or appointments; front desk
  cannot write visit summaries or decide refills (blocked server-side);
  internal staff notes and draft summaries are never returned to patients.
- **State machines**: appointment, visit summary, refill, and document
  lifecycles are enforced as explicit allowed-transition edge lists
  (`AppointmentWorkflow`, `VisitWorkflow`, `RefillWorkflow`,
  `DocumentWorkflow`); every transition is audited with before/after values
  and the authorization basis.
- **Audit**: append-only `audit_event` table — there is no update or delete
  path anywhere; every state transition and privileged action records actor,
  action, before/after, timestamp, and authz basis. Chart reads record ONE
  event per request with the record count. Admin-only viewer + export.
- **Output/input**: all user content rendered with JSF's default escaping
  (never `escape="false"`); length/format validation server-side; JPA
  parameterized queries only (never string-concatenated JPQL); CSV import
  parsed strictly with row-level error reports.
- **Files**: uploads stored on local disk under a **canonicalised base
  directory** (`FileStore`); metadata + SHA-256 in Postgres; content-type
  whitelist per category; downloads re-authorize per request and audit every
  download; `Content-Disposition` with RFC 5987 filename encoding.
- **Sessions/limits**: decision-enforced limits (upload MB, message length,
  booking horizon, cancellation cutoff, export row caps) read from seeded
  decision tables; sessions expire (12h) and are revoked on logout, password
  change, role change, and disable.
- **Headers**: CSP, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, `Cache-Control: no-store` on app pages.
- **API surface**: session Bearer tokens and SHA-256-hashed API keys (shown
  once); webhook payloads signed with HMAC-SHA256 and delivered with bounded
  retries; core-mock scope tokens with scope + subject binding.
- **Secrets**: `DECISION_TOKEN_SECRET`, `CORE_MOCK_API_KEY`, DB credentials
  come from environment variables (dev defaults only in `docker-compose.yml`).

### Money handling

Appointment type fees and payer copays are fixed-precision `BigDecimal`
values in a named currency (`USD`), seeded in reference tables and read
server-side only — never accepted from clients.

### Background workflows (in-process EJB timers, no external broker)

- Outbox processor (mock email/SMS "send", every 30s)
- Webhook delivery retries + failed refill transmission retries (every 2 min)
- Session janitor (expired decision sessions revoked, every 5 min)
- Appointment janitor (48h-unconfirmed booking requests auto-cancelled via
  the normal transition + audit, every 30 min)
- Next-day appointment reminders (SMS outbox + in-app notification, 07:00)

### Precision baseline (intentional, non-vulnerability locations)

These five locations exist to test scanner precision and are **not**
vulnerabilities; each carries an in-code comment:

1. Ownership/scope check in a shared service one call frame above the
   controllers (`PatientAccessService.requirePatientAccess`).
2. `java.util.Random` used only for a cache-busting suffix on a static asset
   (`AssetHelper`).
3. MD5 used only as an ETag for reference-data responses
   (`ReferenceResource` + `TokenCodec.md5Hex`).
4. Parameterised query with a bounded, whitelisted sort/filter column
   (`PatientService.search`).
5. File download resolving paths through a canonicalised base directory
   (`FileStore.resolve`).

---

## Implementation summary

**Main screens**

- Public: login, patient self-registration, password reset (single-use link),
  invite activation, styled 403/404/500 pages.
- Role dashboards: patient (upcoming appointments, unread messages, forms in
  review, active prescriptions, released summaries, quick actions), front desk
  (today's board, confirm queue, form queue), clinician (today's queue, refill
  queue, draft summaries), admin (user/patient/provider/appointment stats,
  recent audit).
- Patient: appointment list/detail (cancel, reschedule), 4-step booking wizard
  (type → provider → date/slot → reason), secure messages, document
  upload/download, prescriptions with refill request/resubmit/cancel, released
  visit summaries.
- Staff: schedule board (per-provider day view with check-in, complete,
  no-show, cancel actions), appointment search/filters, patient search,
  patient profile with front-desk demographics editing, messages with
  internal staff notes, document management (verify/reject/archive, clinical
  uploads).
- Clinician: my queue, full patient chart (overview, appointments, summaries,
  prescriptions, messages, documents, audit history — one audited read per
  request), visit summary create/edit/release, prescription creation, refill
  queue (approve / deny / request more info), payer eligibility check via the
  core-mock.
- Front desk: booking-request confirmation queue, form verification queue.
- Admin: users (create, edit, role change, disable/enable, admin password
  reset, invites), providers, provider schedules + blocked dates, appointment
  types with seeded USD fees, clinic settings, audit log with filters +
  export, CSV patient import with error reports + template, schedule/visit
  summary/audit CSV exports, API keys (one-time display), webhook
  subscriptions with deliveries + demo receiver, email/SMS outbox viewer.

**Workflows covered end to end**: patient booking → confirmation → check-in →
completion → visit summary draft → release; refill request → approve (with
mock pharmacy transmission) / deny / needs-info / cancel; document upload →
verify / reject / archive; patient ↔ staff messaging with internal notes;
staff rescheduling and cancellation.

**Scripted checks**: `scripts/smoke.sh` exercises login for all four roles,
wrong-password rejection, anonymous rejection, booking via API, scope
boundaries, core-mock credential enforcement, and export authorization.
Unit tests cover the state machines, the decision context (scope boundaries
per role), token signing/tamper/expiry, password hashing, and file-path
canonicalisation (`./mvnw verify`).

## UI summary

- **Navigation**: persistent dark-teal sidebar with role-aware sections and
  unread badges (notifications, messages), topbar with breadcrumbs,
  notification bell, avatar/account menu, and sign-out; collapses to an
  off-canvas drawer below 1024px.
- **Dashboards**: role-specific stat cards (teal/green/amber/blue/violet
  chips), quick-action links, today's schedule, queues, and recent
  notifications/audit previews.
- **Visual system**: warm paper background, white cards with soft borders and
  shadows, deep-teal primary actions, serif display headings (Iowan Old
  Style/Palatino/Georgia stack) over a humanist system sans — an editorial,
  "calm clinical" identity. Semantic status badges always pair a color with a
  text label (never color-only). Accessible contrast throughout (dark text on
  light surfaces, solid white on teal/red/green buttons), visible 3px focus
  rings, labelled forms with help text and inline validation, empty/error/
  success states, responsive grids down to phone widths, and a split-panel
  branded login experience.

## Notes / limitations

- All external integrations are intentionally local mocks: email/SMS (outbox
  table + demo links shown in the UI when `demo.show_email_links=true`),
  webhooks (bundled echo receiver), and the pharmacy/payer core stub.
- The project targets a single-node local deployment (no clustering).
- JSF project stage is `Development` for helpful error pages; set
  `JSF_PROJECT_STAGE=Production` for production-like deployments.

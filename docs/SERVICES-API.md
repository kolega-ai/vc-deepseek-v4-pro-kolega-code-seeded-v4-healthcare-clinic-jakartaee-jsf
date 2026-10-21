# Service API reference (for UI implementation)

All business logic lives in EJB services under `com.oakfield.clinic.svc`. Every method that
touches records takes a `DecisionContext` (authorization context). Backing beans obtain it via
`session.ctx()` (`com.oakfield.clinic.channel.web.common.SessionInfo`, `@Named("session")`).

Exceptions (all `RuntimeException`): `ValidationException` (user-facing message), `NotFoundException`,
`AccessDeniedException`, `NotAuthenticatedException`. Beans catch `ValidationException` and show
`FacesUtil.error(...)`; authz exceptions are handled globally.

## DecisionContext (com.oakfield.clinic.decision)
- `long getUserId()`, `String getFullName()`, `String getRole()`, `String getEmail()`
- `boolean hasScope(String)`, `void require(String)`, `boolean isPatient()`, `boolean isStaff()`
- `int limitInt(String name, int default)`

## AuditService — `com.oakfield.clinic.svc.AuditService`
- `record(AuditFilter filter, int page, int pageSize)` — NO: correct signatures:
- `List<AuditEvent> search(AuditFilter filter, int page, int pageSize)`
- `long count(AuditFilter filter)`
- `List<AuditEvent> forPatient(Long patientId, int page, int pageSize)`
- `List<String> distinctActions()`
- `record(AuditFilter{LocalDate from, LocalDate to, String action, String targetType, String actor})` — the filter record.

## AppointmentService
- `PageResult<Appointment> search(DecisionContext ctx, AppointmentFilter filter, int page, int pageSize)`
  - `AppointmentFilter(LocalDate from, LocalDate to, Long providerId, Long patientId, AppointmentStatus status)`
- `Appointment get(DecisionContext ctx, long id)`
- `List<Appointment> upcomingForPatient(ctx, int limit)`
- `List<Appointment> forDay(ctx, LocalDate day, Long providerId)` — staff, providerId null = whole clinic
- `List<Appointment> requestedQueue(ctx)` — REQUESTED, for confirm queue
- `List<Appointment> todayForProvider(ctx, long providerId)` — clinician today
- `List<LocalDateTime> availableSlots(ctx, long providerId, LocalDate date, long typeId)`
- `Appointment bookRequested(ctx, patientId, providerId, typeId, LocalDateTime start, String reason, String notes)`
- `Appointment bookScheduled(ctx, patientId, providerId, typeId, LocalDateTime start, String reason, String notes)`
- `Appointment confirm(ctx, id)` / `checkIn(ctx, id)` / `complete(ctx, id)`
- `Appointment cancel(ctx, id, String reason)` / `markNoShow(ctx, id, String reason)`
- `Appointment reschedule(ctx, id, LocalDateTime newStart)` — staff
- `Appointment reschedulePatient(ctx, id, providerId, typeId, LocalDateTime newStart, String reason)` — patient cancel+rebook
- `long countByStatus(ctx, status)` / `long countOnDay(ctx, LocalDate)`

## VisitService
- `record VisitForm(String diagnosisText, String careInstructions, String followUpPlan, String clinicianNotes)`
- `VisitSummary createDraft(ctx, long appointmentId, VisitForm)`
- `VisitSummary updateDraft(ctx, long summaryId, VisitForm)`
- `VisitSummary release(ctx, long summaryId)`
- `VisitSummary getForPatient(ctx, long summaryId)` — RELEASED + own only
- `List<VisitSummary> releasedForPatient(ctx, int limit)`
- `List<VisitSummary> forChart(ctx, long patientId)` — clinician
- `List<VisitSummary> draftsByProvider(ctx, long providerId)` / `countDraftsByProvider(ctx, long providerId)`

## PrescriptionService
- `record RxForm(String medicationName, String dosage, String frequency, String instructions, int refillsRemaining, Long appointmentId)`
- `Prescription create(ctx, long patientId, RxForm)`
- `Prescription discontinue(ctx, long prescriptionId)`
- `Prescription requestRefill(ctx, long prescriptionId, String note)` — patient (or NEEDS_INFO resubmit)
- `List<Prescription> refillQueue(ctx)` — clinician
- `Prescription approveRefill(ctx, id, String note)` / `denyRefill(ctx, id, note)` / `needsInfoRefill(ctx, id, question)` / `cancelRefill(ctx, id)`
- `List<Prescription> forPatientHome(ctx)` / `forChart(ctx, long patientId)` / `getForPatient(ctx, id)`

## DocumentService
- `record UploadResult(Document document, long sizeBytes)`
- `UploadResult upload(ctx, long patientId, DocumentCategory category, String title, String description, jakarta.servlet.http.Part part)`
- `record DocFilter(Long patientId, DocumentCategory category, DocumentStatus status, String query)`
- `PageResult<Document> search(ctx, DocFilter filter, int page, int pageSize)`
- `List<Document> verificationQueue(ctx)` — SUBMITTED FORM docs
- `Document verify(ctx, id, String note)` / `reject(ctx, id, String note)` / `archive(ctx, id)`
- `long countByStatus(ctx, DocumentStatus)`
- Download link (audited, authorized): `/app/files/{id}` — plain `<a href>`.

## MessageService
- `record ThreadView(MessageThread thread, List<Message> messages, boolean unread, long patientVisibleCount)`
- `List<MessageThread> myThreads(ctx)` — patient
- `List<MessageThread> threadsForPatient(ctx, long patientId)` — staff
- `ThreadView openThread(ctx, long threadId)` — marks read; patient sees PATIENT_VISIBLE only
- `MessageThread startThread(ctx, patientId, subject, body)` — patient sends
- `MessageThread staffStartThread(ctx, patientId, subject, body)` — staff starts
- `Message reply(ctx, threadId, body)`
- `Message addInternalNote(ctx, threadId, body)` — staff only, never patient-visible
- `boolean isUnread(ctx, thread)` / `long unreadThreadCount(ctx)`

## PatientService
- `record PatientFilter(String query, Long providerId, Boolean activeOnly)`
- `PageResult<Patient> search(ctx, PatientFilter, int page, int pageSize, String sort)` — sort: "name" | "created" | "dob"
- `Patient getForStaff(ctx, long id)`
- `record PatientForm(firstName, lastName, LocalDate dateOfBirth, String gender, String phone, String email,
   String addressLine1, String city, String state, String postalCode, String emergencyContactName,
   String emergencyContactPhone, String insuranceProvider, String insurancePolicyNumber,
   String insuranceGroupNumber, Long primaryProviderId, String medicalAlert)`
- `Patient create(ctx, PatientForm)` — front desk walk-in
- `Patient updateDemographics(ctx, long id, PatientForm)`
- `Patient updateOwnProfile(ctx, phone, email, addressLine1, city, state, postalCode, ecName, ecPhone)`
- `void setActive(ctx, long id, boolean active)` — admin
- `long countActive()`

## ProviderService
- `List<Provider> listActive()` / `listAll()` / `get(id)` / `byNpi(String)` / `byUserId(long)`
- `create(ctx, firstName, lastName, title, specialty, npi, phone, email)` — schedule.manage
- `update(ctx, id, firstName, lastName, title, specialty, phone, email, boolean active)`

## AppointmentTypeService
- `List<AppointmentType> listActive()` / `listAll()` / `get(id)`
- `create(ctx, code, name, description, int durationMinutes, BigDecimal feeAmount, String feeCurrency, boolean active)`
- `update(ctx, id, name, description, durationMinutes, feeAmount, feeCurrency, active)`

## ScheduleService
- `List<ProviderSchedule> schedulesFor(long providerId)`
- `List<ProviderBlockedDate> blockedDatesFor(providerId, from, to)`
- `saveSchedule(ctx, providerId, int dayOfWeek /*1-7*/, LocalTime start, LocalTime end)`
- `removeSchedule(ctx, long scheduleId)`
- `addBlockedDate(ctx, providerId, LocalDate date, String reason)`
- `removeBlockedDate(ctx, long blockedId)`

## ImportExportService
- `ImportJob importPatients(ctx, String originalName, InputStream csv)`
- `record ExportFilter(LocalDate from, LocalDate to, Long providerId, AppointmentStatus status)`
- `ExportJob exportSchedule(ctx, ExportFilter)`
- `ExportJob exportSummaries(ctx, LocalDate from, LocalDate to, Long providerId)`
- `ExportJob exportAudit(ctx, LocalDate from, LocalDate to, String action)`
- `List<ExportJob> recentExports(ctx)`
- `List<ImportJob> recentImports(int limit)`
- Download links (authorized): exports `/app/exports/{id}`, error reports `/app/imports/errors/{importId}`

## IntegrationService (admin)
- `List<WebhookSubscription> listSubscriptions()`
- `WebhookSubscription createSubscription(ctx, String url, String secret, List<String> events)`
- `WebhookSubscription updateSubscription(ctx, long id, boolean active)`
- `String testSubscription(ctx, long id)`
- `List<WebhookDelivery> listDeliveries(int page, int pageSize)`
- `List<WebhookEcho> listEchoes(int page, int pageSize)`
- `Set<String> SUPPORTED_EVENTS`

## DecisionService (decision tier)
- `record ApiKeyInfo(long id, String name, String keyPrefix, Set<String> scopes, boolean active, LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime lastUsedAt)`
- `record ApiKeyCreation(long id, String plaintextKey)`
- `ApiKeyCreation createApiKey(String name, Collection<String> scopes, Long createdBy, LocalDateTime expiresAt)`
- `List<ApiKeyInfo> listApiKeys()` / `setApiKeyActive(long id, boolean active)`
- `Set<String> ALLOWED_API_KEY_SCOPES` — "availability.read", "appointment.request", "export.read"

## UserAdminService (admin)
- `record UserForm(String email, String fullName, AppRole role, String phone, String password)`
- `PageResult<User> list(ctx, String query, int page, int pageSize)`
- `User create(ctx, UserForm)`
- `User update(ctx, long id, String fullName, String phone, AppRole role)`
- `User setStatus(ctx, long id, boolean active)`
- `User adminResetPassword(ctx, long id, String newPassword)`
- `long countByRole(ctx, AppRole)` / `countAll(ctx)`

## AuthService (admin invites)
- `record InviteRequest(String email, String fullName, AppRole role)`
- `record InviteResult(String demoLink)`
- `InviteResult createInvite(ctx, InviteRequest)` — roles: CLINICIAN, FRONT_DESK, PATIENT (not admin)
- `User findByEmail(String)`

## Others
- `NotificationService`: `forUser(userId, page, pageSize)`, `countForUser(userId)`, `unreadCount(userId)`, `markRead(ctx, id)`, `markAllRead(ctx)`
- `OutboxService`: `list(int page, int pageSize)`, `count()`
- `SettingsService`: `all()` (Map), `get(key, default)`, `getInt`, `getBool`, `clinicName()`, `set(DecisionContext ctx, String key, String value)` (admin-only, audited)
- `PatientAccessService`: `patientForUser(ctx)` (patient's own record), `requirePatientAccess(ctx, id, level)` — for the chart bean only.

NOTE for admin settings: `SettingsService.set(ctx, key, value)` exists and is audited; the admin settings page uses it for known keys.

## Model getters (entities)
All entities are POJOs with standard getters (e.g. `Appointment.getStartTime()`, `getPatient().getFirstName()`,
`getProvider().fullName()`, `Patient.fullName()` (no get prefix!), `Provider.fullName()`,
`AppointmentType.getName()`, `Document.getOriginalName()`...).
Enums: `AppointmentStatus`, `VisitStatus`, `RefillStatus`, `DocumentStatus`, `DocumentCategory`, `Gender`,
`AppRole`, `UserStatus`, `JobStatus`, `DeliveryStatus`, `OutboxChannel`, `NotificationType`, `ExportKind`.

-- =====================================================================
-- V2 - Seeded reference data.
-- Fee schedule (appointment types), decision tier role->scope grants and
-- per-role limits, and clinic settings. All amounts are fixed-precision
-- decimals (NUMERIC) in a named currency; clients never supply them.
-- =====================================================================

-- ------------------------- Fee schedule -------------------------------
INSERT INTO appointment_type (code, name, description, duration_minutes, fee_amount, fee_currency, active, sort_order) VALUES
 ('NEW_PATIENT',   'New Patient Visit',   'First comprehensive visit with a provider, including history and baseline exam.', 60, 180.00, 'USD', TRUE, 1),
 ('FOLLOW_UP',     'Follow-up Visit',     'Return visit to review ongoing care.',                                             30,  95.00, 'USD', TRUE, 2),
 ('ANNUAL',        'Annual Physical',     'Full annual wellness examination and preventive screening review.',                60, 210.00, 'USD', TRUE, 3),
 ('SICK',          'Sick Visit',          'Same-week visit for acute illness or injury.',                                     30, 110.00, 'USD', TRUE, 4),
 ('TELEHEALTH',    'Telehealth Visit',    'Video or phone visit; no in-person attendance required.',                          30,  85.00, 'USD', TRUE, 5),
 ('VACCINATION',   'Vaccination',         'Scheduled immunization appointment.',                                              20,  45.00, 'USD', TRUE, 6),
 ('MEDICARE_WC',   'Medicare Wellness',   'Annual wellness visit covered under Medicare; self-pay rate shown for reference.', 60, 150.00, 'USD', FALSE, 7);

-- ------------------- Decision tier: role -> scopes ---------------------
-- patient
INSERT INTO decision_role_scope (role, scope) VALUES
 ('PATIENT', 'patient.read.own'),
 ('PATIENT', 'appointment.book'),
 ('PATIENT', 'appointment.cancel.own'),
 ('PATIENT', 'refill.request'),
 ('PATIENT', 'message.send'),
 ('PATIENT', 'document.upload'),
 ('PATIENT', 'profile.update');

-- front_desk
INSERT INTO decision_role_scope (role, scope) VALUES
 ('FRONT_DESK', 'patient.read'),
 ('FRONT_DESK', 'patient.demographics.write'),
 ('FRONT_DESK', 'appointment.book'),
 ('FRONT_DESK', 'appointment.manage'),
 ('FRONT_DESK', 'appointment.checkin'),
 ('FRONT_DESK', 'appointment.complete'),
 ('FRONT_DESK', 'schedule.read'),
 ('FRONT_DESK', 'message.send'),
 ('FRONT_DESK', 'message.note.internal'),
 ('FRONT_DESK', 'document.upload'),
 ('FRONT_DESK', 'document.verify'),
 ('FRONT_DESK', 'document.manage'),
 ('FRONT_DESK', 'export.schedule'),
 ('FRONT_DESK', 'import.patients');

-- clinician
INSERT INTO decision_role_scope (role, scope) VALUES
 ('CLINICIAN', 'patient.read'),
 ('CLINICIAN', 'appointment.book'),
 ('CLINICIAN', 'appointment.manage'),
 ('CLINICIAN', 'appointment.complete'),
 ('CLINICIAN', 'schedule.read'),
 ('CLINICIAN', 'visit.write'),
 ('CLINICIAN', 'visit.release'),
 ('CLINICIAN', 'visit.read.clinical'),
 ('CLINICIAN', 'rx.write'),
 ('CLINICIAN', 'refill.decide'),
 ('CLINICIAN', 'message.send'),
 ('CLINICIAN', 'message.note.internal'),
 ('CLINICIAN', 'document.upload'),
 ('CLINICIAN', 'document.manage'),
 ('CLINICIAN', 'export.schedule'),
 ('CLINICIAN', 'export.clinical');

-- clinic_admin (full control)
INSERT INTO decision_role_scope (role, scope) VALUES
 ('CLINIC_ADMIN', 'patient.read'),
 ('CLINIC_ADMIN', 'patient.demographics.write'),
 ('CLINIC_ADMIN', 'patient.manage'),
 ('CLINIC_ADMIN', 'appointment.book'),
 ('CLINIC_ADMIN', 'appointment.manage'),
 ('CLINIC_ADMIN', 'appointment.checkin'),
 ('CLINIC_ADMIN', 'appointment.complete'),
 ('CLINIC_ADMIN', 'schedule.read'),
 ('CLINIC_ADMIN', 'schedule.manage'),
 ('CLINIC_ADMIN', 'visit.read.clinical'),
 ('CLINIC_ADMIN', 'message.send'),
 ('CLINIC_ADMIN', 'message.note.internal'),
 ('CLINIC_ADMIN', 'document.upload'),
 ('CLINIC_ADMIN', 'document.verify'),
 ('CLINIC_ADMIN', 'document.manage'),
 ('CLINIC_ADMIN', 'export.schedule'),
 ('CLINIC_ADMIN', 'export.clinical'),
 ('CLINIC_ADMIN', 'export.audit'),
 ('CLINIC_ADMIN', 'import.patients'),
 ('CLINIC_ADMIN', 'audit.read'),
 ('CLINIC_ADMIN', 'user.manage'),
 ('CLINIC_ADMIN', 'integration.manage'),
 ('CLINIC_ADMIN', 'setting.manage'),
 ('CLINIC_ADMIN', 'outbox.read');

-- ------------------- Decision tier: per-role limits --------------------
INSERT INTO decision_scope_limit (role, scope, limit_name, limit_value) VALUES
 ('PATIENT',    'appointment.book',       'appointment.book.max_future_days', '90'),
 ('PATIENT',    'appointment.cancel.own', 'appointment.cancel.cutoff_hours',  '24'),
 ('PATIENT',    'document.upload',        'upload.max_mb',                    '10'),
 ('PATIENT',    'message.send',           'message.max_length',               '4000'),
 ('FRONT_DESK', 'document.upload',        'upload.max_mb',                    '10'),
 ('FRONT_DESK', 'import.patients',        'import.max_rows',                  '5000'),
 ('FRONT_DESK', 'export.schedule',        'export.max_rows',                  '5000'),
 ('FRONT_DESK', 'message.send',           'message.max_length',               '4000'),
 ('CLINICIAN',  'document.upload',        'upload.max_mb',                    '10'),
 ('CLINICIAN',  'export.schedule',        'export.max_rows',                  '5000'),
 ('CLINICIAN',  'export.clinical',        'export.max_rows',                  '5000'),
 ('CLINICIAN',  'message.send',           'message.max_length',               '4000'),
 ('CLINIC_ADMIN', 'document.upload',      'upload.max_mb',                    '10'),
 ('CLINIC_ADMIN', 'import.patients',      'import.max_rows',                  '10000'),
 ('CLINIC_ADMIN', 'export.schedule',      'export.max_rows',                  '20000'),
 ('CLINIC_ADMIN', 'export.clinical',      'export.max_rows',                  '20000'),
 ('CLINIC_ADMIN', 'message.send',         'message.max_length',               '8000');

-- ------------------------- Clinic settings ------------------------------
INSERT INTO clinic_setting (key, value, description) VALUES
 ('clinic.name',              'Oakfield Family Clinic',       'Display name of the clinic'),
 ('clinic.phone',             '(555) 014-2200',               'Main clinic phone number'),
 ('clinic.email',             'care@oakfield-clinic.local',   'Main clinic contact email'),
 ('clinic.address',           '214 Sycamore Lane, Oakfield',  'Clinic street address'),
 ('clinic.hours',             'Mon-Fri 8:00-18:00, Sat 9:00-13:00', 'Reception hours'),
 ('demo.show_email_links',    'true',                         'Demo: show mocked email content (reset links) on screen'),
 ('portal.registration_open', 'true',                         'Whether public patient self-registration is enabled'),
 ('reminder.lead_hours',      '24',                           'Hours before an appointment to send a reminder'),
 ('refill.auto_transmit',     'true',                         'Transmit approved refills to the (mock) pharmacy clearinghouse');

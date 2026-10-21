#!/usr/bin/env bash
# Per-role page sweep: JSF login flow, then key pages per role (expect 200 + marker).
set -u
BASE="${1:-http://localhost:8080/clinic-portal}"
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); echo "  PASS: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }

login() { # email -> cookie jar path
  local email="$1" jar="$2"
  rm -f "$jar"
  curl -s -c "$jar" -m 20 "$BASE/login.xhtml" -o /tmp/sw_login.html
  local VS BTN
  VS=$(python3 -c "
import re
m=re.search(r'name=\"jakarta.faces.ViewState\"[^>]*value=\"([^\"]+)\"',open('/tmp/sw_login.html').read())
print(m.group(1) if m else '')")
  BTN=$(python3 -c "
import re
m=re.search(r'name=\"(loginForm:j_idt\d+)\" value=\"Sign in\"',open('/tmp/sw_login.html').read())
print(m.group(1) if m else '')")
  local code
  code=$(curl -s -b "$jar" -c "$jar" -o /dev/null -w '%{http_code}' -m 30 -X POST "$BASE/login.xhtml" \
    --data "loginForm=loginForm&j_username=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote('$email'))")&j_password=PatientPortal2026%21&jakarta.faces.ViewState=$VS&returnTo=")
  [ "$code" = "302" ]
}

page() { # jar url marker label
  local jar="$1" url="$2" marker="$3" label="$4"
  local code
  code=$(curl -s -b "$jar" -o /tmp/sw_page.html -w '%{http_code}' -m 30 "$url")
  if [ "$code" = "200" ] && grep -q "$marker" /tmp/sw_page.html; then ok "$label"; else bad "$label (code=$code)"; fi
}

say() { echo; echo "== $*"; }

say "Patient"
login patient@oakfield.local /tmp/sw_pat.jar && ok "patient JSF login" || bad "patient JSF login"
page /tmp/sw_pat.jar "$BASE/app/dashboard.xhtml" "Upcoming appointments" "patient dashboard"
page /tmp/sw_pat.jar "$BASE/app/patient/appointments.xhtml" "My appointments" "appointments list"
page /tmp/sw_pat.jar "$BASE/app/patient/book.xhtml" "Book an appointment" "booking wizard"
page /tmp/sw_pat.jar "$BASE/app/patient/messages.xhtml" "New message" "messages"
page /tmp/sw_pat.jar "$BASE/app/patient/documents.xhtml" "Upload a form" "documents"
page /tmp/sw_pat.jar "$BASE/app/patient/prescriptions.xhtml" "Prescriptions" "prescriptions"
page /tmp/sw_pat.jar "$BASE/app/patient/summaries.xhtml" "Visit summaries" "visit summaries"
page /tmp/sw_pat.jar "$BASE/app/notifications.xhtml" "Notifications" "notifications"
page /tmp/sw_pat.jar "$BASE/app/profile.xhtml" "Change password" "profile"

say "Front desk"
login frontdesk@oakfield.local /tmp/sw_desk.jar && ok "front desk JSF login" || bad "front desk JSF login"
page /tmp/sw_desk.jar "$BASE/app/dashboard.xhtml" "Today's board" "front desk dashboard"
page /tmp/sw_desk.jar "$BASE/app/staff/schedule.xhtml" "Schedule board" "schedule board"
page /tmp/sw_desk.jar "$BASE/app/staff/appointments.xhtml" "Appointments" "appointments"
page /tmp/sw_desk.jar "$BASE/app/staff/patients.xhtml" "Patients" "patients"
page /tmp/sw_desk.jar "$BASE/app/staff/messages.xhtml" "Choose patient" "staff messages"
page /tmp/sw_desk.jar "$BASE/app/staff/documents.xhtml" "Documents" "staff documents"
page /tmp/sw_desk.jar "$BASE/app/staff/frontdesk/confirm.xhtml" "Confirm" "confirm queue"
page /tmp/sw_desk.jar "$BASE/app/staff/frontdesk/forms.xhtml" "Form verification" "form queue"

say "Clinician"
login clinician@oakfield.local /tmp/sw_doc.jar && ok "clinician JSF login" || bad "clinician JSF login"
page /tmp/sw_doc.jar "$BASE/app/dashboard.xhtml" "My appointments today" "clinician dashboard"
page /tmp/sw_doc.jar "$BASE/app/staff/clinician/queue.xhtml" "My queue" "clinician queue"
page /tmp/sw_doc.jar "$BASE/app/staff/clinician/refills.xhtml" "Refill queue" "refill queue"
page /tmp/sw_doc.jar "$BASE/app/staff/clinician/chart.xhtml?id=1" "Patient chart" "patient chart"

say "Admin"
login admin@oakfield.local /tmp/sw_admin.jar && ok "admin JSF login" || bad "admin JSF login"
page /tmp/sw_admin.jar "$BASE/app/dashboard.xhtml" "Recent activity" "admin dashboard"
page /tmp/sw_admin.jar "$BASE/app/admin/users.xhtml" "Users" "users"
page /tmp/sw_admin.jar "$BASE/app/admin/providers.xhtml" "Providers" "providers"
page /tmp/sw_admin.jar "$BASE/app/admin/schedules.xhtml" "Schedules" "schedules"
page /tmp/sw_admin.jar "$BASE/app/admin/types.xhtml" "Appointment types" "types"
page /tmp/sw_admin.jar "$BASE/app/admin/settings.xhtml" "Settings" "settings"
page /tmp/sw_admin.jar "$BASE/app/admin/audit.xhtml" "Audit log" "audit log"
page /tmp/sw_admin.jar "$BASE/app/admin/imports.xhtml" "Import" "imports"
page /tmp/sw_admin.jar "$BASE/app/admin/exports.xhtml" "Exports" "exports"
page /tmp/sw_admin.jar "$BASE/app/admin/api-keys.xhtml" "API keys" "api keys"
page /tmp/sw_admin.jar "$BASE/app/admin/webhooks.xhtml" "Webhooks" "webhooks"
page /tmp/sw_admin.jar "$BASE/app/admin/outbox.xhtml" "Outbox" "outbox"

say "Authorization (server-side)"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -b /tmp/sw_pat.jar -m 20 "$BASE/app/admin/users.xhtml")
[ "$CODE" = "403" ] || [ "$CODE" = "302" ] && ok "patient blocked from admin area ($CODE)" || bad "patient admin area ($CODE)"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -b /tmp/sw_desk.jar -m 20 "$BASE/app/staff/clinician/chart.xhtml?id=1")
[ "$CODE" = "403" ] || [ "$CODE" = "302" ] && ok "front desk blocked from clinical chart ($CODE)" || bad "front desk chart ($CODE)"

echo; echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]

-- Short self check-in code shown to the patient on the day of the visit.
ALTER TABLE appointment ADD COLUMN IF NOT EXISTS checkin_code varchar(6);

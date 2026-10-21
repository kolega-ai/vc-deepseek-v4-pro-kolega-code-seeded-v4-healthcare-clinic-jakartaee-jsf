-- Lab analyzer bridge interface credential: the analyzer bridge authenticates
-- with a shared interface code; only its digest is stored.
INSERT INTO clinic_setting (key, value, description) VALUES
 ('lab.interface_code_hash', 'dfd9f1ef379605de4aeed78ec76c5928', 'Lab analyzer bridge interface credential');

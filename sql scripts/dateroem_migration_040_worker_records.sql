-- Migration 040 (24 October 2026): worker details and documents.
--
-- "worker ID number and documents with admin/SuperAdmin verification ... this
-- system can grow thats why i need to expand the data collection area". The
-- owner chose (24 October 2026): ID or passport number, KRA PIN, NSSF and SHA
-- numbers, next of kin and document photos; the worker enters them on the
-- phone; only the SuperAdmin opens them (and so verifies them); kept until the
-- SuperAdmin deletes them.
--
-- The fields and document types are rows, not columns, so the SuperAdmin can
-- add more later without a new version of the apps. Values marked Is_Sensitive
-- and every document photo are stored encrypted (AES-GCM, the backend's
-- app.workers.data-secret); every opening by the SuperAdmin is logged.

CREATE TABLE WORKER_FIELDS (
  Field_Key VARCHAR(40) NOT NULL,
  Label VARCHAR(80) NOT NULL,
  Kind VARCHAR(10) NOT NULL DEFAULT 'text',          -- text, number, date, phone
  Hint VARCHAR(160) NULL,
  Is_Sensitive TINYINT(1) NOT NULL DEFAULT 0,
  Is_Required TINYINT(1) NOT NULL DEFAULT 0,
  Sort_Order INT NOT NULL DEFAULT 0,
  Is_Active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (Field_Key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE WORKER_DOC_TYPES (
  Type_Key VARCHAR(40) NOT NULL,
  Label VARCHAR(80) NOT NULL,
  Hint VARCHAR(160) NULL,
  Is_Required TINYINT(1) NOT NULL DEFAULT 0,
  Sort_Order INT NOT NULL DEFAULT 0,
  Is_Active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (Type_Key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One per staff account: where verification stands.
CREATE TABLE WORKER_RECORDS (
  User_ID INT NOT NULL,
  Status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',  -- NOT_STARTED, SUBMITTED, VERIFIED, NEEDS_CHANGES
  Status_Note VARCHAR(250) NULL,
  Submitted_At DATETIME NULL,
  Verified_By INT NULL,
  Verified_At DATETIME NULL,
  Updated_At DATETIME NULL,
  PRIMARY KEY (User_ID),
  CONSTRAINT fk_worker_records_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE WORKER_FIELD_VALUES (
  User_ID INT NOT NULL,
  Field_Key VARCHAR(40) NOT NULL,
  Value_Text VARCHAR(600) NULL,                       -- encrypted when the field is sensitive
  Updated_At DATETIME NULL,
  PRIMARY KEY (User_ID, Field_Key),
  CONSTRAINT fk_worker_values_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID),
  CONSTRAINT fk_worker_values_field FOREIGN KEY (Field_Key) REFERENCES WORKER_FIELDS (Field_Key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE WORKER_DOCUMENTS (
  Document_ID INT NOT NULL AUTO_INCREMENT,
  User_ID INT NOT NULL,
  Type_Key VARCHAR(40) NOT NULL,
  Data MEDIUMBLOB NOT NULL,                           -- the photo, encrypted
  Uploaded_At DATETIME NULL,
  Is_Deleted TINYINT(1) NOT NULL DEFAULT 0,
  Deleted_By INT NULL,
  Deleted_At DATETIME NULL,
  PRIMARY KEY (Document_ID),
  KEY ix_worker_documents_user (User_ID, Type_Key),
  CONSTRAINT fk_worker_documents_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID),
  CONSTRAINT fk_worker_documents_type FOREIGN KEY (Type_Key) REFERENCES WORKER_DOC_TYPES (Type_Key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO WORKER_FIELDS (Field_Key, Label, Kind, Hint, Is_Sensitive, Is_Required, Sort_Order) VALUES
  ('id_number', 'National ID or passport number', 'text', 'As on the ID card or passport', 1, 1, 10),
  ('kra_pin', 'KRA PIN', 'text', 'For example A012345678Z', 1, 0, 20),
  ('nssf_number', 'NSSF number', 'text', NULL, 1, 0, 30),
  ('sha_number', 'SHA number', 'text', 'Social Health Authority', 1, 0, 40),
  ('kin_name', 'Next of kin - name', 'text', NULL, 0, 0, 50),
  ('kin_relationship', 'Next of kin - relationship', 'text', 'For example wife, brother, mother', 0, 0, 60),
  ('kin_phone', 'Next of kin - phone', 'phone', NULL, 1, 0, 70),
  ('licence_number', 'Driving licence number', 'text', 'Drivers only', 1, 0, 80),
  ('licence_class', 'Driving licence class', 'text', 'Drivers only, for example BCE', 0, 0, 90),
  ('licence_expiry', 'Driving licence expiry', 'date', 'Drivers only', 0, 0, 100);

INSERT INTO WORKER_DOC_TYPES (Type_Key, Label, Hint, Is_Required, Sort_Order) VALUES
  ('id_front', 'ID or passport - front', 'The side with the photo', 1, 10),
  ('id_back', 'ID - back', 'Not needed for a passport', 0, 20),
  ('passport_photo', 'Passport-size photo', 'A clear photo of your face', 0, 30),
  ('driving_licence', 'Driving licence', 'Drivers only', 0, 40);

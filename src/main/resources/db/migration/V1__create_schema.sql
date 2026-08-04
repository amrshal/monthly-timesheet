CREATE TABLE `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(320) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  manager_id BIGINT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_user_email UNIQUE (email),
  CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES `user` (id),
  CONSTRAINT chk_user_role CHECK (role IN ('EMPLOYEE','MANAGER','ADMIN'))
);

CREATE TABLE monthly_timesheet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  timesheet_year SMALLINT NOT NULL,
  timesheet_month TINYINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  submitted_at DATETIME(6) NULL,
  submitted_by_user_id BIGINT NULL,
  approved_at DATETIME(6) NULL,
  approved_by_user_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_timesheet_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_timesheet_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES `user` (id),
  CONSTRAINT fk_timesheet_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES `user` (id),
  CONSTRAINT uk_timesheet_user_month UNIQUE (user_id, timesheet_year, timesheet_month),
  CONSTRAINT chk_timesheet_month CHECK (timesheet_month BETWEEN 1 AND 12),
  CONSTRAINT chk_timesheet_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED'))
);

CREATE TABLE daily_time_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  timesheet_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  duration_minutes INT NOT NULL,
  note VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_entry_timesheet FOREIGN KEY (timesheet_id) REFERENCES monthly_timesheet (id),
  CONSTRAINT uk_entry_timesheet_date UNIQUE (timesheet_id, work_date),
  CONSTRAINT chk_entry_duration CHECK (duration_minutes BETWEEN 0 AND 1440)
);

CREATE TABLE audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_time DATETIME(6) NOT NULL,
  actor_user_id BIGINT NULL,
  subject_user_id BIGINT NULL,
  event_type VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id BIGINT NULL,
  ip_address VARCHAR(64) NULL,
  details_json JSON NULL,
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES `user` (id),
  CONSTRAINT fk_audit_subject FOREIGN KEY (subject_user_id) REFERENCES `user` (id)
);
CREATE INDEX ix_timesheet_status ON monthly_timesheet (status, submitted_at);
CREATE INDEX ix_entry_date ON daily_time_entry (work_date);
CREATE INDEX ix_audit_event_time ON audit_event (event_time);

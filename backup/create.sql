-- class ApprovalRight

CREATE TABLE approval_rights
(
  last_backed_up INT NOT NULL DEFAULT 0,
  ar_id INT NOT NULL,
  gae_key VARCHAR(255) NOT NULL,
  required CHAR(1) NOT NULL DEFAULT 'N' CHECK (required IN ('Y','N')),

  PRIMARY KEY (ar_id),
  UNIQUE (gae_key)
);

CREATE TABLE approval_right_files
(
  ar_id INT NOT NULL,
  path VARCHAR(255) NOT NULL,

  PRIMARY KEY (ar_id, path),

  FOREIGN KEY (ar_id)
  REFERENCES approval_rights (ar_id)
  ON DELETE CASCADE
);

CREATE TABLE approval_right_users
(
  ar_id INT NOT NULL,
  email VARCHAR(255) NOT NULL,
  type VARCHAR(9) NOT NULL CHECK (type IN ('approver',
                                           'verifier',
                                           'submitter')),

  PRIMARY KEY (ar_id, type, email),

  FOREIGN KEY (ar_id)
  REFERENCES approval_rights (ar_id)
  ON DELETE CASCADE
);

CREATE TABLE approval_right_groups
(
  ar_id INT NOT NULL,
  group_key VARCHAR(255) NOT NULL,
  type VARCHAR(9) NOT NULL CHECK (type IN ('approver',
                                           'verifier',
                                           'submitter')),

  PRIMARY KEY (ar_id, type, group_key),

  FOREIGN KEY (ar_id)
  REFERENCES approval_rights (ar_id)
  ON DELETE CASCADE
);

-- class Project

CREATE TABLE projects
(
  last_backed_up INT NOT NULL DEFAULT 0,
  project_id INT NOT NULL,
  gae_key VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  comment VARCHAR(255),

  PRIMARY KEY (project_id),
  UNIQUE (gae_key)
);

CREATE TABLE project_owner_users
(
  project_id INT NOT NULL,
  email VARCHAR(255) NOT NULL,

  PRIMARY KEY (project_id, email),

  FOREIGN KEY (project_id)
  REFERENCES projects (project_id)
  ON DELETE CASCADE
);

CREATE TABLE project_owner_groups
(
  project_id INT NOT NULL,
  group_key VARCHAR(255) NOT NULL,

  PRIMARY KEY (project_id, group_key),

  FOREIGN KEY (project_id)
  REFERENCES projects (project_id)
  ON DELETE CASCADE
);

CREATE TABLE project_code_reviews
(
  project_id INT NOT NULL,
  ar_key VARCHAR(255) NOT NULL,

  PRIMARY KEY (project_id, ar_key),

  FOREIGN KEY (project_id)
  REFERENCES projects (project_id)
  ON DELETE CASCADE
);

-- class Branch

CREATE TABLE branches
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,

  PRIMARY KEY (project_key, name),
  UNIQUE (gae_key)
);

-- class RevisionId

CREATE TABLE revisions
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  project_key VARCHAR(255) NOT NULL,
  revision_id CHAR(40) NOT NULL,

  author_name VARCHAR(255),
  author_email VARCHAR(255),
  author_when TIMESTAMP,
  author_tz INT,

  committer_name VARCHAR(255),
  committer_email VARCHAR(255),
  committer_when TIMESTAMP,
  committer_tz INT,

  message TEXT,
  patchset_key VARCHAR(255),

  UNIQUE (gae_key)
);
CREATE INDEX revisions_idx1 ON revisions (revision_id);

CREATE TABLE revision_ancestors
(
  gae_key VARCHAR(255) NOT NULL,
  child_id CHAR(40) NOT NULL,
  parent_id CHAR(40) NOT NULL,
  position INT NOT NULL,

  PRIMARY KEY (gae_key, position),

  FOREIGN KEY (gae_key)
  REFERENCES revisions (gae_key)
  ON DELETE CASCADE
);

-- class Change

CREATE TABLE changes
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,
  change_id INT NOT NULL,

  subject VARCHAR(255) NOT NULL,
  description TEXT,
  owner VARCHAR(255) NOT NULL,
  created TIMESTAMP NOT NULL,
  modified TIMESTAMP NOT NULL,
  claimed CHAR(1) NOT NULL DEFAULT 'N' CHECK (claimed IN ('Y','N')),
  closed CHAR(1) NOT NULL DEFAULT 'N' CHECK (closed IN ('Y','N')),
  n_comments INT,
  n_patchsets INT,
  dest_project_key VARCHAR(255) NOT NULL,
  dest_branch_key VARCHAR(255) NOT NULL,
  merge_submitted TIMESTAMP,
  merged CHAR(1) NOT NULL DEFAULT 'N' CHECK (merged IN ('Y','N')),
  emailed_clean_merge CHAR(1) NOT NULL DEFAULT 'N' CHECK (emailed_clean_merge IN ('Y','N')),
  emailed_missing_dependency CHAR(1) NOT NULL DEFAULT 'N' CHECK (emailed_missing_dependency IN ('Y','N')),
  emailed_path_conflict CHAR(1) NOT NULL DEFAULT 'N' CHECK (emailed_path_conflict IN ('Y','N')),
  merge_patchset_key VARCHAR(255),

  PRIMARY KEY (change_id),
  UNIQUE (gae_key)
);

CREATE TABLE change_people
(
  change_id INT NOT NULL,
  email VARCHAR(255) NOT NULL,
  type VARCHAR(8) NOT NULL CHECK (type IN ('reviewer', 'cc')),

  PRIMARY KEY (change_id, type, email),

  FOREIGN KEY (change_id)
  REFERENCES changes (change_id)
  ON DELETE CASCADE
);

-- class PatchSet

CREATE TABLE patch_sets
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  patchset_id INT NOT NULL,
  change_key VARCHAR(255) NOT NULL,
  message VARCHAR(255),
  owner VARCHAR(255) NOT NULL,
  created TIMESTAMP NOT NULL,
  modified TIMESTAMP NOT NULL,
  revision_key VARCHAR(255) NOT NULL,
  complete CHAR(1) NOT NULL DEFAULT 'N' CHECK (complete IN ('Y','N')),

  PRIMARY KEY (change_key, patchset_id),
  UNIQUE (gae_key)
);

-- class Message

CREATE TABLE messages
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  change_key VARCHAR(255) NOT NULL,
  subject VARCHAR(255),
  sender VARCHAR(255),
  date_sent TIMESTAMP,
  body TEXT,

  PRIMARY KEY (gae_key)
);

CREATE TABLE message_recipients
(
  message_key VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,

  PRIMARY KEY (message_key, email),

  FOREIGN KEY (message_key)
  REFERENCES messages (gae_key)
  ON DELETE CASCADE
);

-- class DeltaContent

CREATE TABLE delta_content
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  type VARCHAR(8) NOT NULL CHECK (type IN ('patch','content')),
  hash CHAR(40) NOT NULL,
  data_z BYTEA NOT NULL,
  depth INT NOT NULL,
  base_key VARCHAR(255),

  PRIMARY KEY (type, hash),
  UNIQUE (gae_key)
);

-- class Patch

CREATE TABLE patches
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  patchset_key VARCHAR(255) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  status CHAR(1) NOT NULL CHECK (status IN ('A', 'M', 'D')),
  multi_way_diff CHAR(1) NOT NULL DEFAULT 'N' CHECK (multi_way_diff IN ('Y','N')),
  n_comments INT,
  old_data_key VARCHAR(255),
  new_data_key VARCHAR(255),
  diff_data_key VARCHAR(255),

  PRIMARY KEY (patchset_key, filename),
  UNIQUE(gae_key)
);

-- class Comment

CREATE TABLE comments
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,
  
  patch_key VARCHAR(255) NOT NULL,
  message_id VARCHAR(255) NOT NULL,
  author VARCHAR(255),
  written TIMESTAMP,
  lineno INT,
  body TEXT,
  is_left CHAR(1) NOT NULL DEFAULT 'N' CHECK (is_left IN ('Y','N')),
  draft CHAR(1) NOT NULL DEFAULT 'N' CHECK (draft IN ('Y','N')),

  UNIQUE(gae_key)
);

-- class ReviewStatus

CREATE TABLE review_status
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  change_key VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  lgtm VARCHAR(7) CHECK (lgtm IN ('lgtm',
                                  'yes',
                                  'abstain',
                                  'no',
                                  'reject')),
  verified CHAR(1) CHECK (verified IN ('Y','N')),

  PRIMARY KEY (change_key, email),
  UNIQUE(gae_key)
);

-- class Account

CREATE TABLE accounts
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  user_email VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  preferred_email VARCHAR(255),

  created TIMESTAMP,
  modified TIMESTAMP,

  is_admin CHAR(1) NOT NULL DEFAULT 'N' CHECK (is_admin IN ('Y','N')),
  welcomed CHAR(1) NOT NULL DEFAULT 'N' CHECK (welcomed IN ('Y','N')),
  real_name_entered CHAR(1) NOT NULL DEFAULT 'N' CHECK (real_name_entered IN ('Y','N')),
  real_name VARCHAR(255),
  mailing_address VARCHAR(255),
  mailing_address_country VARCHAR(255),
  phone_number VARCHAR(255),
  fax_number VARCHAR(255),

  cla_verified CHAR(1) NOT NULL DEFAULT 'N' CHECK (cla_verified IN ('Y','N')),
  cla_verified_by VARCHAR(255),
  cla_verified_timestamp TIMESTAMP,
  individual_cla_version INT,
  individual_cla_timestamp TIMESTAMP,
  cla_comments TEXT,

  default_context INT,

  PRIMARY KEY (email),
  UNIQUE(gae_key)
);

CREATE TABLE account_stars
(
  email VARCHAR(255) NOT NULL,
  change_id INT NOT NULL,

  PRIMARY KEY (email, change_id),

  FOREIGN KEY (email)
  REFERENCES accounts (email)
  ON DELETE CASCADE
);

CREATE TABLE account_unclaimed_changes_projects
(
  email VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,

  PRIMARY KEY (email, project_key),

  FOREIGN KEY (email)
  REFERENCES accounts (email)
  ON DELETE CASCADE
);

-- class AccountGroup

CREATE TABLE account_groups
(
  last_backed_up INT NOT NULL DEFAULT 0,
  gae_key VARCHAR(255) NOT NULL,

  name VARCHAR(255) NOT NULL,
  comment TEXT,

  PRIMARY KEY (name),
  UNIQUE(gae_key)
);

CREATE TABLE account_group_users
(
  group_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,

  PRIMARY KEY (group_name, email),

  FOREIGN KEY (group_name)
  REFERENCES account_groups (name)
  ON DELETE CASCADE
);

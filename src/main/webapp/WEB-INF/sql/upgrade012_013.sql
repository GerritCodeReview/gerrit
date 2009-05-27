-- Upgrade: schema_version 11 to 12
--

CREATE SEQUENCE safe_files_id_seq;

CREATE TABLE safe_files
(file_extension VARCHAR(40),
id INTEGER NOT NULL,
PRIMARY KEY(id)
);

ALTER TABLE safe_files OWNER TO gerrit2;

UPDATE schema_version SET version_nbr = 13;

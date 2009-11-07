-- Upgrade: schema_version 5 to 6
--

CREATE TABLE trusted_external_ids
(id_pattern VARCHAR(255) NOT NULL
,PRIMARY KEY (id_pattern)
) WITHOUT OIDS;

INSERT INTO trusted_external_ids VALUES ('http://');
INSERT INTO trusted_external_ids VALUES ('https://');
INSERT INTO trusted_external_ids VALUES ('https://www.google.com/accounts/o8/id?id=');

UPDATE schema_version SET version_nbr = 6;

-- Upgrade: schema_version 14 to 15
--
ALTER TABLE patch_comments ADD parent_uuid VARCHAR(40);

CREATE TABLE account_patch_reviews
(account_id INTEGER NOT NULL DEFAULT(0),
change_id INTEGER NOT NULL DEFAULT(0),
patch_set_id INTEGER NOT NULL DEFAULT(0),
file_name VARCHAR(255) NOT NULL DEFAULT(''),
PRIMARY KEY (account_id, change_id, patch_set_id, file_name)
);

ALTER TABLE account_patch_reviews OWNER TO gerrit2;

UPDATE schema_version SET version_nbr = 15;

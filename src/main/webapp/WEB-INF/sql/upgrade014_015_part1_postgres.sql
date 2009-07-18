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

INSERT INTO approval_categories
(name, position, function_name, category_id)
VALUES
('Owner', -1, 'NoOp', 'OWN');

INSERT INTO approval_category_values
(category_id, value, name)
VALUES
('OWN', 1, 'Administer All Settings');

INSERT INTO project_rights
(project_id, category_id, group_id, min_value, max_value)
SELECT p.project_id, 'OWN', p.owner_group_id, 1, 1
FROM projects p
AND p.project_id <> 0;

DROP INDEX projects_ownedByGroup;
DROP INDEX project_rights_byCat;
DROP INDEX project_rights_byGroup;

CREATE INDEX project_rights_byCatGroup
ON project_rights (category_id, group_id);

UPDATE schema_version SET version_nbr = 15;

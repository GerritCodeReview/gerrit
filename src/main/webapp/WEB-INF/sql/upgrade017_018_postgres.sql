-- Upgrade: schema_version 17 to 18 (PostgreSQL)
--

BEGIN;

SELECT check_schema_version(17);

ALTER TABLE approval_categories ADD abbreviated_name VARCHAR(4);
UPDATE approval_categories SET abbreviated_name = 'V' WHERE category_id = 'VRIF';
UPDATE approval_categories SET abbreviated_name = 'R' WHERE category_id = 'CRVW';

ALTER TABLE approval_categories ADD copy_min_score CHAR(1);
UPDATE approval_categories SET copy_min_score = 'N';
UPDATE approval_categories SET copy_min_score = 'Y' WHERE category_id = 'CRVW';
ALTER TABLE approval_categories ALTER COLUMN copy_min_score SET DEFAULT 'N';
ALTER TABLE approval_categories ALTER COLUMN copy_min_score SET NOT NULL;

UPDATE patch_comments SET
 patch_set_id = (SELECT patch_set_id FROM patch_comments p
                 WHERE p.change_id = patch_comments.change_id
                 AND p.file_name = patch_comments.file_name
                 AND p.uuid = patch_comments.parent_uuid)
WHERE parent_uuid IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM patch_comments p
                WHERE p.change_id = patch_comments.change_id
                AND p.patch_set_id = patch_comments.patch_set_id
                AND p.file_name = patch_comments.file_name
                AND p.uuid = patch_comments.parent_uuid);

DROP TABLE patches;

UPDATE schema_version SET version_nbr = 18;

COMMIT;

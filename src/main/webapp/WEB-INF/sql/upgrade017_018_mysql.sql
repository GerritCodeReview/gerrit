-- Upgrade: schema_version 17 to 18 (MySQL)
--

ALTER TABLE approval_categories ADD abbreviated_name VARCHAR(4);
UPDATE approval_categories SET abbreviated_name = 'V' WHERE category_id = 'VRIF';
UPDATE approval_categories SET abbreviated_name = 'R' WHERE category_id = 'CRVW';

ALTER TABLE approval_categories ADD copy_min_score CHAR(1);
UPDATE approval_categories SET copy_min_score = 'N';
UPDATE approval_categories SET copy_min_score = 'Y' WHERE category_id = 'CRVW';
ALTER TABLE approval_categories MODIFY copy_min_score CHAR(1) DEFAULT 'N' NOT NULL;

CREATE TEMPORARY TABLE copy_patch_comments1 AS SELECT * FROM patch_comments;
CREATE TEMPORARY TABLE copy_patch_comments2 AS SELECT * FROM patch_comments;

UPDATE patch_comments SET
 patch_set_id = (SELECT patch_set_id FROM copy_patch_comments2 p
                 WHERE p.change_id = patch_comments.change_id
                 AND p.file_name = patch_comments.file_name
                 AND p.uuid = patch_comments.parent_uuid)
WHERE parent_uuid IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM copy_patch_comments1 r
                WHERE r.change_id = patch_comments.change_id
                AND r.patch_set_id = patch_comments.patch_set_id
                AND r.file_name = patch_comments.file_name
                AND r.uuid = patch_comments.parent_uuid);

DROP TEMPORARY TABLE copy_patch_comments1;
DROP TEMPORARY TABLE copy_patch_comments2;

DROP TABLE patches;

UPDATE schema_version SET version_nbr = 18;

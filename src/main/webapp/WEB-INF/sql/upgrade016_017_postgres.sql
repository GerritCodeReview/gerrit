-- Upgrade: schema_version 16 to 17 (PostgreSQL)
--

CREATE LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION
check_schema_version (exp INT)
RETURNS VARCHAR(255)
AS $$
DECLARE
  l_act INT;
BEGIN
  SELECT version_nbr INTO l_act
  FROM schema_version;

  IF l_act <> exp
  THEN
    RAISE EXCEPTION 'expected schema %, found %', exp, l_act;
  END IF;
  RETURN 'OK';
END;
$$ LANGUAGE plpgsql;

BEGIN;

SELECT check_schema_version(16);

ALTER TABLE system_config DROP xsrf_private_key;
ALTER TABLE system_config DROP max_session_age;
ALTER TABLE system_config RENAME account_private_key TO register_email_private_key;
ALTER TABLE changes ADD change_key VARCHAR(60);

UPDATE changes SET change_key = 'I' || (SELECT p.revision
  FROM patch_sets p
  WHERE p.change_id = changes.change_id
  AND p.patch_set_id = 1);

CREATE INDEX changes_key
ON changes (change_key);

ALTER TABLE changes ALTER COLUMN change_key SET NOT NULL;

UPDATE schema_version SET version_nbr = 17;

COMMIT;

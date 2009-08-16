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
ALTER TABLE system_config RENAME account_private_key TO register_email_private_key;
UPDATE schema_version SET version_nbr = 17;

COMMIT;

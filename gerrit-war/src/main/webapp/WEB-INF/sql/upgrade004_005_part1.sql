-- Upgrade: schema_version 4 to 5
--

ALTER TABLE accounts ADD contact_filed_on TIMESTAMP WITH TIME ZONE;
UPDATE accounts
  SET contact_filed_on = (SELECT MAX(accepted_on)
                          FROM account_agreements g
                          WHERE g.account_id = accounts.account_id
                          AND g.status <> 'R')
  WHERE full_name IS NOT NULL
    AND full_name <> ''
    AND preferred_email IS NOT null
    AND preferred_email <> ''
    AND contact_address IS NOT NULL
    AND contact_address <> ''
    AND EXISTS (SELECT 1 FROM account_agreements g
                WHERE g.account_id = accounts.account_id
                AND g.status <> 'R');
UPDATE accounts SET contact_filed_on = registered_on
  WHERE full_name IS NOT NULL
    AND full_name <> ''
    AND preferred_email IS NOT null
    AND preferred_email <> ''
    AND contact_address IS NOT NULL
    AND contact_address <> ''
    AND contact_filed_on IS NULL;

ALTER TABLE system_config ADD contact_store_url VARCHAR(255);
ALTER TABLE system_config ADD contact_store_appsec VARCHAR(255);

UPDATE schema_version SET version_nbr = 5;

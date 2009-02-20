-- Upgrade: schema_version 4 to 5 (part 2)
--

ALTER TABLE accounts DROP COLUMN contact_address;
ALTER TABLE accounts DROP COLUMN contact_country;
ALTER TABLE accounts DROP COLUMN contact_phone_nbr;
ALTER TABLE accounts DROP COLUMN contact_fax_nbr;

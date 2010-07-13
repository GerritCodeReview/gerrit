-- Gerrit 2 : MySQL
--
delimiter //

CREATE FUNCTION nextval_account_id ()
  RETURNS BIGINT
  LANGUAGE SQL
  NOT DETERMINISTIC
  MODIFIES SQL DATA
BEGIN
  INSERT INTO account_id (s) VALUES (NULL);
  DELETE FROM account_id WHERE s = LAST_INSERT_ID();
  RETURN LAST_INSERT_ID();
END;
//

ALTER TABLE new_ref_rights ADD UNIQUE (project_name, ref_pattern, category_id, group_id);

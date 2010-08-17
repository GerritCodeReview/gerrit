package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface AccessCategoryAccess extends
    Access<AccessCategory, AccessCategory.Id> {
  @PrimaryKey("accessId")
  AccessCategory get(AccessCategory.Id id) throws OrmException;

  @Query("ORDER BY accessId")
  ResultSet<AccessCategory> all() throws OrmException;
}

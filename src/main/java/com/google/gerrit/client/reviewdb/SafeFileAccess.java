package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

/** Access interface for {@link SafeFile}. */
public interface SafeFileAccess extends Access<SafeFile, SafeFile.Id> {

  @PrimaryKey("id")
  SafeFile get(SafeFile.Id key) throws OrmException;

  @Query("WHERE fileExtension = ?")
  ResultSet<SafeFile> byFileExtension(String fileExtension) throws OrmException;

}

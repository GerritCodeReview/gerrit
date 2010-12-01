package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface AtomicEntryAccess extends Access<AtomicEntry, AtomicEntry.Id>{

  @PrimaryKey("id")
  AtomicEntry get(AtomicEntry.Id id) throws OrmException;

  @Query("WHERE id.superChangeId = ?")
  ResultSet<AtomicEntry> bySuperChangeId(Change.Id id) throws OrmException;

  @Query("WHERE id.sourceSha1 = ?")
  ResultSet<AtomicEntry> bySourceSha1(String sha1) throws OrmException;

  @Query("WHERE id.superChangeId = ? AND id.sourceSha1 = ''")
  ResultSet<AtomicEntry> getNotReceivedSources(Change.Id id) throws OrmException;

  @Query("WHERE sourceChangeId = ? LIMIT 1")
  ResultSet<AtomicEntry> bySourceChangeId(Change.Id id) throws OrmException;
}

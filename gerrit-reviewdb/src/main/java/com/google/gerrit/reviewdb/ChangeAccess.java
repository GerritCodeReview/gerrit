// Copyright (C) 2008 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface ChangeAccess extends Access<Change, Change.Id> {
  @PrimaryKey("changeId")
  Change get(Change.Id id) throws OrmException;

  @Query("WHERE changeKey = ?")
  ResultSet<Change> byKey(Change.Key key) throws OrmException;

  @Query("WHERE changeKey >= ? AND changeKey <= ?")
  ResultSet<Change> byKeyRange(Change.Key reva, Change.Key revb)
      throws OrmException;

  @Query("WHERE dest = ? AND changeKey = ?")
  ResultSet<Change> byBranchKey(Branch.NameKey p, Change.Key key)
      throws OrmException;

  @Query("WHERE dest.projectName = ?")
  ResultSet<Change> byProject(Project.NameKey p) throws OrmException;

  @Query("WHERE dest = ? AND status = '" + Change.STATUS_NEW + "'")
  ResultSet<Change> allNew(Branch.NameKey dest) throws OrmException;

  @Query("WHERE dest = ? AND status = '" + Change.STATUS_NEW
      + "' AND mergeTestStatus != '" + Change.TESTED_IS_MERGEABLE + "'")
  ResultSet<Change> allNewNotMergeable(Branch.NameKey dest)
      throws OrmException;

  @Query("WHERE status = '" + Change.STATUS_NEW + "' AND mergeTestStatus = '"
      + Change.MERGE_TEST_PENDING + "'")
  ResultSet<Change> allNewMergeNotTested() throws OrmException;

  @Query("WHERE owner = ? AND open = true ORDER BY createdOn, changeId")
  ResultSet<Change> byOwnerOpen(Account.Id id) throws OrmException;

  @Query("WHERE owner = ? AND open = false ORDER BY lastUpdatedOn DESC LIMIT 5")
  ResultSet<Change> byOwnerClosed(Account.Id id) throws OrmException;

  @Query("WHERE owner = ? AND open = false ORDER BY lastUpdatedOn")
  ResultSet<Change> byOwnerClosedAll(Account.Id id) throws OrmException;

  @Query("WHERE dest = ? AND status = '" + Change.STATUS_SUBMITTED
      + "' ORDER BY lastUpdatedOn")
  ResultSet<Change> submitted(Branch.NameKey dest) throws OrmException;

  @Query("WHERE status = '" + Change.STATUS_SUBMITTED + "'")
  ResultSet<Change> allSubmitted() throws OrmException;

  @Query("WHERE open = true AND sortKey > ? ORDER BY sortKey LIMIT ?")
  ResultSet<Change> allOpenPrev(String sortKey, int limit) throws OrmException;

  @Query("WHERE open = true AND sortKey < ? ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Change> allOpenNext(String sortKey, int limit) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ?")
  ResultSet<Change> byProjectOpenAll(Project.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest = ?")
  ResultSet<Change> byBranchOpenAll(Branch.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ? AND sortKey > ?"
      + " ORDER BY sortKey LIMIT ?")
  ResultSet<Change> byProjectOpenPrev(Project.NameKey p, String sortKey,
      int limit) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ? AND sortKey < ?"
      + " ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Change> byProjectOpenNext(Project.NameKey p, String sortKey,
      int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND dest.projectName = ? AND sortKey > ?"
      + " ORDER BY sortKey LIMIT ?")
  ResultSet<Change> byProjectClosedPrev(char status, Project.NameKey p,
      String sortKey, int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND dest.projectName = ? AND sortKey < ?"
      + " ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Change> byProjectClosedNext(char status, Project.NameKey p,
      String sortKey, int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND sortKey > ? ORDER BY sortKey LIMIT ?")
  ResultSet<Change> allClosedPrev(char status, String sortKey, int limit)
      throws OrmException;

  @Query("WHERE open = false AND status = ? AND sortKey < ? ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Change> allClosedNext(char status, String sortKey, int limit)
      throws OrmException;

  @Query
  ResultSet<Change> all() throws OrmException;
}

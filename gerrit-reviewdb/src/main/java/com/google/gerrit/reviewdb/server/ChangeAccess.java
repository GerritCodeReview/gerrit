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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface ChangeAccess extends Access<Change, Change.Id> {
  @Override
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

  @Query("WHERE dest = ? AND status = '" + Change.STATUS_SUBMITTED
      + "' ORDER BY lastUpdatedOn")
  ResultSet<Change> submitted(Branch.NameKey dest) throws OrmException;

  @Query("WHERE status = '" + Change.STATUS_SUBMITTED + "'")
  ResultSet<Change> allSubmitted() throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ?")
  ResultSet<Change> byProjectOpenAll(Project.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest = ?")
  ResultSet<Change> byBranchOpenAll(Branch.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ? AND sortKey < ?"
      + " ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Change> byProjectOpenNext(Project.NameKey p, String sortKey,
      int limit) throws OrmException;

  @Query
  ResultSet<Change> all() throws OrmException;
}

// Copyright (C) 2011 The Android Open Source Project
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

public interface TopicAccess extends Access<Topic, Topic.Id> {
  @PrimaryKey("topicId")
  Topic get(Topic.Id id) throws OrmException;

  @Query("WHERE topicKey = ?")
  ResultSet<Topic> byKey(Topic.Key key) throws OrmException;

  @Query("WHERE dest = ? AND topicKey = ?")
  ResultSet<Topic> byBranchKey(Branch.NameKey p, Topic.Key key)
      throws OrmException;

  @Query("WHERE dest = ? AND dest.projectName = ?")
  ResultSet<Topic> byBranchProject(Branch.NameKey p, Project.NameKey k)
      throws OrmException;

  @Query("WHERE dest.projectName = ?")
  ResultSet<Topic> byProject(Project.NameKey p) throws OrmException;

  @Query("WHERE owner = ? AND open = true ORDER BY createdOn, topicId")
  ResultSet<Topic> byOwnerOpen(Account.Id id) throws OrmException;

  @Query("WHERE owner = ? AND open = false ORDER BY lastUpdatedOn DESC LIMIT 5")
  ResultSet<Topic> byOwnerClosed(Account.Id id) throws OrmException;

  @Query("WHERE owner = ? AND open = false ORDER BY lastUpdatedOn")
  ResultSet<Topic> byOwnerClosedAll(Account.Id id) throws OrmException;

  @Query("WHERE dest = ? AND status = '" + Topic.STATUS_SUBMITTED
      + "' ORDER BY lastUpdatedOn")
  ResultSet<Topic> submitted(Branch.NameKey dest) throws OrmException;

  @Query("WHERE status = '" + Topic.STATUS_SUBMITTED + "'")
  ResultSet<Topic> allSubmitted() throws OrmException;

  @Query("WHERE open = true AND sortKey > ? ORDER BY sortKey LIMIT ?")
  ResultSet<Topic> allOpenPrev(String sortKey, int limit) throws OrmException;

  @Query("WHERE open = true AND sortKey < ? ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Topic> allOpenNext(String sortKey, int limit) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ?")
  ResultSet<Topic> byProjectOpenAll(Project.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest = ?")
  ResultSet<Topic> byBranchOpenAll(Branch.NameKey p) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ? AND sortKey > ?"
      + " ORDER BY sortKey LIMIT ?")
  ResultSet<Topic> byProjectOpenPrev(Project.NameKey p, String sortKey,
      int limit) throws OrmException;

  @Query("WHERE open = true AND dest.projectName = ? AND sortKey < ?"
      + " ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Topic> byProjectOpenNext(Project.NameKey p, String sortKey,
      int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND dest.projectName = ? AND sortKey > ?"
      + " ORDER BY sortKey LIMIT ?")
  ResultSet<Topic> byProjectClosedPrev(char status, Project.NameKey p,
      String sortKey, int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND dest.projectName = ? AND sortKey < ?"
      + " ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Topic> byProjectClosedNext(char status, Project.NameKey p,
      String sortKey, int limit) throws OrmException;

  @Query("WHERE open = false AND status = ? AND sortKey > ? ORDER BY sortKey LIMIT ?")
  ResultSet<Topic> allClosedPrev(char status, String sortKey, int limit)
      throws OrmException;

  @Query("WHERE open = false AND status = ? AND sortKey < ? ORDER BY sortKey DESC LIMIT ?")
  ResultSet<Topic> allClosedNext(char status, String sortKey, int limit)
      throws OrmException;

  @Query
  ResultSet<Topic> all() throws OrmException;
}

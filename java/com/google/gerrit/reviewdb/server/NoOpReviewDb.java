// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.Key;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.StatementExecutor;
import java.util.Map;

/** ReviewDb that is disabled. */
public class NoOpReviewDb implements ReviewDb {
  public static class ReviewDbDisabledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private ReviewDbDisabledException() {
      super("ReviewDb has been disabled and should not be used anymore");
    }
  }

  @Override
  public void close() {
    // Do nothing.
  }

  @Override
  public void commit() {
    throw new ReviewDbDisabledException();
  }

  @Override
  public void rollback() {
    throw new ReviewDbDisabledException();
  }

  @Override
  public void updateSchema(StatementExecutor e) {
    throw new ReviewDbDisabledException();
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new ReviewDbDisabledException();
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new ReviewDbDisabledException();
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    return new SchemaVersionAccess() {

      @Override
      public void upsert(Iterable<CurrentSchemaVersion> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<CurrentSchemaVersion> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key, CurrentSchemaVersion>
          toMap(Iterable<CurrentSchemaVersion> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key primaryKey(
          CurrentSchemaVersion entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<CurrentSchemaVersion> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<CurrentSchemaVersion> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<CurrentSchemaVersion, OrmException> getAsync(
          com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<CurrentSchemaVersion> get(
          Iterable<com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(
          Iterable<com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<CurrentSchemaVersion> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key key)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public CurrentSchemaVersion atomicUpdate(
          com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key key,
          AtomicUpdate<CurrentSchemaVersion> update)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public CurrentSchemaVersion get(
          com.google.gerrit.reviewdb.client.CurrentSchemaVersion.Key key) throws OrmException {
        CurrentSchemaVersion version = new CurrentSchemaVersion();
        version.versionNbr = 170;
        return version;
      }
    };
  }

  @Override
  public ChangeAccess changes() {
    return new ChangeAccess() {

      @Override
      public void upsert(Iterable<Change> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<Change> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<Id, Change> toMap(Iterable<Change> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Id primaryKey(Change entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<Change> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<Change> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<Change, OrmException> getAsync(Id key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<Change> get(Iterable<Id> keys) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(Iterable<Id> keys) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<Change> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(Id key) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Change atomicUpdate(Id key, AtomicUpdate<Change> update) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Change get(Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<Change> all() throws OrmException {
        throw new ReviewDbDisabledException();
      }
    };
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    return new PatchSetApprovalAccess() {

      @Override
      public void upsert(Iterable<PatchSetApproval> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<PatchSetApproval> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<Key, PatchSetApproval> toMap(Iterable<PatchSetApproval> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Key primaryKey(PatchSetApproval entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<PatchSetApproval> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<PatchSetApproval, OrmException> getAsync(Key key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> get(Iterable<Key> keys) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(Iterable<Key> keys) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<PatchSetApproval> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(Key key) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchSetApproval atomicUpdate(Key key, AtomicUpdate<PatchSetApproval> update)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchSetApproval get(Key key) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> byPatchSetUser(
          com.google.gerrit.reviewdb.client.PatchSet.Id patchSet,
          com.google.gerrit.reviewdb.client.Account.Id account)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> byPatchSet(
          com.google.gerrit.reviewdb.client.PatchSet.Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> byChange(Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSetApproval> all() throws OrmException {
        throw new ReviewDbDisabledException();
      }
    };
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    return new ChangeMessageAccess() {

      @Override
      public void upsert(Iterable<ChangeMessage> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<ChangeMessage> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<com.google.gerrit.reviewdb.client.ChangeMessage.Key, ChangeMessage> toMap(
          Iterable<ChangeMessage> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public com.google.gerrit.reviewdb.client.ChangeMessage.Key primaryKey(ChangeMessage entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<ChangeMessage> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<ChangeMessage> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<ChangeMessage, OrmException> getAsync(
          com.google.gerrit.reviewdb.client.ChangeMessage.Key key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<ChangeMessage> get(
          Iterable<com.google.gerrit.reviewdb.client.ChangeMessage.Key> keys) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(Iterable<com.google.gerrit.reviewdb.client.ChangeMessage.Key> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<ChangeMessage> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(com.google.gerrit.reviewdb.client.ChangeMessage.Key key)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ChangeMessage atomicUpdate(
          com.google.gerrit.reviewdb.client.ChangeMessage.Key key,
          AtomicUpdate<ChangeMessage> update)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ChangeMessage get(com.google.gerrit.reviewdb.client.ChangeMessage.Key id)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<ChangeMessage> byPatchSet(com.google.gerrit.reviewdb.client.PatchSet.Id id)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<ChangeMessage> byChange(Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<ChangeMessage> all() throws OrmException {
        throw new ReviewDbDisabledException();
      }
    };
  }

  @Override
  public PatchSetAccess patchSets() {
    return new PatchSetAccess() {

      @Override
      public void upsert(Iterable<PatchSet> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<PatchSet> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<com.google.gerrit.reviewdb.client.PatchSet.Id, PatchSet> toMap(
          Iterable<PatchSet> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public com.google.gerrit.reviewdb.client.PatchSet.Id primaryKey(PatchSet entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSet> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<PatchSet> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<PatchSet, OrmException> getAsync(
          com.google.gerrit.reviewdb.client.PatchSet.Id key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSet> get(Iterable<com.google.gerrit.reviewdb.client.PatchSet.Id> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(Iterable<com.google.gerrit.reviewdb.client.PatchSet.Id> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<PatchSet> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(com.google.gerrit.reviewdb.client.PatchSet.Id key)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchSet atomicUpdate(
          com.google.gerrit.reviewdb.client.PatchSet.Id key, AtomicUpdate<PatchSet> update)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchSet get(com.google.gerrit.reviewdb.client.PatchSet.Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSet> byChange(Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchSet> all() throws OrmException {
        throw new ReviewDbDisabledException();
      }
    };
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    return new PatchLineCommentAccess() {

      @Override
      public void upsert(Iterable<PatchLineComment> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void update(Iterable<PatchLineComment> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public Map<com.google.gerrit.reviewdb.client.PatchLineComment.Key, PatchLineComment> toMap(
          Iterable<PatchLineComment> c) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public com.google.gerrit.reviewdb.client.PatchLineComment.Key primaryKey(
          PatchLineComment entity) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> iterateAllEntities() throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void insert(Iterable<PatchLineComment> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public String getRelationName() {
        return "disabled";
      }

      @Override
      public int getRelationID() {
        return 0;
      }

      @Override
      public CheckedFuture<PatchLineComment, OrmException> getAsync(
          com.google.gerrit.reviewdb.client.PatchLineComment.Key key) {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> get(
          Iterable<com.google.gerrit.reviewdb.client.PatchLineComment.Key> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void deleteKeys(Iterable<com.google.gerrit.reviewdb.client.PatchLineComment.Key> keys)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void delete(Iterable<PatchLineComment> instances) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public void beginTransaction(com.google.gerrit.reviewdb.client.PatchLineComment.Key key)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchLineComment atomicUpdate(
          com.google.gerrit.reviewdb.client.PatchLineComment.Key key,
          AtomicUpdate<PatchLineComment> update)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> publishedByPatchSet(
          com.google.gerrit.reviewdb.client.PatchSet.Id patchset) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> publishedByChangeFile(Id id, String file)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public PatchLineComment get(com.google.gerrit.reviewdb.client.PatchLineComment.Key id)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> draftByPatchSetAuthor(
          com.google.gerrit.reviewdb.client.PatchSet.Id patchset,
          com.google.gerrit.reviewdb.client.Account.Id author)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> draftByChangeFileAuthor(
          Id id, String file, com.google.gerrit.reviewdb.client.Account.Id author)
          throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> draftByAuthor(
          com.google.gerrit.reviewdb.client.Account.Id author) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> byPatchSet(
          com.google.gerrit.reviewdb.client.PatchSet.Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> byChange(Id id) throws OrmException {
        throw new ReviewDbDisabledException();
      }

      @Override
      public ResultSet<PatchLineComment> all() throws OrmException {
        throw new ReviewDbDisabledException();
      }
    };
  }

  @Override
  public int nextChangeId() {
    throw new ReviewDbDisabledException();
  }
}

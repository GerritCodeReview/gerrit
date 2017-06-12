// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.StatementExecutor;
import java.util.Map;

public class ReviewDbWrapper implements ReviewDb {
  protected final ReviewDb delegate;

  protected ReviewDbWrapper(ReviewDb delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  public void commit() throws OrmException {
    delegate.commit();
  }

  @Override
  public void rollback() throws OrmException {
    delegate.rollback();
  }

  @Override
  public void updateSchema(StatementExecutor e) throws OrmException {
    delegate.updateSchema(e);
  }

  @Override
  public void pruneSchema(StatementExecutor e) throws OrmException {
    delegate.pruneSchema(e);
  }

  @Override
  public Access<?, ?>[] allRelations() {
    return delegate.allRelations();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    return delegate.schemaVersion();
  }

  @Override
  public SystemConfigAccess systemConfig() {
    return delegate.systemConfig();
  }

  @Override
  public AccountAccess accounts() {
    return delegate.accounts();
  }

  @Override
  public AccountExternalIdAccess accountExternalIds() {
    return delegate.accountExternalIds();
  }

  @Override
  public AccountGroupAccess accountGroups() {
    return delegate.accountGroups();
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    return delegate.accountGroupNames();
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    return delegate.accountGroupMembers();
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    return delegate.accountGroupMembersAudit();
  }

  @Override
  public AccountProjectWatchAccess accountProjectWatches() {
    return delegate.accountProjectWatches();
  }

  @Override
  public ChangeAccess changes() {
    return delegate.changes();
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    return delegate.patchSetApprovals();
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    return delegate.changeMessages();
  }

  @Override
  public PatchSetAccess patchSets() {
    return delegate.patchSets();
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    return delegate.patchComments();
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    return delegate.accountGroupById();
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    return delegate.accountGroupByIdAud();
  }

  @Override
  public int nextAccountId() throws OrmException {
    return delegate.nextAccountId();
  }

  @Override
  public int nextAccountGroupId() throws OrmException {
    return delegate.nextAccountGroupId();
  }

  @Override
  @SuppressWarnings("deprecation")
  public int nextChangeId() throws OrmException {
    return delegate.nextChangeId();
  }

  @Override
  public int nextChangeMessageId() throws OrmException {
    return delegate.nextChangeMessageId();
  }

  public static class ChangeAccessWrapper implements ChangeAccess {
    protected final ChangeAccess delegate;

    protected ChangeAccessWrapper(ChangeAccess delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public String getRelationName() {
      return delegate.getRelationName();
    }

    @Override
    public int getRelationID() {
      return delegate.getRelationID();
    }

    @Override
    public ResultSet<Change> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public Change.Id primaryKey(Change entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<Change.Id, Change> toMap(Iterable<Change> c) {
      return delegate.toMap(c);
    }

    @Override
    public CheckedFuture<Change, OrmException> getAsync(Change.Id key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<Change> get(Iterable<Change.Id> keys) throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<Change> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<Change> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<Change> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<Change.Id> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<Change> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(Change.Id key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public Change atomicUpdate(Change.Id key, AtomicUpdate<Change> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public Change get(Change.Id id) throws OrmException {
      return delegate.get(id);
    }

    @Override
    public ResultSet<Change> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class PatchSetApprovalAccessWrapper implements PatchSetApprovalAccess {
    protected final PatchSetApprovalAccess delegate;

    protected PatchSetApprovalAccessWrapper(PatchSetApprovalAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getRelationName() {
      return delegate.getRelationName();
    }

    @Override
    public int getRelationID() {
      return delegate.getRelationID();
    }

    @Override
    public ResultSet<PatchSetApproval> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public PatchSetApproval.Key primaryKey(PatchSetApproval entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<PatchSetApproval.Key, PatchSetApproval> toMap(Iterable<PatchSetApproval> c) {
      return delegate.toMap(c);
    }

    @Override
    public CheckedFuture<PatchSetApproval, OrmException> getAsync(PatchSetApproval.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<PatchSetApproval> get(Iterable<PatchSetApproval.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<PatchSetApproval> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<PatchSetApproval> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<PatchSetApproval> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<PatchSetApproval.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<PatchSetApproval> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(PatchSetApproval.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public PatchSetApproval atomicUpdate(
        PatchSetApproval.Key key, AtomicUpdate<PatchSetApproval> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public PatchSetApproval get(PatchSetApproval.Key key) throws OrmException {
      return delegate.get(key);
    }

    @Override
    public ResultSet<PatchSetApproval> byChange(Change.Id id) throws OrmException {
      return delegate.byChange(id);
    }

    @Override
    public ResultSet<PatchSetApproval> byPatchSet(PatchSet.Id id) throws OrmException {
      return delegate.byPatchSet(id);
    }

    @Override
    public ResultSet<PatchSetApproval> byPatchSetUser(PatchSet.Id patchSet, Account.Id account)
        throws OrmException {
      return delegate.byPatchSetUser(patchSet, account);
    }
  }

  public static class ChangeMessageAccessWrapper implements ChangeMessageAccess {
    protected final ChangeMessageAccess delegate;

    protected ChangeMessageAccessWrapper(ChangeMessageAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getRelationName() {
      return delegate.getRelationName();
    }

    @Override
    public int getRelationID() {
      return delegate.getRelationID();
    }

    @Override
    public ResultSet<ChangeMessage> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public ChangeMessage.Key primaryKey(ChangeMessage entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<ChangeMessage.Key, ChangeMessage> toMap(Iterable<ChangeMessage> c) {
      return delegate.toMap(c);
    }

    @Override
    public CheckedFuture<ChangeMessage, OrmException> getAsync(ChangeMessage.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<ChangeMessage> get(Iterable<ChangeMessage.Key> keys) throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<ChangeMessage> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<ChangeMessage> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<ChangeMessage> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<ChangeMessage.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<ChangeMessage> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(ChangeMessage.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public ChangeMessage atomicUpdate(ChangeMessage.Key key, AtomicUpdate<ChangeMessage> update)
        throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public ChangeMessage get(ChangeMessage.Key id) throws OrmException {
      return delegate.get(id);
    }

    @Override
    public ResultSet<ChangeMessage> byChange(Change.Id id) throws OrmException {
      return delegate.byChange(id);
    }

    @Override
    public ResultSet<ChangeMessage> byPatchSet(PatchSet.Id id) throws OrmException {
      return delegate.byPatchSet(id);
    }

    @Override
    public ResultSet<ChangeMessage> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class PatchSetAccessWrapper implements PatchSetAccess {
    protected final PatchSetAccess delegate;

    protected PatchSetAccessWrapper(PatchSetAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getRelationName() {
      return delegate.getRelationName();
    }

    @Override
    public int getRelationID() {
      return delegate.getRelationID();
    }

    @Override
    public ResultSet<PatchSet> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public PatchSet.Id primaryKey(PatchSet entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<PatchSet.Id, PatchSet> toMap(Iterable<PatchSet> c) {
      return delegate.toMap(c);
    }

    @Override
    public CheckedFuture<PatchSet, OrmException> getAsync(PatchSet.Id key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<PatchSet> get(Iterable<PatchSet.Id> keys) throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<PatchSet> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<PatchSet> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<PatchSet> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<PatchSet.Id> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<PatchSet> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(PatchSet.Id key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public PatchSet atomicUpdate(PatchSet.Id key, AtomicUpdate<PatchSet> update)
        throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public PatchSet get(PatchSet.Id id) throws OrmException {
      return delegate.get(id);
    }

    @Override
    public ResultSet<PatchSet> byChange(Change.Id id) throws OrmException {
      return delegate.byChange(id);
    }
  }

  public static class PatchLineCommentAccessWrapper implements PatchLineCommentAccess {
    protected PatchLineCommentAccess delegate;

    protected PatchLineCommentAccessWrapper(PatchLineCommentAccess delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getRelationName() {
      return delegate.getRelationName();
    }

    @Override
    public int getRelationID() {
      return delegate.getRelationID();
    }

    @Override
    public ResultSet<PatchLineComment> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public PatchLineComment.Key primaryKey(PatchLineComment entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<PatchLineComment.Key, PatchLineComment> toMap(Iterable<PatchLineComment> c) {
      return delegate.toMap(c);
    }

    @Override
    public CheckedFuture<PatchLineComment, OrmException> getAsync(PatchLineComment.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<PatchLineComment> get(Iterable<PatchLineComment.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<PatchLineComment> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<PatchLineComment> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<PatchLineComment> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<PatchLineComment.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<PatchLineComment> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(PatchLineComment.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public PatchLineComment atomicUpdate(
        PatchLineComment.Key key, AtomicUpdate<PatchLineComment> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public PatchLineComment get(PatchLineComment.Key id) throws OrmException {
      return delegate.get(id);
    }

    @Override
    public ResultSet<PatchLineComment> byChange(Change.Id id) throws OrmException {
      return delegate.byChange(id);
    }

    @Override
    public ResultSet<PatchLineComment> byPatchSet(PatchSet.Id id) throws OrmException {
      return delegate.byPatchSet(id);
    }

    @Override
    public ResultSet<PatchLineComment> publishedByChangeFile(Change.Id id, String file)
        throws OrmException {
      return delegate.publishedByChangeFile(id, file);
    }

    @Override
    public ResultSet<PatchLineComment> publishedByPatchSet(PatchSet.Id patchset)
        throws OrmException {
      return delegate.publishedByPatchSet(patchset);
    }

    @Override
    public ResultSet<PatchLineComment> draftByPatchSetAuthor(
        PatchSet.Id patchset, Account.Id author) throws OrmException {
      return delegate.draftByPatchSetAuthor(patchset, author);
    }

    @Override
    public ResultSet<PatchLineComment> draftByChangeFileAuthor(
        Change.Id id, String file, Account.Id author) throws OrmException {
      return delegate.draftByChangeFileAuthor(id, file, author);
    }

    @Override
    public ResultSet<PatchLineComment> draftByAuthor(Account.Id author) throws OrmException {
      return delegate.draftByAuthor(author);
    }
  }
}

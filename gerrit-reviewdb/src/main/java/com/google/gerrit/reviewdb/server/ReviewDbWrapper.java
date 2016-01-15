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
import com.google.gerrit.reviewdb.client.Change;
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
  public AccountSshKeyAccess accountSshKeys() {
    return delegate.accountSshKeys();
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
  public StarredChangeAccess starredChanges() {
    return delegate.starredChanges();
  }

  @Override
  public AccountProjectWatchAccess accountProjectWatches() {
    return delegate.accountProjectWatches();
  }

  @Override
  public AccountPatchReviewAccess accountPatchReviews() {
    return delegate.accountPatchReviews();
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
  public SubmoduleSubscriptionAccess submoduleSubscriptions() {
    return delegate.submoduleSubscriptions();
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
    public Change atomicUpdate(Change.Id key, AtomicUpdate<Change> update)
        throws OrmException {
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
}

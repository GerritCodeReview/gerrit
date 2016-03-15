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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.StatementExecutor;
import java.util.Map;

public class ReviewDbWrapper implements ReviewDb {
  public static JdbcSchema unwrapJbdcSchema(ReviewDb db) {
    if (db instanceof ReviewDbWrapper) {
      return unwrapJbdcSchema(((ReviewDbWrapper) db).unsafeGetDelegate());
    }
    return (JdbcSchema) db;
  }

  protected final ReviewDb delegate;

  private boolean inTransaction;

  protected ReviewDbWrapper(ReviewDb delegate) {
    this.delegate = requireNonNull(delegate);
  }

  public ReviewDb unsafeGetDelegate() {
    return delegate;
  }

  public boolean inTransaction() {
    return inTransaction;
  }

  public void beginTransaction() {
    inTransaction = true;
  }

  @Override
  public void commit() throws OrmException {
    if (!inTransaction) {
      // This reads a little weird, we're not in a transaction, so why are we calling commit?
      // Because we want to let the underlying ReviewDb do its normal thing in this case (which may
      // be throwing an exception, or not, depending on implementation).
      delegate.commit();
    }
  }

  @Override
  public void rollback() throws OrmException {
    if (inTransaction) {
      inTransaction = false;
    } else {
      // See comment in commit(): we want to let the underlying ReviewDb do its thing.
      delegate.rollback();
    }
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
  @SuppressWarnings("deprecation")
  public int nextAccountId() throws OrmException {
    return delegate.nextAccountId();
  }

  @Override
  @SuppressWarnings("deprecation")
  public int nextAccountGroupId() throws OrmException {
    return delegate.nextAccountGroupId();
  }

  @Override
  @SuppressWarnings("deprecation")
  public int nextChangeId() throws OrmException {
    return delegate.nextChangeId();
  }

  public static class ChangeAccessWrapper implements ChangeAccess {
    protected final ChangeAccess delegate;

    protected ChangeAccessWrapper(ChangeAccess delegate) {
      this.delegate = requireNonNull(delegate);
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

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<Change, OrmException> getAsync(
        Change.Id key) {
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

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchSetApproval, OrmException> getAsync(
        PatchSetApproval.Key key) {
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

    @Override
    public ResultSet<PatchSetApproval> all() throws OrmException {
      return delegate.all();
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

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<ChangeMessage, OrmException> getAsync(
        ChangeMessage.Key key) {
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

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchSet, OrmException> getAsync(
        PatchSet.Id key) {
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

    @Override
    public ResultSet<PatchSet> all() throws OrmException {
      return delegate.all();
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

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchLineComment, OrmException> getAsync(
        PatchLineComment.Key key) {
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

    @Override
    public ResultSet<PatchLineComment> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class AccountGroupAccessWrapper implements AccountGroupAccess {
    protected final AccountGroupAccess delegate;

    protected AccountGroupAccessWrapper(AccountGroupAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroup> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroup.Id primaryKey(AccountGroup entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroup.Id, AccountGroup> toMap(Iterable<AccountGroup> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroup, OrmException> getAsync(
        AccountGroup.Id key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroup> get(Iterable<AccountGroup.Id> keys) throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroup> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroup> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroup> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroup.Id> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroup> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroup.Id key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroup atomicUpdate(AccountGroup.Id key, AtomicUpdate<AccountGroup> update)
        throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroup get(AccountGroup.Id id) throws OrmException {
      return delegate.get(id);
    }

    @Override
    public ResultSet<AccountGroup> byUUID(AccountGroup.UUID uuid) throws OrmException {
      return delegate.byUUID(uuid);
    }

    @Override
    public ResultSet<AccountGroup> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class AccountGroupNameAccessWrapper implements AccountGroupNameAccess {
    protected final AccountGroupNameAccess delegate;

    protected AccountGroupNameAccessWrapper(AccountGroupNameAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroupName> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroup.NameKey primaryKey(AccountGroupName entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroup.NameKey, AccountGroupName> toMap(Iterable<AccountGroupName> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupName, OrmException> getAsync(
        AccountGroup.NameKey key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroupName> get(Iterable<AccountGroup.NameKey> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroupName> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroupName> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroupName> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroup.NameKey> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroupName> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroup.NameKey key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroupName atomicUpdate(
        AccountGroup.NameKey key, AtomicUpdate<AccountGroupName> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroupName get(AccountGroup.NameKey name) throws OrmException {
      return delegate.get(name);
    }

    @Override
    public ResultSet<AccountGroupName> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class AccountGroupMemberAccessWrapper implements AccountGroupMemberAccess {
    protected final AccountGroupMemberAccess delegate;

    protected AccountGroupMemberAccessWrapper(AccountGroupMemberAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroupMember> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroupMember.Key primaryKey(AccountGroupMember entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroupMember.Key, AccountGroupMember> toMap(Iterable<AccountGroupMember> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupMember, OrmException>
        getAsync(AccountGroupMember.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroupMember> get(Iterable<AccountGroupMember.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroupMember> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroupMember> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroupMember> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroupMember.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroupMember> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroupMember.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroupMember atomicUpdate(
        AccountGroupMember.Key key, AtomicUpdate<AccountGroupMember> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroupMember get(AccountGroupMember.Key key) throws OrmException {
      return delegate.get(key);
    }

    @Override
    public ResultSet<AccountGroupMember> byAccount(Account.Id id) throws OrmException {
      return delegate.byAccount(id);
    }

    @Override
    public ResultSet<AccountGroupMember> byGroup(AccountGroup.Id id) throws OrmException {
      return delegate.byGroup(id);
    }
  }

  public static class AccountGroupMemberAuditAccessWrapper
      implements AccountGroupMemberAuditAccess {
    protected final AccountGroupMemberAuditAccess delegate;

    protected AccountGroupMemberAuditAccessWrapper(AccountGroupMemberAuditAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroupMemberAudit> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroupMemberAudit.Key primaryKey(AccountGroupMemberAudit entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroupMemberAudit.Key, AccountGroupMemberAudit> toMap(
        Iterable<AccountGroupMemberAudit> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupMemberAudit, OrmException>
        getAsync(AccountGroupMemberAudit.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> get(Iterable<AccountGroupMemberAudit.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroupMemberAudit> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroupMemberAudit> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroupMemberAudit> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroupMemberAudit.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroupMemberAudit> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroupMemberAudit.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroupMemberAudit atomicUpdate(
        AccountGroupMemberAudit.Key key, AtomicUpdate<AccountGroupMemberAudit> update)
        throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroupMemberAudit get(AccountGroupMemberAudit.Key key) throws OrmException {
      return delegate.get(key);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroupAccount(
        AccountGroup.Id groupId, Account.Id accountId) throws OrmException {
      return delegate.byGroupAccount(groupId, accountId);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroup(AccountGroup.Id groupId) throws OrmException {
      return delegate.byGroup(groupId);
    }
  }

  public static class AccountGroupByIdAccessWrapper implements AccountGroupByIdAccess {
    protected final AccountGroupByIdAccess delegate;

    protected AccountGroupByIdAccessWrapper(AccountGroupByIdAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroupById> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroupById.Key primaryKey(AccountGroupById entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroupById.Key, AccountGroupById> toMap(Iterable<AccountGroupById> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupById, OrmException> getAsync(
        AccountGroupById.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroupById> get(Iterable<AccountGroupById.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroupById> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroupById> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroupById> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroupById.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroupById> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroupById.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroupById atomicUpdate(
        AccountGroupById.Key key, AtomicUpdate<AccountGroupById> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroupById get(AccountGroupById.Key key) throws OrmException {
      return delegate.get(key);
    }

    @Override
    public ResultSet<AccountGroupById> byIncludeUUID(AccountGroup.UUID uuid) throws OrmException {
      return delegate.byIncludeUUID(uuid);
    }

    @Override
    public ResultSet<AccountGroupById> byGroup(AccountGroup.Id id) throws OrmException {
      return delegate.byGroup(id);
    }

    @Override
    public ResultSet<AccountGroupById> all() throws OrmException {
      return delegate.all();
    }
  }

  public static class AccountGroupByIdAudAccessWrapper implements AccountGroupByIdAudAccess {
    protected final AccountGroupByIdAudAccess delegate;

    protected AccountGroupByIdAudAccessWrapper(AccountGroupByIdAudAccess delegate) {
      this.delegate = requireNonNull(delegate);
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
    public ResultSet<AccountGroupByIdAud> iterateAllEntities() throws OrmException {
      return delegate.iterateAllEntities();
    }

    @Override
    public AccountGroupByIdAud.Key primaryKey(AccountGroupByIdAud entity) {
      return delegate.primaryKey(entity);
    }

    @Override
    public Map<AccountGroupByIdAud.Key, AccountGroupByIdAud> toMap(
        Iterable<AccountGroupByIdAud> c) {
      return delegate.toMap(c);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupByIdAud, OrmException>
        getAsync(AccountGroupByIdAud.Key key) {
      return delegate.getAsync(key);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> get(Iterable<AccountGroupByIdAud.Key> keys)
        throws OrmException {
      return delegate.get(keys);
    }

    @Override
    public void insert(Iterable<AccountGroupByIdAud> instances) throws OrmException {
      delegate.insert(instances);
    }

    @Override
    public void update(Iterable<AccountGroupByIdAud> instances) throws OrmException {
      delegate.update(instances);
    }

    @Override
    public void upsert(Iterable<AccountGroupByIdAud> instances) throws OrmException {
      delegate.upsert(instances);
    }

    @Override
    public void deleteKeys(Iterable<AccountGroupByIdAud.Key> keys) throws OrmException {
      delegate.deleteKeys(keys);
    }

    @Override
    public void delete(Iterable<AccountGroupByIdAud> instances) throws OrmException {
      delegate.delete(instances);
    }

    @Override
    public void beginTransaction(AccountGroupByIdAud.Key key) throws OrmException {
      delegate.beginTransaction(key);
    }

    @Override
    public AccountGroupByIdAud atomicUpdate(
        AccountGroupByIdAud.Key key, AtomicUpdate<AccountGroupByIdAud> update) throws OrmException {
      return delegate.atomicUpdate(key, update);
    }

    @Override
    public AccountGroupByIdAud get(AccountGroupByIdAud.Key key) throws OrmException {
      return delegate.get(key);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroupInclude(
        AccountGroup.Id groupId, AccountGroup.UUID incGroupUUID) throws OrmException {
      return delegate.byGroupInclude(groupId, incGroupUUID);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroup(AccountGroup.Id groupId) throws OrmException {
      return delegate.byGroup(groupId);
    }
  }
}

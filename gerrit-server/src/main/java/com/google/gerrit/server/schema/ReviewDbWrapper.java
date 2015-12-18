// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.server.AccountAccess;
import com.google.gerrit.reviewdb.server.AccountExternalIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAudAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAuditAccess;
import com.google.gerrit.reviewdb.server.AccountGroupNameAccess;
import com.google.gerrit.reviewdb.server.AccountPatchReviewAccess;
import com.google.gerrit.reviewdb.server.AccountProjectWatchAccess;
import com.google.gerrit.reviewdb.server.AccountSshKeyAccess;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SchemaVersionAccess;
import com.google.gerrit.reviewdb.server.StarredChangeAccess;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.reviewdb.server.SystemConfigAccess;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewDbWrapper implements ReviewDb {
  private final ReviewDb db;

  public SchemaVersionAccess schemaVersion() {
    return db.schemaVersion();
  }

  public SystemConfigAccess systemConfig() {
    return db.systemConfig();
  }

  public AccountAccess accounts() {
    return db.accounts();
  }

  public AccountExternalIdAccess accountExternalIds() {
    return db.accountExternalIds();
  }

  public AccountSshKeyAccess accountSshKeys() {
    return db.accountSshKeys();
  }

  public AccountGroupAccess accountGroups() {
    return db.accountGroups();
  }

  public AccountGroupNameAccess accountGroupNames() {
    return db.accountGroupNames();
  }

  public AccountGroupMemberAccess accountGroupMembers() {
    return db.accountGroupMembers();
  }

  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    return db.accountGroupMembersAudit();
  }

  public StarredChangeAccess starredChanges() {
    return db.starredChanges();
  }

  public AccountProjectWatchAccess accountProjectWatches() {
    return db.accountProjectWatches();
  }

  public void commit() throws OrmException {
    db.commit();
  }

  public AccountPatchReviewAccess accountPatchReviews() {
    return db.accountPatchReviews();
  }

  public void rollback() throws OrmException {
    db.rollback();
  }

  public ChangeAccess changes() {
    return new ChangeAccessWrapper(db.changes());
  }

  public PatchSetApprovalAccess patchSetApprovals() {
    return db.patchSetApprovals();
  }

  public void updateSchema(StatementExecutor e) throws OrmException {
    db.updateSchema(e);
  }

  public ChangeMessageAccess changeMessages() {
    return db.changeMessages();
  }

  public PatchSetAccess patchSets() {
    return new PatchSetAccessWrapper(db.patchSets());
  }

  public PatchLineCommentAccess patchComments() {
    return new PatchLineCommentAccessWrapper(db.patchComments());
  }

  public SubmoduleSubscriptionAccess submoduleSubscriptions() {
    return db.submoduleSubscriptions();
  }

  public AccountGroupByIdAccess accountGroupById() {
    return db.accountGroupById();
  }

  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    return db.accountGroupByIdAud();
  }

  public int nextAccountId() throws OrmException {
    return db.nextAccountId();
  }

  public void pruneSchema(StatementExecutor e) throws OrmException {
    db.pruneSchema(e);
  }

  public int nextAccountGroupId() throws OrmException {
    return db.nextAccountGroupId();
  }

  public int nextChangeId() throws OrmException {
    return db.nextChangeId();
  }

  public int nextChangeMessageId() throws OrmException {
    return db.nextChangeMessageId();
  }

  public Access<?, ?>[] allRelations() {
    return db.allRelations();
  }

  public void close() {
    db.close();
  }

  public ReviewDbWrapper(ReviewDb db) {
    this.db = db;
  }

  @Override
  public JdbcSchema asJdbcSchema() {
    return (JdbcSchema) db;
  }
}

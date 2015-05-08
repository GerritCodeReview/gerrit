package com.google.gerrit.acceptance;

import com.google.gerrit.reviewdb.server.AccountAccess;
import com.google.gerrit.reviewdb.server.AccountDiffPreferenceAccess;
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
import com.google.gerrit.reviewdb.server.PatchSetAncestorAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SchemaVersionAccess;
import com.google.gerrit.reviewdb.server.StarredChangeAccess;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.reviewdb.server.SystemConfigAccess;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.StatementExecutor;

/** ReviewDb that is disabled for testing. */
class DisabledReviewDb implements ReviewDb {
  private static final String MESSAGE = "ReviewDb is disabled for this test";

  @Override
  public void close() {
    // Do nothing.
  }

  @Override
  public void commit() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void rollback() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void updateSchema(StatementExecutor e) {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SystemConfigAccess systemConfig() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountAccess accounts() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountExternalIdAccess accountExternalIds() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountSshKeyAccess accountSshKeys() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupAccess accountGroups() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountDiffPreferenceAccess accountDiffPreferences() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public StarredChangeAccess starredChanges() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountProjectWatchAccess accountProjectWatches() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountPatchReviewAccess accountPatchReviews() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public ChangeAccess changes() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchSetAccess patchSets() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchSetAncestorAccess patchSetAncestors() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SubmoduleSubscriptionAccess submoduleSubscriptions() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextAccountId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextAccountGroupId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextChangeId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextChangeMessageId() {
    throw new AssertionError(MESSAGE);
  }
}

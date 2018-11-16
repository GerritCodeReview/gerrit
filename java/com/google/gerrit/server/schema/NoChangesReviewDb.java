// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.StatementExecutor;

/**
 * Wrapper for ReviewDb that never calls the underlying change tables.
 *
 * <p>See {@link NotesMigrationSchemaFactory} for discussion.
 */
class NoChangesReviewDb implements ReviewDb {
  private static final String GONE = "ReviewDb is gone";

  private static <T> ResultSet<T> empty() {
    return new ListResultSet<>(ImmutableList.of());
  }

  private final ChangeAccess changes;
  private final PatchSetApprovalAccess patchSetApprovals;
  private final ChangeMessageAccess changeMessages;
  private final PatchSetAccess patchSets;
  private final PatchLineCommentAccess patchComments;

  NoChangesReviewDb() {
    changes = new Changes(this);
    patchSetApprovals = new PatchSetApprovals(this);
    changeMessages = new ChangeMessages(this);
    patchSets = new PatchSets(this);
    patchComments = new PatchLineComments(this);
  }

  @Override
  public ChangeAccess changes() {
    return changes;
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    return patchSetApprovals;
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    return changeMessages;
  }

  @Override
  public PatchSetAccess patchSets() {
    return patchSets;
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    return patchComments;
  }

  @Override
  public int nextAccountId() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public int nextAccountGroupId() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public int nextChangeId() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public void commit() {}

  @Override
  public void rollback() {}

  @Override
  public void updateSchema(StatementExecutor e) {

    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public void close() {}

  private static class Changes extends AbstractDisabledAccess<Change, Change.Id>
      implements ChangeAccess {
    private Changes(NoChangesReviewDb wrapper) {
      super(wrapper);
    }

    @Override
    public ResultSet<Change> all() {
      return empty();
    }
  }

  private static class ChangeMessages
      extends AbstractDisabledAccess<ChangeMessage, ChangeMessage.Key>
      implements ChangeMessageAccess {
    private ChangeMessages(NoChangesReviewDb wrapper) {
      super(wrapper);
    }

    @Override
    public ResultSet<ChangeMessage> byChange(Change.Id id) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<ChangeMessage> byPatchSet(PatchSet.Id id) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<ChangeMessage> all() throws OrmException {
      return empty();
    }
  }

  private static class PatchSets extends AbstractDisabledAccess<PatchSet, PatchSet.Id>
      implements PatchSetAccess {
    private PatchSets(NoChangesReviewDb wrapper) {
      super(wrapper);
    }

    @Override
    public ResultSet<PatchSet> byChange(Change.Id id) {
      return empty();
    }

    @Override
    public ResultSet<PatchSet> all() {
      return empty();
    }
  }

  private static class PatchSetApprovals
      extends AbstractDisabledAccess<PatchSetApproval, PatchSetApproval.Key>
      implements PatchSetApprovalAccess {
    private PatchSetApprovals(NoChangesReviewDb wrapper) {
      super(wrapper);
    }

    @Override
    public ResultSet<PatchSetApproval> byChange(Change.Id id) {
      return empty();
    }

    @Override
    public ResultSet<PatchSetApproval> byPatchSet(PatchSet.Id id) {
      return empty();
    }

    @Override
    public ResultSet<PatchSetApproval> byPatchSetUser(PatchSet.Id patchSet, Account.Id account) {
      return empty();
    }

    @Override
    public ResultSet<PatchSetApproval> all() {
      return empty();
    }
  }

  private static class PatchLineComments
      extends AbstractDisabledAccess<PatchLineComment, PatchLineComment.Key>
      implements PatchLineCommentAccess {
    private PatchLineComments(NoChangesReviewDb wrapper) {
      super(wrapper);
    }

    @Override
    public ResultSet<PatchLineComment> byChange(Change.Id id) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> byPatchSet(PatchSet.Id id) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> publishedByChangeFile(Change.Id id, String file) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> publishedByPatchSet(PatchSet.Id patchset) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> draftByPatchSetAuthor(
        PatchSet.Id patchset, Account.Id author) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> draftByChangeFileAuthor(
        Change.Id id, String file, Account.Id author) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> draftByAuthor(Account.Id author) {
      return empty();
    }

    @Override
    public ResultSet<PatchLineComment> all() {
      return empty();
    }
  }
}

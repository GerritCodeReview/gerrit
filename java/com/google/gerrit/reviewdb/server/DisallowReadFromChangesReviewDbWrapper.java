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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

public class DisallowReadFromChangesReviewDbWrapper extends ReviewDbWrapper {
  private static final String MSG = "This table has been migrated to NoteDb";

  private final Changes changes;
  private final PatchSetApprovals patchSetApprovals;
  private final ChangeMessages changeMessages;
  private final PatchSets patchSets;
  private final PatchLineComments patchComments;

  public DisallowReadFromChangesReviewDbWrapper(ReviewDb db) {
    super(db);
    changes = new Changes(delegate.changes());
    patchSetApprovals = new PatchSetApprovals(delegate.patchSetApprovals());
    changeMessages = new ChangeMessages(delegate.changeMessages());
    patchSets = new PatchSets(delegate.patchSets());
    patchComments = new PatchLineComments(delegate.patchComments());
  }

  public ReviewDb unsafeGetDelegate() {
    return delegate;
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

  private static class Changes extends ChangeAccessWrapper {

    protected Changes(ChangeAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<Change> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<Change, OrmException> getAsync(
        Change.Id key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<Change> get(Iterable<Change.Id> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public Change get(Change.Id id) throws OrmException {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<Change> all() throws OrmException {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class PatchSetApprovals extends PatchSetApprovalAccessWrapper {
    PatchSetApprovals(PatchSetApprovalAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchSetApproval> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchSetApproval, OrmException> getAsync(
        PatchSetApproval.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSetApproval> get(Iterable<PatchSetApproval.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public PatchSetApproval get(PatchSetApproval.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSetApproval> byChange(Change.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSetApproval> byPatchSet(PatchSet.Id id) {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class ChangeMessages extends ChangeMessageAccessWrapper {
    ChangeMessages(ChangeMessageAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<ChangeMessage> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<ChangeMessage, OrmException> getAsync(
        ChangeMessage.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<ChangeMessage> get(Iterable<ChangeMessage.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ChangeMessage get(ChangeMessage.Key id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<ChangeMessage> byChange(Change.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<ChangeMessage> byPatchSet(PatchSet.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<ChangeMessage> all() {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class PatchSets extends PatchSetAccessWrapper {
    PatchSets(PatchSetAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchSet> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchSet, OrmException> getAsync(
        PatchSet.Id key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSet> get(Iterable<PatchSet.Id> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public PatchSet get(PatchSet.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSet> byChange(Change.Id id) {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class PatchLineComments extends PatchLineCommentAccessWrapper {
    PatchLineComments(PatchLineCommentAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchLineComment> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<PatchLineComment, OrmException> getAsync(
        PatchLineComment.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> get(Iterable<PatchLineComment.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public PatchLineComment get(PatchLineComment.Key id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> byChange(Change.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> byPatchSet(PatchSet.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> publishedByChangeFile(Change.Id id, String file) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> publishedByPatchSet(PatchSet.Id patchset) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByPatchSetAuthor(
        PatchSet.Id patchset, Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByChangeFileAuthor(
        Change.Id id, String file, Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByAuthor(Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }
  }
}

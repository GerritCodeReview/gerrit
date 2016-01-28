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

package com.google.gerrit.server.schema;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper.ChangeMessageAccessWrapper;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper.PatchLineCommentAccessWrapper;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper.PatchSetAccessWrapper;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper.PatchSetApprovalAccessWrapper;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class NotesMigrationSchemaFactory implements SchemaFactory<ReviewDb> {
  private static final String MSG = "This table has been migrated to notedb";

  private final SchemaFactory<ReviewDb> delegate;
  private final boolean disableChangesTables;

  @Inject
  NotesMigrationSchemaFactory(
      @ReviewDbFactory SchemaFactory<ReviewDb> delegate,
      NotesMigration migration) {
    this.delegate = delegate;
    disableChangesTables = migration.readChanges();
  }

  @Override
  public ReviewDb open() throws OrmException {
    ReviewDb db = delegate.open();
    if (!disableChangesTables) {
      return db;
    }
    return new DisabledChangesWrapper(db);
  }

  private static class DisabledChangesWrapper extends ReviewDbWrapper {
    DisabledChangesWrapper(ReviewDb db) {
      super(db);
    }

    @Override
    public PatchSetApprovalAccess patchSetApprovals() {
      return new DisabledPatchSetApprovalAccess(delegate.patchSetApprovals());
    }

    @Override
    public ChangeMessageAccess changeMessages() {
      return new DisabledChangeMessageAccess(delegate.changeMessages());
    }

    @Override
    public PatchSetAccess patchSets() {
      return new DisabledPatchSetAccess(delegate.patchSets());
    }

    @Override
    public PatchLineCommentAccess patchComments() {
      return new DisabledPatchLineCommentAccess(delegate.patchComments());
    }
  }

  private static class DisabledPatchSetApprovalAccess
      extends PatchSetApprovalAccessWrapper {
    DisabledPatchSetApprovalAccess(PatchSetApprovalAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchSetApproval> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public CheckedFuture<PatchSetApproval, OrmException> getAsync(
        PatchSetApproval.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchSetApproval> get(
        Iterable<PatchSetApproval.Key> keys) {
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

  private static class DisabledChangeMessageAccess
      extends ChangeMessageAccessWrapper {
    DisabledChangeMessageAccess(ChangeMessageAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<ChangeMessage> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public CheckedFuture<ChangeMessage, OrmException> getAsync(
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

  private static class DisabledPatchSetAccess extends PatchSetAccessWrapper {
    DisabledPatchSetAccess(PatchSetAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchSet> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public CheckedFuture<PatchSet, OrmException> getAsync(PatchSet.Id key) {
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

  private static class DisabledPatchLineCommentAccess
      extends PatchLineCommentAccessWrapper {
    DisabledPatchLineCommentAccess(PatchLineCommentAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<PatchLineComment> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public CheckedFuture<PatchLineComment, OrmException> getAsync(
        PatchLineComment.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> get(
        Iterable<PatchLineComment.Key> keys) {
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
    public ResultSet<PatchLineComment> publishedByChangeFile(Change.Id id,
        String file) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> publishedByPatchSet(
        PatchSet.Id patchset) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByPatchSetAuthor(
        PatchSet.Id patchset, Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByChangeFileAuthor(Change.Id id,
        String file, Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<PatchLineComment> draftByAuthor(Account.Id author) {
      throw new UnsupportedOperationException(MSG);
    }
  }
}

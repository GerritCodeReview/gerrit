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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
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
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gwtorm.client.Key;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.util.Map;
import java.util.function.Function;

/**
 * Wrapper for ReviewDb that never calls the underlying change tables.
 *
 * <p>See {@link NotesMigrationSchemaFactory} for discussion.
 */
class NoChangesReviewDbWrapper extends ReviewDbWrapper {
  private static <T> ResultSet<T> empty() {
    return new ListResultSet<>(ImmutableList.of());
  }

  @SuppressWarnings("deprecation")
  private static <T, K extends Key<?>>
      com.google.common.util.concurrent.CheckedFuture<T, OrmException> emptyFuture() {
    return Futures.immediateCheckedFuture(null);
  }

  private final ChangeAccess changes;
  private final PatchSetApprovalAccess patchSetApprovals;
  private final ChangeMessageAccess changeMessages;
  private final PatchSetAccess patchSets;
  private final PatchLineCommentAccess patchComments;

  private boolean inTransaction;

  NoChangesReviewDbWrapper(ReviewDb db) {
    super(db);
    changes = new Changes(this, delegate);
    patchSetApprovals = new PatchSetApprovals(this, delegate);
    changeMessages = new ChangeMessages(this, delegate);
    patchSets = new PatchSets(this, delegate);
    patchComments = new PatchLineComments(this, delegate);
  }

  @Override
  public boolean changesTablesEnabled() {
    return false;
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

  private abstract static class AbstractDisabledAccess<T, K extends Key<?>>
      implements Access<T, K> {
    // Don't even hold a reference to delegate, so it's not possible to use it accidentally.
    private final NoChangesReviewDbWrapper wrapper;
    private final String relationName;
    private final int relationId;
    private final Function<T, K> primaryKey;
    private final Function<Iterable<T>, Map<K, T>> toMap;

    private AbstractDisabledAccess(NoChangesReviewDbWrapper wrapper, Access<T, K> delegate) {
      this.wrapper = wrapper;
      this.relationName = delegate.getRelationName();
      this.relationId = delegate.getRelationID();
      this.primaryKey = delegate::primaryKey;
      this.toMap = delegate::toMap;
    }

    @Override
    public final int getRelationID() {
      return relationId;
    }

    @Override
    public final String getRelationName() {
      return relationName;
    }

    @Override
    public final K primaryKey(T entity) {
      return primaryKey.apply(entity);
    }

    @Override
    public final Map<K, T> toMap(Iterable<T> iterable) {
      return toMap.apply(iterable);
    }

    @Override
    public final ResultSet<T> iterateAllEntities() {
      return empty();
    }

    @SuppressWarnings("deprecation")
    @Override
    public final com.google.common.util.concurrent.CheckedFuture<T, OrmException> getAsync(K key) {
      return emptyFuture();
    }

    @Override
    public final ResultSet<T> get(Iterable<K> keys) {
      return empty();
    }

    @Override
    public final void insert(Iterable<T> instances) {
      // Do nothing.
    }

    @Override
    public final void update(Iterable<T> instances) {
      // Do nothing.
    }

    @Override
    public final void upsert(Iterable<T> instances) {
      // Do nothing.
    }

    @Override
    public final void deleteKeys(Iterable<K> keys) {
      // Do nothing.
    }

    @Override
    public final void delete(Iterable<T> instances) {
      // Do nothing.
    }

    @Override
    public final void beginTransaction(K key) {
      // Keep track of when we've started a transaction so that we can avoid calling commit/rollback
      // on the underlying ReviewDb. This is just a simple arm's-length approach, and may produce
      // slightly different results from a native ReviewDb in corner cases like:
      //  * beginning transactions on different tables simultaneously
      //  * doing work between commit and rollback
      // These kinds of things are already misuses of ReviewDb, and shouldn't be happening in
      // current code anyway.
      checkState(!wrapper.inTransaction, "already in transaction");
      wrapper.inTransaction = true;
    }

    @Override
    public final T atomicUpdate(K key, AtomicUpdate<T> update) {
      return null;
    }

    @Override
    public final T get(K id) {
      return null;
    }
  }

  private static class Changes extends AbstractDisabledAccess<Change, Change.Id>
      implements ChangeAccess {
    private Changes(NoChangesReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.changes());
    }

    @Override
    public ResultSet<Change> all() {
      return empty();
    }
  }

  private static class ChangeMessages
      extends AbstractDisabledAccess<ChangeMessage, ChangeMessage.Key>
      implements ChangeMessageAccess {
    private ChangeMessages(NoChangesReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.changeMessages());
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
    private PatchSets(NoChangesReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.patchSets());
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
    private PatchSetApprovals(NoChangesReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.patchSetApprovals());
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
    private PatchLineComments(NoChangesReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.patchComments());
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

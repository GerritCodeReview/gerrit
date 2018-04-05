// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Utility functions to manipulate change edits.
 *
 * <p>This class contains methods to retrieve, publish and delete edits. For changing edits see
 * {@link ChangeEditModifier}.
 */
@Singleton
public class ChangeEditUtil {
  private final GitRepositoryManager gitManager;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeIndexer indexer;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> userProvider;
  private final ChangeKindCache changeKindCache;
  private final PatchSetUtil psUtil;

  @Inject
  ChangeEditUtil(
      GitRepositoryManager gitManager,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeIndexer indexer,
      Provider<ReviewDb> db,
      Provider<CurrentUser> userProvider,
      ChangeKindCache changeKindCache,
      PatchSetUtil psUtil) {
    this.gitManager = gitManager;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.indexer = indexer;
    this.db = db;
    this.userProvider = userProvider;
    this.changeKindCache = changeKindCache;
    this.psUtil = psUtil;
  }

  /**
   * Retrieve edit for a given change.
   *
   * <p>At most one change edit can exist per user and change.
   *
   * @param notes change notes of change to retrieve change edits for.
   * @return edit for this change for this user, if present.
   * @throws AuthException if this is not a logged-in user.
   * @throws IOException if an error occurs.
   */
  public Optional<ChangeEdit> byChange(ChangeNotes notes) throws AuthException, IOException {
    return byChange(notes, userProvider.get());
  }

  /**
   * Retrieve edit for a change and the given user.
   *
   * <p>At most one change edit can exist per user and change.
   *
   * @param notes change notes of change to retrieve change edits for.
   * @param user user to retrieve edits as.
   * @return edit for this change for this user, if present.
   * @throws AuthException if this is not a logged-in user.
   * @throws IOException if an error occurs.
   */
  public Optional<ChangeEdit> byChange(ChangeNotes notes, CurrentUser user)
      throws AuthException, IOException {
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser u = user.asIdentifiedUser();
    Change change = notes.getChange();
    try (Repository repo = gitManager.openRepository(change.getProject())) {
      int n = change.currentPatchSetId().get();
      String[] refNames = new String[n];
      for (int i = n; i > 0; i--) {
        refNames[i - 1] =
            RefNames.refsEdit(u.getAccountId(), change.getId(), new PatchSet.Id(change.getId(), i));
      }
      Ref ref = repo.getRefDatabase().firstExactRef(refNames);
      if (ref == null) {
        return Optional.empty();
      }
      try (RevWalk rw = new RevWalk(repo)) {
        RevCommit commit = rw.parseCommit(ref.getObjectId());
        PatchSet basePs = getBasePatchSet(notes, ref);
        return Optional.of(new ChangeEdit(change, ref.getName(), commit, basePs));
      }
    }
  }

  /**
   * Promote change edit to patch set, by squashing the edit into its parent.
   *
   * @param updateFactory factory for creating updates.
   * @param notes the {@code ChangeNotes} of the change to which the change edit belongs
   * @param user the current user
   * @param edit change edit to publish
   * @param notify Notify handling that defines to whom email notifications should be sent after the
   *     change edit is published.
   * @param accountsToNotify Accounts that should be notified after the change edit is published.
   * @throws IOException
   * @throws OrmException
   * @throws UpdateException
   * @throws RestApiException
   */
  public void publish(
      BatchUpdate.Factory updateFactory,
      ChangeNotes notes,
      CurrentUser user,
      final ChangeEdit edit,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws IOException, OrmException, RestApiException, UpdateException {
    Change change = edit.getChange();
    try (Repository repo = gitManager.openRepository(change.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      PatchSet basePatchSet = edit.getBasePatchSet();
      if (!basePatchSet.getId().equals(change.currentPatchSetId())) {
        throw new ResourceConflictException("only edit for current patch set can be published");
      }

      RevCommit squashed = squashEdit(rw, oi, edit.getEditCommit(), basePatchSet);
      PatchSet.Id psId = ChangeUtil.nextPatchSetId(repo, change.currentPatchSetId());
      PatchSetInserter inserter =
          patchSetInserterFactory
              .create(notes, psId, squashed)
              .setNotify(notify)
              .setAccountsToNotify(accountsToNotify);

      StringBuilder message =
          new StringBuilder("Patch Set ").append(inserter.getPatchSetId().get()).append(": ");

      // Previously checked that the base patch set is the current patch set.
      ObjectId prior = ObjectId.fromString(basePatchSet.getRevision().get());
      ChangeKind kind =
          changeKindCache.getChangeKind(change.getProject(), rw, repo.getConfig(), prior, squashed);
      if (kind == ChangeKind.NO_CODE_CHANGE) {
        message.append("Commit message was updated.");
        inserter.setDescription("Edit commit message");
      } else {
        message
            .append("Published edit on patch set ")
            .append(basePatchSet.getPatchSetId())
            .append(".");
      }

      try (BatchUpdate bu =
          updateFactory.create(db.get(), change.getProject(), user, TimeUtil.nowTs())) {
        bu.setRepository(repo, rw, oi);
        bu.addOp(change.getId(), inserter.setMessage(message.toString()));
        bu.addOp(
            change.getId(),
            new BatchUpdateOp() {
              @Override
              public void updateRepo(RepoContext ctx) throws Exception {
                ctx.addRefUpdate(edit.getEditCommit().copy(), ObjectId.zeroId(), edit.getRefName());
              }
            });
        bu.execute();
      }
    }
  }

  /**
   * Delete change edit.
   *
   * @param edit change edit to delete
   * @throws IOException
   * @throws OrmException
   */
  public void delete(ChangeEdit edit) throws IOException, OrmException {
    Change change = edit.getChange();
    try (Repository repo = gitManager.openRepository(change.getProject())) {
      deleteRef(repo, edit);
    }
    indexer.index(db.get(), change);
  }

  private PatchSet getBasePatchSet(ChangeNotes notes, Ref ref) throws IOException {
    try {
      int pos = ref.getName().lastIndexOf('/');
      checkArgument(pos > 0, "invalid edit ref: %s", ref.getName());
      String psId = ref.getName().substring(pos + 1);
      return psUtil.get(
          db.get(), notes, new PatchSet.Id(notes.getChange().getId(), Integer.parseInt(psId)));
    } catch (OrmException | NumberFormatException e) {
      throw new IOException(e);
    }
  }

  private RevCommit squashEdit(
      RevWalk rw, ObjectInserter inserter, RevCommit edit, PatchSet basePatchSet)
      throws IOException, ResourceConflictException {
    RevCommit parent = rw.parseCommit(ObjectId.fromString(basePatchSet.getRevision().get()));
    if (parent.getTree().equals(edit.getTree())
        && edit.getFullMessage().equals(parent.getFullMessage())) {
      throw new ResourceConflictException("identical tree and message");
    }
    return writeSquashedCommit(rw, inserter, parent, edit);
  }

  private static void deleteRef(Repository repo, ChangeEdit edit) throws IOException {
    String refName = edit.getRefName();
    RefUpdate ru = repo.updateRef(refName, true);
    ru.setExpectedOldObjectId(edit.getEditCommit());
    ru.setForceUpdate(true);
    RefUpdate.Result result = ru.delete();
    switch (result) {
      case FORCED:
      case NEW:
      case NO_CHANGE:
        break;
      case FAST_FORWARD:
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        throw new IOException(String.format("Failed to delete ref %s: %s", refName, result));
    }
  }

  private static RevCommit writeSquashedCommit(
      RevWalk rw, ObjectInserter inserter, RevCommit parent, RevCommit edit) throws IOException {
    CommitBuilder mergeCommit = new CommitBuilder();
    for (int i = 0; i < parent.getParentCount(); i++) {
      mergeCommit.addParentId(parent.getParent(i));
    }
    mergeCommit.setAuthor(parent.getAuthorIdent());
    mergeCommit.setMessage(edit.getFullMessage());
    mergeCommit.setCommitter(edit.getCommitterIdent());
    mergeCommit.setTreeId(edit.getTree());

    return rw.parseCommit(commit(inserter, mergeCommit));
  }

  private static ObjectId commit(ObjectInserter inserter, CommitBuilder mergeCommit)
      throws IOException {
    ObjectId id = inserter.insert(mergeCommit);
    inserter.flush();
    return id;
  }
}

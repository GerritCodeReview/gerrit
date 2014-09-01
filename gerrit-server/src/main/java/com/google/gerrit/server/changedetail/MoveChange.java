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


package com.google.gerrit.server.changedetail;

import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

public class MoveChange implements Callable<Void> {

  public interface Factory {
    MoveChange create(@Assisted Change.Id changeId,
        @Assisted("branch") String branch,
        @Assisted("message") String changeComment);
  }

  private final ChangeControl.GenericFactory changeControlFactory;
  private final GitRepositoryManager repoManager;
  private final Provider<GerritApi> gApi;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<CurrentUser> userProvider;
  private final ReviewDb db;

  private final Change.Id changeId;
  private final String dest;
  private final String changeComment;

  @Inject
  MoveChange(final ChangeControl.GenericFactory changeControlFactory,
      final GitRepositoryManager repoManager,
      final Provider<GerritApi> gApi,
      final Provider<InternalChangeQuery> queryProvider,
      final Provider<CurrentUser> userProvider, final ReviewDb db,
      @Assisted final Change.Id changeId,
      @Assisted("branch") final String branch,
      @Assisted("message") @Nullable final String changeComment) {
    this.changeControlFactory = changeControlFactory;
    this.repoManager = repoManager;
    this.gApi = gApi;
    this.queryProvider = queryProvider;
    this.userProvider = userProvider;
    this.db = db;

    this.changeId = changeId;
    dest =
        (branch.startsWith(Constants.R_REFS) ? "" : Constants.R_HEADS) + branch;
    this.changeComment = changeComment;
  }

  @Override
  public Void call() throws InvalidChangeOperationException, IOException,
      NoSuchChangeException, NoSuchRefException, OrmException,
      RepositoryNotFoundException, RestApiException {
    ChangeControl control = changeControlFactory.validateFor(changeId,
        userProvider.get());
    if (!control.canMoveTo(dest)) {
      throw new InvalidChangeOperationException("Permission denied moving"
          + " change " + changeId + "'s destination branch to " + dest);
    }

    Change change = db.changes().get(changeId);

    Project.NameKey projectKey = change.getProject();
    Repository repo = repoManager.openRepository(projectKey);

    final PatchSet.Id patchSetId = change.currentPatchSetId();
    try {
      final RevWalk revWalk = new RevWalk(repo);
      final String currPatchsetRev =
          db.patchSets().get(patchSetId).getRevision().get();
      final RevCommit currPatchsetRevCommit =
          revWalk.parseCommit(ObjectId.fromString((currPatchsetRev)));
      if (currPatchsetRevCommit.getParentCount() > 1) {
        throw new InvalidChangeOperationException(
            "Merge commit cannot be moved");
      }
      final ObjectId refId = repo.resolve(dest);
      // Check ref exists in project repo
      if (refId == null) {
        throw new NoSuchRefException(dest);
      }
      RevCommit refCommit = revWalk.parseCommit(refId);
      if (revWalk.isMergedInto(currPatchsetRevCommit, refCommit)) {
        throw new InvalidChangeOperationException(
            "Current patchset revision is reachable from tip of " + dest);
      }
    } finally {
      repo.close();
    }

    Change.Key changeKey = change.getKey();
    final Branch.NameKey destKey = new Branch.NameKey(projectKey, dest);
    List<Change> destChanges =
        asChanges(queryProvider.get().byBranchKey(destKey, changeKey));
    if (!destChanges.isEmpty()) {
      for (Change destChange : destChanges) {
        if (destChange.getId().get() == changeId.get()) {
          // Destination is same as change's current dest
          // Don't error out to allow repeated invocations
          return null;
        }
      }
      throw new InvalidChangeOperationException("Dest "
          + destKey.getShortName()
          + " has a different change with same change key " + changeKey);
    }

    Branch.NameKey changePrevDest = change.getDest();
    final Change updatedChange =
        db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus() == Change.Status.NEW
                && change.currentPatchSetId().equals(patchSetId)) {
              change.setDest(destKey);
              ChangeUtil.updated(change);
              return change;
            } else {
              return null;
            }
          }
        });

    if (updatedChange == null) {
      throw new InvalidChangeOperationException("Change " + patchSetId
          + " is not open");
    }

    final StringBuilder msgBuf =
        new StringBuilder("Patch set " + patchSetId.get()
            + ": Change destination moved from "
            + changePrevDest.getShortName() + " to " + destKey.getShortName());
    if (changeComment != null && changeComment.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(changeComment);
    }

    ReviewInput review = new ReviewInput();
    review.message = Strings.emptyToNull(changeComment);
    gApi.get().changes()
        .id(changeId.get())
        .revision(db.patchSets().get(patchSetId).getRevision().get())
        .review(review);

    return null;
  }
}

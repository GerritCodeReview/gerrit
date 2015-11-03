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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;

@Singleton
public class Move implements RestModifyView<ChangeResource, MoveInput> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeIndexer indexer;
  private final GitRepositoryManager repoManager;
  private final Provider<GerritApi> gApi;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  Move(Provider<ReviewDb> dbProvider, ChangeJson.Factory json,
      ChangeIndexer indexer, GitRepositoryManager repoManager,
      Provider<GerritApi> gApi, Provider<InternalChangeQuery> queryProvider) {
    this.dbProvider = dbProvider;
    this.json = json;
    this.indexer = indexer;
    this.repoManager = repoManager;
    this.gApi = gApi;
    this.queryProvider = queryProvider;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, MoveInput input)
      throws AuthException, RestApiException, ResourceConflictException,
      OrmException, IOException {
    ReviewDb db = dbProvider.get();
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canMoveTo(RefNames.fullName(input.destination), db)) {
      throw new AuthException("Move not permitted");
    } else if (change.getStatus() != Status.NEW
        && change.getStatus() != Status.DRAFT) {
      throw new ResourceConflictException("Change is " + status(change));
    }

    Project.NameKey projectKey = change.getProject();
    final PatchSet.Id patchSetId = change.currentPatchSetId();
    try (Repository repo = repoManager.openRepository(projectKey);
        RevWalk revWalk = new RevWalk(repo)) {
      String currPatchsetRev =
          db.patchSets().get(patchSetId).getRevision().get();
      RevCommit currPatchsetRevCommit =
          revWalk.parseCommit(ObjectId.fromString((currPatchsetRev)));
      if (currPatchsetRevCommit.getParentCount() > 1) {
        throw new ResourceConflictException("Merge commit cannot be moved");
      }

      ObjectId refId = repo.resolve(input.destination);
      // Check if destnation ref exists in project repo
      if (refId == null) {
        throw new ResourceConflictException(
            "Destination " + input.destination + " not found in the project");
      }
      RevCommit refCommit = revWalk.parseCommit(refId);
      if (revWalk.isMergedInto(currPatchsetRevCommit, refCommit)) {
        throw new ResourceConflictException(
            "Current patchset revision is reachable from tip of "
                + input.destination);
      }
    }

    Change.Key changeKey = change.getKey();
    final Branch.NameKey destKey =
        new Branch.NameKey(projectKey, input.destination);
    List<Change> destChanges =
        asChanges(queryProvider.get().byBranchKey(destKey, changeKey));
    if (!destChanges.isEmpty()) {
      for (Change destChange : destChanges) {
        if (destChange.getId().get() == change.getId().get()) {
          // Destination is same as change's current dest
          // Don't error out to allow repeated invocations
          return null;
        }
      }
      throw new ResourceConflictException(
          "Destination " + destKey.getShortName()
              + " has a different change with same change key " + changeKey);
    }

    Branch.NameKey changePrevDest = change.getDest();

    db.changes().beginTransaction(change.getId());
    try {
      change =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.currentPatchSetId().equals(patchSetId)) {
                change.setDest(destKey);
                ChangeUtil.updated(change);
                return change;
              }
              return null;
            }
          });
      if (change == null) {
        throw new ResourceConflictException("Patch set is not current");
      }
      db.commit();
    } finally {
      db.rollback();
    }

    indexer.index(db, change);

    StringBuilder msgBuf = new StringBuilder();
    msgBuf.append("Patch set ");
    msgBuf.append(patchSetId.get());
    msgBuf.append(": Change destination moved from ");
    msgBuf.append(changePrevDest.getShortName());
    msgBuf.append(" to ");
    msgBuf.append(destKey.getShortName());
    if (!Strings.isNullOrEmpty(input.message)) {
      msgBuf.append("\n\n");
      msgBuf.append(input.message);
    }

    ReviewInput review = new ReviewInput();
    review.message = Strings.emptyToNull(msgBuf.toString());
    gApi.get().changes().id(change.getId().get())
        .revision(db.patchSets().get(patchSetId).getRevision().get())
        .review(review);

    return json.create(ChangeJson.NO_OPTIONS).format(change);
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}

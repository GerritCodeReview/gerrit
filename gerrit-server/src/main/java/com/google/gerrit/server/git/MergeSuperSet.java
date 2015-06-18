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

package com.google.gerrit.server.git;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This calculates the minimal superset of changes required to be merged.
 * <p>
 * This includes all parents between a change and the tip of its target
 * branch for the merging/rebasing submit strategies. For the cherry-pick
 * strategy no additional changes are included.
 */
public class MergeSuperSet {
  public interface Factory {
    MergeSuperSet create(ChangeSet changes, boolean withSpread);
  }

  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  private final ChangeData.Factory changeDataFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;

  private ChangeSet changes;
  private boolean withSpread;

  @Inject
  MergeSuperSet(ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager,
      SchemaFactory<ReviewDb> schemaFactory,
      @Assisted ChangeSet changes,
      @Assisted boolean withSpread) {
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.schemaFactory = schemaFactory;

    this.changes = changes;
    this.withSpread = withSpread;
  }

  private ChangeSet calculateCompleteChangeSet(ReviewDb db) throws
      MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {

    List<Change> ret = new ArrayList<>();

    for (Project.NameKey project : changes.projects()) {
      try (Repository repo = repoManager.openRepository( project);
           RevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
        rw.sort(RevSort.TOPO);
        rw.sort(RevSort.COMMIT_TIME_DESC, true);

        for (Change.Id cId : changes.changesByProject().get(project)) {
          ChangeData cd = changeDataFactory.create(db, cId);

          SubmitTypeRecord r = new SubmitRuleEvaluator(cd)
              .setPatchSet(cd.currentPatchSet()).getSubmitType();
          if (r.status != SubmitTypeRecord.Status.OK) {
            logErrorAndThrow("Failed to get submit type for " + cd.getId());
          }
          if (r.type == SubmitType.CHERRY_PICK) {
            ret.add(cd.change());
            continue;
          }

          // Get the underlying git commit object
          PatchSet ps = cd.currentPatchSet();
          String objIdStr = ps.getRevision().get();
          RevCommit commit = rw.parseCommit(ObjectId.fromString(objIdStr));

          // Get the common ancestor with target branch
          Branch.NameKey destBranch = cd.change().getDest();
          repo.getRefDatabase().refresh();
          Ref ref = repo.getRefDatabase().getRef(destBranch.get());
          if (ref == null) {
            ret.add(cd.change());
            // A new empty branch doesn't have additional changes
            continue;
          }

          rw.markStart(commit);
          RevCommit head = rw.parseCommit(ref.getObjectId());
          rw.markUninteresting(head);

          List<String> hashes = new ArrayList<>();
          for (RevCommit c : rw) {
            hashes.add(c.name());
          }

          if (!hashes.isEmpty()) {
            List<ChangeData> destChanges = queryProvider.get()
                .byCommitsOnBranch(cd.change().getDest(), hashes);

            for (ChangeData chd : destChanges) {
              Change chg = chd.change();
              //TODO: bail if not new or submitted
              if (chg.getStatus() != Change.Status.NEW
                  && chg.getStatus() != Change.Status.SUBMITTED) {
                throw new OrmException("Change " + chg.getChangeId()
                    + "is in state " + chg.getStatus());
              }
              ret.add(chg);
            }
          }
        }
      }
    }

    return ChangeSet.create(ret);
  }

  private ChangeSet calculateCompleteChangeSetWithTopicSpread(ReviewDb db)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {
    Set<String> topicsTraversed = new HashSet<>();
    boolean done = false;
    ChangeSet newCs = calculateCompleteChangeSet(db);
    while (!done) {
      List<Change> cds = new ArrayList<>();
      done = true;
      for (Change.Id cId : newCs.ids()) {
        ChangeData cd = changeDataFactory.create(db, cId);
        cds.add(cd.change());

        String topic = cd.change().getTopic();
        if (!Strings.isNullOrEmpty(topic) && !topicsTraversed.contains(topic)) {
          for (ChangeData addCd : queryProvider.get().byTopicOpen(topic)) {
            cds.add(addCd.change());
          }
          done = false;
          topicsTraversed.add(topic);
        }
      }
      changes = ChangeSet.create(cds);
      newCs = calculateCompleteChangeSet(db);
    }
    return newCs;
  }

  public ChangeSet completeChangeSet()
      throws MissingObjectException, IncorrectObjectTypeException,
      IOException, OrmException {
    try (ReviewDb db = schemaFactory.open()) {
      if (withSpread) {
        return calculateCompleteChangeSetWithTopicSpread(db);
      } else {
        return calculateCompleteChangeSet(db);
      }
    }
  }

  private void logErrorAndThrow(String msg) throws OrmException {
    if (log.isErrorEnabled()) {
      log.error(msg);
    }
    throw new OrmException(msg);
  }
}
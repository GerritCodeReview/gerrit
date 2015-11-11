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
import com.google.common.collect.Multimap;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
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
 * Calculates the minimal superset of changes required to be merged.
 * <p>
 * This includes all parents between a change and the tip of its target
 * branch for the merging/rebasing submit strategies. For the cherry-pick
 * strategy no additional changes are included.
 * <p>
 * If change.submitWholeTopic is enabled, also all changes of the topic
 * and their parents are included.
 */
@Singleton
public class MergeSuperSet {
  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);

  private final ChangeData.Factory changeDataFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final boolean submitWholeTopicCfg;

  @Inject
  MergeSuperSet(@GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager) {
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
    this.submitWholeTopicCfg = Submit.wholeTopicEnabled(cfg);
  }

  public ChangeSet completeChangeSet(ReviewDb db, Change change)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {
    return completeChangeSet(db, change, submitWholeTopicCfg);
  }

  public ChangeSet completeChangeSet(ReviewDb db, Change change,
      boolean submitWholeTopic) throws MissingObjectException,
          IncorrectObjectTypeException, IOException, OrmException {
    ChangeData cd = changeDataFactory.create(db, change.getId());
    if (submitWholeTopic) {
      return completeChangeSetIncludingTopics(db, new ChangeSet(cd));
    } else {
      return completeChangeSetWithoutTopic(db, new ChangeSet(cd));
    }
  }

  private ChangeSet completeChangeSetWithoutTopic(ReviewDb db, ChangeSet changes)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {
    List<ChangeData> ret = new ArrayList<>();

    Multimap<Project.NameKey, Change.Id> pc = changes.changesByProject();
    for (Project.NameKey project : pc.keySet()) {
      try (Repository repo = repoManager.openRepository(project);
           RevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
        for (Change.Id cId : pc.get(project)) {
          ChangeData cd = changeDataFactory.create(db, cId);

          SubmitTypeRecord r = new SubmitRuleEvaluator(cd).getSubmitType();
          if (r.status != SubmitTypeRecord.Status.OK) {
            logErrorAndThrow("Failed to get submit type for " + cd.getId());
          }
          if (r.type == SubmitType.CHERRY_PICK) {
            ret.add(cd);
            continue;
          }

          // Get the underlying git commit object
          PatchSet ps = cd.currentPatchSet();
          String objIdStr = ps.getRevision().get();
          RevCommit commit = rw.parseCommit(ObjectId.fromString(objIdStr));

          // Collect unmerged ancestors
          Branch.NameKey destBranch = cd.change().getDest();
          repo.getRefDatabase().refresh();
          Ref ref = repo.getRefDatabase().getRef(destBranch.get());

          rw.reset();
          rw.sort(RevSort.TOPO);
          rw.markStart(commit);
          if (ref != null) {
            RevCommit head = rw.parseCommit(ref.getObjectId());
            rw.markUninteresting(head);
          }

          List<String> hashes = new ArrayList<>();
          for (RevCommit c : rw) {
            hashes.add(c.name());
          }

          if (!hashes.isEmpty()) {
            // Merged changes are ok to exclude
            Iterable<ChangeData> destChanges = queryProvider.get()
                .byCommitsOnBranchNotMerged(
                  repo, db, cd.change().getDest(), hashes);
            for (ChangeData chd : destChanges) {
              ret.add(chd);
            }
          }
        }
      }
    }

    return new ChangeSet(ret);
  }

  private ChangeSet completeChangeSetIncludingTopics(
      ReviewDb db, ChangeSet changes) throws MissingObjectException,
      IncorrectObjectTypeException, IOException, OrmException {
    Set<String> topicsTraversed = new HashSet<>();
    boolean done = false;
    ChangeSet newCs = completeChangeSetWithoutTopic(db, changes);
    while (!done) {
      List<ChangeData> chgs = new ArrayList<>();
      done = true;
      for (ChangeData cd : newCs.changes()) {
        chgs.add(cd);
        String topic = cd.change().getTopic();
        if (!Strings.isNullOrEmpty(topic) && !topicsTraversed.contains(topic)) {
          chgs.addAll(queryProvider.get().byTopicOpen(topic));
          done = false;
          topicsTraversed.add(topic);
        }
      }
      changes = new ChangeSet(chgs);
      newCs = completeChangeSetWithoutTopic(db, changes);
    }
    return newCs;
  }

  private void logError(String msg) {
    if (log.isErrorEnabled()) {
      log.error(msg);
    }
  }

  private void logErrorAndThrow(String msg) throws OrmException {
    logError(msg);
    throw new OrmException(msg);
  }
}

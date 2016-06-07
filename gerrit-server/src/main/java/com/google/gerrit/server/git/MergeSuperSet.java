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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

  public static void reloadChanges(ChangeSet cs) throws OrmException {
    // Clear exactly the fields requested by query() below.
    for (ChangeData cd : cs.changes()) {
      cd.reloadChange();
      cd.setPatchSets(null);
    }
  }

  private final ChangeData.Factory changeDataFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager repoManager;
  private final Config cfg;

  @Inject
  MergeSuperSet(@GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      GitRepositoryManager repoManager) {
    this.cfg = cfg;
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.repoManager = repoManager;
  }

  public ChangeSet completeChangeSet(ReviewDb db, Change change, CurrentUser user)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {
    ChangeData cd =
        changeDataFactory.create(db, change.getProject(), change.getId());
    cd.changeControl(user);
    if (Submit.wholeTopicEnabled(cfg)) {
      return completeChangeSetIncludingTopics(db, new ArrayList<>(Arrays.asList(cd)), user);
    }
    return completeChangeSetWithoutTopic(db, new ArrayList<>(Arrays.asList(cd)), user);
  }

  private ChangeSet completeChangeSetWithoutTopic(ReviewDb db,
      List<ChangeData> chgs, CurrentUser user) throws MissingObjectException,
      IncorrectObjectTypeException, IOException, OrmException {
    List<ChangeData> ret = new ArrayList<>();
    ChangeSet changes = new ChangeSet(chgs, null, null);
    Multimap<Project.NameKey, Change.Id> pc = changes.changesByProject();
    for (Project.NameKey project : pc.keySet()) {
      try (Repository repo = repoManager.openRepository(project);
           RevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
        for (Change.Id cId : pc.get(project)) {
          ChangeData cd = changeDataFactory.create(db, project, cId);
          SubmitTypeRecord str = cd.submitTypeRecord();

          if (str.type == SubmitType.CHERRY_PICK) {
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
          // Always include the input, even if merged. This allows
          // SubmitStrategyOp to correct the situation later, assuming it gets
          // returned by byCommitsOnBranchNotMerged below.
          hashes.add(objIdStr);
          for (RevCommit c : rw) {
            if (!c.equals(commit)) {
              hashes.add(c.name());
            }
          }

          if (!hashes.isEmpty()) {
            Iterable<ChangeData> destChanges = query()
                .byCommitsOnBranchNotMerged(
                  repo, db, cd.change().getDest(), hashes);
            for (ChangeData chd : destChanges) {
              ret.add(chd);
            }
          }
        }
      }
    }

    return new ChangeSet(ret, db, user);
  }

  private ChangeSet completeChangeSetIncludingTopics(
      ReviewDb db, List<ChangeData> chgs, CurrentUser user)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      OrmException {
    Set<String> topicsTraversed = new HashSet<>();
    boolean done = false;
    while (!done) {
      done = true;
      List<ChangeData> newChgs = new ArrayList<>();
      for (ChangeData cd : chgs) {
        newChgs.add(cd);
        String topic = cd.change().getTopic();
        if (!Strings.isNullOrEmpty(topic) && !topicsTraversed.contains(topic)) {
          newChgs.addAll(query().byTopicOpen(topic));
          done = false;
          topicsTraversed.add(topic);
        }
      }
      chgs = new ArrayList<>(completeChangeSetWithoutTopic(db, newChgs, null).changes());
    }
    return completeChangeSetWithoutTopic(db, chgs, user);
  }

  private InternalChangeQuery query() {
    // Request fields required for completing the ChangeSet without having to
    // touch the database. This provides reasonable performance when loading the
    // change screen; callers that care about reading the latest value of these
    // fields should clear them explicitly using reloadChanges().
    Set<String> fields = ImmutableSet.of(
        ChangeField.CHANGE.getName(),
        ChangeField.PATCH_SET.getName());
    return queryProvider.get().setRequestedFields(fields);
  }
}

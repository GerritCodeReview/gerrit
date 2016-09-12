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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
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

  public static void reloadChanges(ChangeSet cs) throws OrmException {
    // Clear exactly the fields requested by query() below.
    for (ChangeData cd : cs.changes()) {
      cd.reloadChange();
      cd.setPatchSets(null);
    }
  }

  private final ChangeData.Factory changeDataFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<MergeOpRepoManager> repoManagerProvider;
  private final Config cfg;

  private MergeOpRepoManager orm;
  private boolean closeOrm;

  @Inject
  MergeSuperSet(@GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeOpRepoManager> repoManagerProvider) {
    this.cfg = cfg;
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.repoManagerProvider = repoManagerProvider;
  }

  public MergeSuperSet setMergeOpRepoManager(MergeOpRepoManager orm) {
    checkState(this.orm == null);
    this.orm = checkNotNull(orm);
    closeOrm = false;
    return this;
  }

  public ChangeSet completeChangeSet(ReviewDb db, Change change,
      CurrentUser user) throws IOException, OrmException {
    try {
      ChangeData cd =
          changeDataFactory.create(db, change.getProject(), change.getId());
      cd.changeControl(user);
      ChangeSet cs = new ChangeSet(cd, cd.changeControl().isVisible(db, cd));
      if (Submit.wholeTopicEnabled(cfg)) {
        return completeChangeSetIncludingTopics(db, cs, user);
      }
      return completeChangeSetWithoutTopic(db, cs, user);
    } finally {
      if (closeOrm && orm != null) {
        orm.close();
        orm = null;
      }
    }
  }

  private SubmitType submitType(ChangeData cd, PatchSet ps, boolean visible)
      throws OrmException {
    // Submit type prolog rules mean that the submit type can depend on the
    // submitting user and the content of the change.
    //
    // If the current user can see the change, run that evaluation to get a
    // preview of what would happen on submit.  If the current user can't see
    // the change, instead of guessing who would do the submitting, rely on the
    // project configuration and ignore the prolog rule.  If the prolog rule
    // doesn't match that, we may pick the wrong submit type and produce a
    // misleading (but still nonzero) count of the non visible changes that
    // would be submitted together with the visible ones.
    if (!visible) {
      return cd.changeControl().getProject().getSubmitType();
    }

    SubmitTypeRecord str =
        ps == cd.currentPatchSet()
            ? cd.submitTypeRecord()
            : new SubmitRuleEvaluator(cd).setPatchSet(ps).getSubmitType();
    if (!str.isOk()) {
      logErrorAndThrow("Failed to get submit type for " + cd.getId()
          + ": " + str.errorMessage);
    }
    return str.type;
  }

  private ChangeSet completeChangeSetWithoutTopic(ReviewDb db,
      ChangeSet changes, CurrentUser user) throws IOException, OrmException {
    List<ChangeData> visibleChanges = new ArrayList<>();
    List<ChangeData> nonVisibleChanges = new ArrayList<>();

    for (ChangeData cd :
        Iterables.concat(changes.changes(), changes.nonVisibleChanges())) {
      checkState(cd.hasChangeControl(),
          "completeChangeSet forgot to set changeControl for current user"
          + " at ChangeData creation time");
      OpenRepo or = getRepo(cd.change().getProject());
      boolean visible = changes.ids().contains(cd.getId());
      if (visible && !cd.changeControl().isVisible(db, cd)) {
        // We thought the change was visible, but it isn't.
        // This can happen if the ACL changes during the
        // completeChangeSet computation, for example.
        visible = false;
      }
      List<ChangeData> dest = visible ? visibleChanges : nonVisibleChanges;

      // Pick a revision to use for traversal.  If any of the patch sets
      // is visible, we use the most recent one.  Otherwise, use the current
      // patch set.
      PatchSet ps = cd.currentPatchSet();
      boolean visiblePatchSet = visible;
      if (!cd.changeControl().isPatchVisible(ps, cd)) {
        Iterable<PatchSet> visiblePatchSets = cd.visiblePatchSets();
        if (Iterables.isEmpty(visiblePatchSets)) {
          visiblePatchSet = false;
        } else {
          ps = Iterables.getLast(visiblePatchSets);
        }
      }

      if (submitType(cd, ps, visiblePatchSet) == SubmitType.CHERRY_PICK) {
        dest.add(cd);
        continue;
      }

      // Get the underlying git commit object
      String objIdStr = ps.getRevision().get();
      RevCommit commit = or.rw.parseCommit(ObjectId.fromString(objIdStr));

      // Collect unmerged ancestors
      Branch.NameKey destBranch = cd.change().getDest();
      Ref ref = or.repo.getRefDatabase().getRef(destBranch.get());

      or.rw.reset();
      or.rw.markStart(commit);
      if (ref != null) {
        RevCommit head = or.rw.parseCommit(ref.getObjectId());
        or.rw.markUninteresting(head);
      }

      List<String> hashes = new ArrayList<>();
      // Always include the input, even if merged. This allows
      // SubmitStrategyOp to correct the situation later, assuming it gets
      // returned by byCommitsOnBranchNotMerged below.
      hashes.add(objIdStr);
      for (RevCommit c : or.rw) {
        if (!c.equals(commit)) {
          hashes.add(c.name());
        }
      }

      if (!hashes.isEmpty()) {
        Iterable<ChangeData> destChanges = query()
            .byCommitsOnBranchNotMerged(
              or.repo, db, cd.change().getDest(), hashes);
        for (ChangeData chd : destChanges) {
          chd.changeControl(user);
          dest.add(chd);
        }
      }
    }

    return new ChangeSet(visibleChanges, nonVisibleChanges);
  }

  private OpenRepo getRepo(Project.NameKey project) throws IOException {
    if (orm == null) {
      orm = repoManagerProvider.get();
      closeOrm = true;
    }
    try {
      OpenRepo or = orm.openRepo(project);
      checkState(or.rw.hasRevSort(RevSort.TOPO));
      return or;
    } catch (NoSuchProjectException e) {
      throw new IOException(e);
    }
  }

  /**
   * Completes {@code cs} with any additional changes from its topics
   * <p>
   * {@link #completeChangeSetIncludingTopics} calls this repeatedly,
   * alternating with {@link #completeChangeSetWithoutTopic}, to discover
   * what additional changes should be submitted with a change until the
   * set stops growing.
   * <p>
   * {@code topicsSeen} and {@code visibleTopicsSeen} keep track of topics
   * already explored to avoid wasted work.
   *
   * @return the resulting larger {@link ChangeSet}
   */
  private ChangeSet topicClosure(
      ReviewDb db, ChangeSet cs, CurrentUser user,
      Set<String> topicsSeen, Set<String> visibleTopicsSeen)
      throws OrmException {
    List<ChangeData> visibleChanges = new ArrayList<>();
    List<ChangeData> nonVisibleChanges = new ArrayList<>();

    for (ChangeData cd : cs.changes()) {
      visibleChanges.add(cd);
      String topic = cd.change().getTopic();
      if (Strings.isNullOrEmpty(topic) || visibleTopicsSeen.contains(topic)) {
        continue;
      }
      for (ChangeData topicCd : query().byTopicOpen(topic)) {
        topicCd.changeControl(user);
        if (topicCd.changeControl().isVisible(db, topicCd)) {
          visibleChanges.add(topicCd);
        } else {
          nonVisibleChanges.add(topicCd);
        }
      }
      topicsSeen.add(topic);
      visibleTopicsSeen.add(topic);
    }
    for (ChangeData cd : cs.nonVisibleChanges()) {
      nonVisibleChanges.add(cd);
      String topic = cd.change().getTopic();
      if (Strings.isNullOrEmpty(topic) || topicsSeen.contains(topic)) {
        continue;
      }
      for (ChangeData topicCd : query().byTopicOpen(topic)) {
        topicCd.changeControl(user);
        nonVisibleChanges.add(topicCd);
      }
      topicsSeen.add(topic);
    }
    return new ChangeSet(visibleChanges, nonVisibleChanges);
  }

  private ChangeSet completeChangeSetIncludingTopics(
      ReviewDb db, ChangeSet changes, CurrentUser user)
      throws IOException, OrmException {
    Set<String> topicsSeen = new HashSet<>();
    Set<String> visibleTopicsSeen = new HashSet<>();
    int oldSeen;
    int seen = 0;

    do {
      oldSeen = seen;

      changes = completeChangeSetWithoutTopic(db, changes, user);
      changes = topicClosure(db, changes, user, topicsSeen, visibleTopicsSeen);

      seen = topicsSeen.size() + visibleTopicsSeen.size();
    } while (seen != oldSeen);
    return changes;
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

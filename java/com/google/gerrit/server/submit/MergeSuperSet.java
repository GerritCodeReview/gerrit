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

package com.google.gerrit.server.submit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Calculates the minimal superset of changes required to be merged.
 *
 * <p>This includes all parents between a change and the tip of its target branch for the
 * merging/rebasing submit strategies. For the cherry-pick strategy no additional changes are
 * included.
 *
 * <p>If change.submitWholeTopic is enabled, also all changes of the topic and their parents are
 * included.
 */
public class MergeSuperSet {

  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<MergeOpRepoManager> repoManagerProvider;
  private final DynamicItem<MergeSuperSetComputation> mergeSuperSetComputation;
  private final PermissionBackend permissionBackend;
  private final Config cfg;
  private final ProjectCache projectCache;

  private MergeOpRepoManager orm;
  private boolean closeOrm;

  @Inject
  MergeSuperSet(
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeOpRepoManager> repoManagerProvider,
      DynamicItem<MergeSuperSetComputation> mergeSuperSetComputation,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.cfg = cfg;
    this.queryProvider = queryProvider;
    this.repoManagerProvider = repoManagerProvider;
    this.mergeSuperSetComputation = mergeSuperSetComputation;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  public static boolean wholeTopicEnabled(Config config) {
    return config.getBoolean("change", null, "submitWholeTopic", false);
  }

  public MergeSuperSet setMergeOpRepoManager(MergeOpRepoManager orm) {
    checkState(this.orm == null);
    this.orm = checkNotNull(orm);
    closeOrm = false;
    return this;
  }

  public ChangeSet completeChangeSet(ReviewDb db, Change change, CurrentUser user)
      throws IOException, OrmException, PermissionBackendException {
    try {
      if (orm == null) {
        orm = repoManagerProvider.get();
        closeOrm = true;
      }
      List<ChangeData> cds = queryProvider.get().byLegacyChangeId(change.getId());
      checkState(cds.size() == 1, "Expected exactly one ChangeData, got " + cds.size());
      ChangeData cd = Iterables.getFirst(cds, null);
      ProjectState projectState = projectCache.checkedGet(cd.project());
      ChangeSet changeSet =
          new ChangeSet(
              cd,
              projectState != null
                  && projectState.statePermitsRead()
                  && permissionBackend
                      .user(user)
                      .change(cd)
                      .database(db)
                      .test(ChangePermission.READ));
      if (wholeTopicEnabled(cfg)) {
        return completeChangeSetIncludingTopics(db, changeSet, user);
      }
      return mergeSuperSetComputation.get().completeWithoutTopic(db, orm, changeSet, user);
    } finally {
      if (closeOrm && orm != null) {
        orm.close();
        orm = null;
      }
    }
  }

  /**
   * Completes {@code changeSet} with any additional changes from its topics
   *
   * <p>{@link #completeChangeSetIncludingTopics} calls this repeatedly, alternating with {@link
   * MergeSuperSetComputation#completeWithoutTopic(ReviewDb, MergeOpRepoManager, ChangeSet,
   * CurrentUser)}, to discover what additional changes should be submitted with a change until the
   * set stops growing.
   *
   * <p>{@code topicsSeen} and {@code visibleTopicsSeen} keep track of topics already explored to
   * avoid wasted work.
   *
   * @return the resulting larger {@link ChangeSet}
   */
  private ChangeSet topicClosure(
      ReviewDb db,
      ChangeSet changeSet,
      CurrentUser user,
      Set<String> topicsSeen,
      Set<String> visibleTopicsSeen)
      throws OrmException, PermissionBackendException, IOException {
    List<ChangeData> visibleChanges = new ArrayList<>();
    List<ChangeData> nonVisibleChanges = new ArrayList<>();

    for (ChangeData cd : changeSet.changes()) {
      visibleChanges.add(cd);
      String topic = cd.change().getTopic();
      if (Strings.isNullOrEmpty(topic) || visibleTopicsSeen.contains(topic)) {
        continue;
      }
      for (ChangeData topicCd : byTopicOpen(topic)) {
        if (canRead(db, user, topicCd)) {
          visibleChanges.add(topicCd);
        } else {
          nonVisibleChanges.add(topicCd);
        }
      }
      topicsSeen.add(topic);
      visibleTopicsSeen.add(topic);
    }
    for (ChangeData cd : changeSet.nonVisibleChanges()) {
      nonVisibleChanges.add(cd);
      String topic = cd.change().getTopic();
      if (Strings.isNullOrEmpty(topic) || topicsSeen.contains(topic)) {
        continue;
      }
      for (ChangeData topicCd : byTopicOpen(topic)) {
        nonVisibleChanges.add(topicCd);
      }
      topicsSeen.add(topic);
    }
    return new ChangeSet(visibleChanges, nonVisibleChanges);
  }

  private ChangeSet completeChangeSetIncludingTopics(
      ReviewDb db, ChangeSet changeSet, CurrentUser user)
      throws IOException, OrmException, PermissionBackendException {
    Set<String> topicsSeen = new HashSet<>();
    Set<String> visibleTopicsSeen = new HashSet<>();
    int oldSeen;
    int seen = 0;

    changeSet = topicClosure(db, changeSet, user, topicsSeen, visibleTopicsSeen);
    seen = topicsSeen.size() + visibleTopicsSeen.size();

    do {
      oldSeen = seen;
      changeSet = mergeSuperSetComputation.get().completeWithoutTopic(db, orm, changeSet, user);
      changeSet = topicClosure(db, changeSet, user, topicsSeen, visibleTopicsSeen);
      seen = topicsSeen.size() + visibleTopicsSeen.size();
    } while (seen != oldSeen);
    return changeSet;
  }

  private List<ChangeData> byTopicOpen(String topic) throws OrmException {
    return queryProvider.get().byTopicOpen(topic);
  }

  private boolean canRead(ReviewDb db, CurrentUser user, ChangeData cd)
      throws PermissionBackendException, IOException {
    ProjectState projectState = projectCache.checkedGet(cd.project());
    return projectState != null
        && projectState.statePermitsRead()
        && permissionBackend.user(user).change(cd).database(db).test(ChangePermission.READ);
  }
}

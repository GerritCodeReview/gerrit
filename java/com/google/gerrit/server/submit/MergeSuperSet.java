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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
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
  private final ChangeData.Factory changeDataFactory;
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
      ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeOpRepoManager> repoManagerProvider,
      DynamicItem<MergeSuperSetComputation> mergeSuperSetComputation,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.cfg = cfg;
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.repoManagerProvider = repoManagerProvider;
    this.mergeSuperSetComputation = mergeSuperSetComputation;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  public static boolean wholeTopicEnabled(Config config) {
    return config.getBoolean("change", null, "submitWholeTopic", false);
  }

  @CanIgnoreReturnValue
  public MergeSuperSet setMergeOpRepoManager(MergeOpRepoManager orm) {
    checkState(this.orm == null);
    this.orm = requireNonNull(orm);
    closeOrm = false;
    return this;
  }

  /**
   * Gets the ChangeSet of this {@code change} based on visiblity of the {@code user}. if
   * change.submitWholeTopic is true, we return the topic closure as well as the dependent changes
   * of the topic closure. Otherwise, we return just the dependent changes.
   *
   * @param change the change for which we get the dependent changes / topic closure.
   * @param user the current user for visibility purposes.
   * @param includingTopicClosure when true, return as if change.submitWholeTopic = true, so we
   *     return the topic closure.
   * @return {@link ChangeSet} object that represents the dependent changes and/or topic closure of
   *     the requested change.
   */
  public ChangeSet completeChangeSet(Change change, CurrentUser user, boolean includingTopicClosure)
      throws IOException, PermissionBackendException {
    try {
      if (orm == null) {
        orm = repoManagerProvider.get();
        closeOrm = true;
      }
      ChangeData cd = changeDataFactory.create(change.getProject(), change.getId());
      boolean visible =
          cd != null
              && cd.projectStatePermitsRead()
              && permissionBackend.user(user).change(cd).test(ChangePermission.READ);

      ChangeSet changeSet = new ChangeSet(cd, visible);
      if (wholeTopicEnabled(cfg) || includingTopicClosure) {
        return completeChangeSetIncludingTopics(changeSet, user);
      }
      try (TraceContext traceContext = PluginContext.newTrace(mergeSuperSetComputation)) {
        return mergeSuperSetComputation.get().completeWithoutTopic(orm, changeSet, user);
      }
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
   * MergeSuperSetComputation#completeWithoutTopic(MergeOpRepoManager, ChangeSet, CurrentUser)}, to
   * discover what additional changes should be submitted with a change until the set stops growing.
   *
   * <p>{@code topicsSeen} and {@code visibleTopicsSeen} keep track of topics already explored to
   * avoid wasted work.
   *
   * @return the resulting larger {@link ChangeSet}
   */
  private ChangeSet topicClosure(
      ChangeSet changeSet, CurrentUser user, Set<String> topicsSeen, Set<String> visibleTopicsSeen)
      throws PermissionBackendException {
    List<ChangeData> visibleChanges = new ArrayList<>();
    List<ChangeData> nonVisibleChanges = new ArrayList<>();

    for (ChangeData cd : changeSet.changes()) {
      visibleChanges.add(cd);
      String topic = cd.change().getTopic();
      if (Strings.isNullOrEmpty(topic) || visibleTopicsSeen.contains(topic)) {
        continue;
      }
      for (ChangeData topicCd : byTopicOpen(topic)) {
        if (canRead(user, topicCd)) {
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

  private ChangeSet completeChangeSetIncludingTopics(ChangeSet changeSet, CurrentUser user)
      throws IOException, PermissionBackendException {
    Set<String> topicsSeen = new HashSet<>();
    Set<String> visibleTopicsSeen = new HashSet<>();
    int oldSeen;
    int seen;

    changeSet = topicClosure(changeSet, user, topicsSeen, visibleTopicsSeen);
    seen = topicsSeen.size() + visibleTopicsSeen.size();

    do {
      oldSeen = seen;
      try (TraceContext traceContext = PluginContext.newTrace(mergeSuperSetComputation)) {
        changeSet = mergeSuperSetComputation.get().completeWithoutTopic(orm, changeSet, user);
      }
      changeSet = topicClosure(changeSet, user, topicsSeen, visibleTopicsSeen);
      seen = topicsSeen.size() + visibleTopicsSeen.size();
    } while (seen != oldSeen);
    return changeSet;
  }

  private List<ChangeData> byTopicOpen(String topic) {
    return queryProvider.get().byTopicOpen(topic);
  }

  private boolean canRead(CurrentUser user, ChangeData cd) throws PermissionBackendException {
    if (!projectCache.get(cd.project()).map(ProjectState::statePermitsRead).orElse(false)) {
      return false;
    }
    return permissionBackend.user(user).change(cd).test(ChangePermission.READ);
  }
}

// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ‘watch.config’ file in the user branch in the All-Users repository that
 * contains the watch configuration of the user.
 * <p>
 * The 'watch.config' file is a git config file that has one 'project' section
 * for all project watches of a project.
 * <p>
 * The project name is used as subsection name and the filters with the notify
 * types that decide for which events email notifications should be sent are
 * represented as 'notify' values in the subsection. A 'notify' value is
 * formatted as '<filter> [<comma-separated-list-of-notify-types>]':
 *
 * <pre>
 *   [project "foo"]
 *     notify = * [ALL_COMMENTS]
 *     notify = branch:master [ALL_COMMENTS, NEW_PATCHSETS]
 *     notify = branch:master owner:self [SUBMITTED_CHANGES]
 * </pre>
 * <p>
 * If two notify values in the same subsection have the same filter they are
 * merged on the next save, taking the union of the notify types.
 * <p>
 * For watch configurations that notify on no event the list of notify types is
 * empty:
 *
 * <pre>
 *   [project "foo"]
 *     notify = branch:master []
 * </pre>
 * <p>
 * Unknown notify types are ignored and removed on save.
 */
public class WatchConfig extends VersionedMetaData implements AutoCloseable {
  @Singleton
  public static class Accessor {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Accessor(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        Provider<MetaDataUpdate.User> metaDataUpdateFactory,
        IdentifiedUser.GenericFactory userFactory) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.metaDataUpdateFactory = metaDataUpdateFactory;
      this.userFactory = userFactory;
    }

    public Map<ProjectWatchKey, Set<NotifyType>> getProjectWatches(
        Account.Id accountId) throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName);
          WatchConfig watchConfig = new WatchConfig(accountId)) {
        watchConfig.load(git);
        return watchConfig.getProjectWatches();
      }
    }

    public void upsertProjectWatches(Account.Id accountId,
        Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches)
            throws IOException, ConfigInvalidException {
      try (WatchConfig watchConfig = open(accountId)) {
        Map<ProjectWatchKey, Set<NotifyType>> projectWatches =
            watchConfig.getProjectWatches();
        projectWatches.putAll(newProjectWatches);
        commit(watchConfig);
      }
    }

    public void deleteProjectWatches(Account.Id accountId,
        Collection<ProjectWatchKey> projectWatchKeys)
            throws IOException, ConfigInvalidException {
      try (WatchConfig watchConfig = open(accountId)) {
        Map<ProjectWatchKey, Set<NotifyType>> projectWatches =
            watchConfig.getProjectWatches();
        for (ProjectWatchKey key : projectWatchKeys) {
          projectWatches.remove(key);
        }
        commit(watchConfig);
      }
    }

    private WatchConfig open(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      Repository git = repoManager.openRepository(allUsersName);
      WatchConfig watchConfig = new WatchConfig(accountId);
      watchConfig.load(git);
      return watchConfig;
    }

    private void commit(WatchConfig watchConfig)
        throws IOException {
      try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName,
          userFactory.create(watchConfig.accountId))) {
        watchConfig.commit(md);
      }
    }
  }

  @AutoValue
  public abstract static class ProjectWatchKey {
    public static ProjectWatchKey create(Project.NameKey project,
        @Nullable String filter) {
      return new AutoValue_WatchConfig_ProjectWatchKey(project,
          Strings.emptyToNull(filter));
    }

    public abstract Project.NameKey project();
    public abstract @Nullable String filter();
  }

  public enum NotifyType {
    NEW_CHANGES, NEW_PATCHSETS, ALL_COMMENTS, SUBMITTED_CHANGES,
    ABANDONED_CHANGES, ALL
  }

  public static final String FILTER_ALL = "*";

  private static final String WATCH_CONFIG = "watch.config";
  private static final String PROJECT = "project";
  private static final String KEY_NOTIFY = "notify";

  private final Account.Id accountId;
  private final String ref;

  private Repository git;
  private Map<ProjectWatchKey, Set<NotifyType>> projectWatches;

  public WatchConfig(Account.Id accountId) {
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  public void load(Repository git) throws IOException, ConfigInvalidException {
    checkState(this.git == null);
    this.git = git;
    super.load(git);
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    projectWatches = new HashMap<>();
    Config cfg = readConfig(WATCH_CONFIG);
    for (String projectName : cfg.getSubsections(PROJECT)) {
      String[] notifyValues = cfg.getStringList(PROJECT, projectName, KEY_NOTIFY);
      for (String notifyValue : notifyValues) {
        int i = notifyValue.lastIndexOf('[');
        int j = notifyValue.lastIndexOf(']');
        if (i < 0 || j < 0 || j < i) {
          continue;
        }
        String filter = notifyValue.substring(0, i).trim();
        if (filter.isEmpty() || FILTER_ALL.equals(filter)) {
          filter = null;
        }

        ProjectWatchKey key =
            ProjectWatchKey.create(new Project.NameKey(projectName), filter);
        if (!projectWatches.containsKey(key)) {
          projectWatches.put(key, EnumSet.noneOf(NotifyType.class));
        }

        List<String> notifyTypes = Splitter.on(", ").omitEmptyStrings()
            .splitToList(notifyValue.substring(i + 1, j));

        for (String nt : notifyTypes) {
          Optional<NotifyType> notifyType =
              Enums.getIfPresent(NotifyType.class, nt);
          if (!notifyType.isPresent()) {
            continue;
          }
          projectWatches.get(key).add(notifyType.get());
        }
      }
    }
  }

  Map<ProjectWatchKey, Set<NotifyType>> getProjectWatches() {
    checkLoaded();
    return projectWatches;
  }

  public void setProjectWatches(
      Map<ProjectWatchKey, Set<NotifyType>> projectWatches) {
    this.projectWatches = projectWatches;
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    checkLoaded();

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated watch configuration\n");
    }

    Config cfg = readConfig(WATCH_CONFIG);

    for (String projectName : cfg.getSubsections(PROJECT)) {
      cfg.unset(PROJECT, projectName, KEY_NOTIFY);
    }

    Multimap<String, String> notifyValuesByProject = ArrayListMultimap.create();
    for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : projectWatches
        .entrySet()) {
      String filter = e.getKey().filter();
      StringBuilder notifyValue = new StringBuilder();
      notifyValue.append(filter != null ? filter : FILTER_ALL)
          .append(" [")
          .append(Joiner.on(", ").join(e.getValue()))
          .append("]");
      notifyValuesByProject.put(e.getKey().project().get(),
          notifyValue.toString());
    }

    for (Map.Entry<String, Collection<String>> e : notifyValuesByProject.asMap()
        .entrySet()) {
      cfg.setStringList(PROJECT, e.getKey(), KEY_NOTIFY,
          new ArrayList<>(e.getValue()));
    }

    saveConfig(WATCH_CONFIG, cfg);
    return true;
  }

  @Override
  public void close() {
    if (git != null) {
      git.close();
    }
  }

  private void checkLoaded() {
    checkState(projectWatches != null, "project watches not loaded yet");
  }
}

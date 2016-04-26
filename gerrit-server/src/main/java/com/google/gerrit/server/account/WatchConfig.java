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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ‘watch.config’ file in the user branch in the All-Users repository that
 * contains the watch configuration of the user.
 *
 * The 'watch.config' file is a git config file that has one 'project'
 * section for each project watch.
 *
 * The name of the watched project is used as subsection name and the
 * events for which email notifications should be received are
 * represented as 'notify' values in the subsection:
 *
 *   [project "foo"]
 *     notify = ALL_COMMENTS
 *     notify = NEW_PATCHSETS
 *
 * If a project is watched with a filter, the filter is included into the
 * subsection name. This prevents conflicting watch configurations for
 * the same project and filter. When a filter is specified the subsection
 * name is formatted like this: '<project-name>%filter=<filter-query>'.
 * A section with a filter looks like this:
 *
 *   [project "foo%filter=branch:master"]
 *     notify = ALL_COMMENTS
 *     notify = NEW_PATCHSETS
 *
 * For watch configurations that notify on no event 'notify' is set to
 * 'NONE' (because empty sections in git config files are omitted):
 *
 *   [project "foo%filter=branch:master"]
 *     notify = NONE
 *
 * If other notify values are specified in addition to 'NONE' they are
 * ignored.
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

    public Map<ProjectWatchKey, Collection<NotifyType>> getProjectWatches(
        Account.Id accountId) throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName);
          WatchConfig watchConfig = new WatchConfig(accountId)) {
        watchConfig.load(git);
        return watchConfig.getProjectWatches();
      }
    }

    public void upsertProjectWatches(Account.Id accountId,
        Map<ProjectWatchKey, Collection<NotifyType>> newProjectWatches)
            throws IOException, ConfigInvalidException {
      try (WatchConfig watchConfig = open(accountId)) {
        Map<ProjectWatchKey, Collection<NotifyType>> projectWatches =
            watchConfig.getProjectWatches();
        projectWatches.putAll(newProjectWatches);
        commit(watchConfig);
      }
    }

    public void deleteProjectWatches(Account.Id accountId,
        Collection<ProjectWatchKey> projectWatchKeys)
            throws IOException, ConfigInvalidException {
      try (WatchConfig watchConfig = open(accountId)) {
        Map<ProjectWatchKey, Collection<NotifyType>> projectWatches =
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
    private static final String SEPARATOR = "%filter=";

    public static ProjectWatchKey parse(String s) {
      int p = s.indexOf(SEPARATOR);
      if (p >= 0) {
        Project.NameKey project = new Project.NameKey(s.substring(0, p));
        String filter = s.substring(p + SEPARATOR.length());
        if (AccountProjectWatch.FILTER_ALL.equals(filter)) {
          filter = null;
        }
        return create(project, filter);
      }
      return create(new Project.NameKey(s), null);
    }

    public static ProjectWatchKey create(Project.NameKey project,
        @Nullable String filter) {
      return new AutoValue_WatchConfig_ProjectWatchKey(project,
          Strings.emptyToNull(filter));
    }

    public abstract Project.NameKey project();
    public abstract @Nullable String filter();

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(project());
      if (filter() != null) {
        b.append(SEPARATOR)
            .append(filter());
      }
      return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ProjectWatchKey) {
        ProjectWatchKey o = (ProjectWatchKey) obj;
        return Objects.equals(project().get(), o.project().get())
            && Objects.equals(filter(), o.filter());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(project().get(), filter());
    }
  }

  private static final Logger log = LoggerFactory.getLogger(WatchConfig.class);

  private static final String WATCH_CONFIG = "watch.config";
  private static final String PROJECT = "project";
  private static final String KEY_NOTIFY = "notify";
  private static final String NOTIFY_NONE = "NONE";

  private final Account.Id accountId;
  private final String ref;

  private Repository git;
  private Map<ProjectWatchKey, Collection<NotifyType>> projectWatches;

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
    for (String projectWatchKey : cfg.getSubsections(PROJECT)) {
      ProjectWatchKey key = ProjectWatchKey.parse(projectWatchKey);
      projectWatches.put(key, new HashSet<NotifyType>());

      List<String> notifyValues = Arrays
          .asList(cfg.getStringList(PROJECT, projectWatchKey, KEY_NOTIFY));
      if (!notifyValues.contains(NOTIFY_NONE)) {
        for (String notify : notifyValues) {
          try {
            projectWatches.get(key)
                .add(AccountProjectWatch.NotifyType.valueOf(notify));
          } catch (IllegalArgumentException e) {
            log.warn(String.format(
                "Project watch configuration %s of account %d"
                    + " contains invalid notify type: %s",
                projectWatchKey, accountId.get(), notify), e);
          }
        }
      }
    }
  }

  Map<ProjectWatchKey, Collection<NotifyType>> getProjectWatches() {
    checkLoaded();
    return projectWatches;
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    checkLoaded();

    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated watch configuration\n");
    }

    Config cfg = readConfig(WATCH_CONFIG);
    clearSection(cfg, PROJECT);
    for (Map.Entry<ProjectWatchKey, Collection<NotifyType>> e : projectWatches
        .entrySet()) {
      if (e.getValue().isEmpty()) {
        // set notify to 'none' since empty sections are not persisted
        cfg.setString(PROJECT, e.getKey().toString(), KEY_NOTIFY, NOTIFY_NONE);
      } else {
        List<String> notifyValues = FluentIterable.from(e.getValue())
            .transform(new Function<NotifyType, String>() {
              @Override
              public String apply(NotifyType notify) {
                return notify.name();
              }
            }).toList();
        cfg.setStringList(PROJECT, e.getKey().toString(), KEY_NOTIFY,
            notifyValues);
      }
    }
    saveConfig(WATCH_CONFIG, cfg);
    return true;
  }

  private static void clearSection(Config cfg, String section) {
    for (String subsection : cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }

  @Override
  public void close() {
    if (git != null) {
      git.close();
    }
  }

  private void checkLoaded() {
    checkNotNull(projectWatches, "project watches not loaded yet");
  }
}

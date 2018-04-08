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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.git.ValidationError;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Parses/writes project watches from/to a {@link Config} file.
 *
 * <p>This is a low-level API. Read/write of project watches in a user branch should be done through
 * {@link AccountsUpdate} or {@link AccountConfig}.
 *
 * <p>The config file has one 'project' section for all project watches of a project.
 *
 * <p>The project name is used as subsection name and the filters with the notify types that decide
 * for which events email notifications should be sent are represented as 'notify' values in the
 * subsection. A 'notify' value is formatted as {@code <filter>
 * [<comma-separated-list-of-notify-types>]}:
 *
 * <pre>
 *   [project "foo"]
 *     notify = * [ALL_COMMENTS]
 *     notify = branch:master [ALL_COMMENTS, NEW_PATCHSETS]
 *     notify = branch:master owner:self [SUBMITTED_CHANGES]
 * </pre>
 *
 * <p>If two notify values in the same subsection have the same filter they are merged on the next
 * save, taking the union of the notify types.
 *
 * <p>For watch configurations that notify on no event the list of notify types is empty:
 *
 * <pre>
 *   [project "foo"]
 *     notify = branch:master []
 * </pre>
 *
 * <p>Unknown notify types are ignored and removed on save.
 *
 * <p>The project watches are lazily parsed.
 */
public class ProjectWatches {
  @AutoValue
  public abstract static class ProjectWatchKey {
    public static ProjectWatchKey create(Project.NameKey project, @Nullable String filter) {
      return new AutoValue_ProjectWatches_ProjectWatchKey(project, Strings.emptyToNull(filter));
    }

    public abstract Project.NameKey project();

    public abstract @Nullable String filter();
  }

  public enum NotifyType {
    // sort by name, except 'ALL' which should stay last
    ABANDONED_CHANGES,
    ALL_COMMENTS,
    NEW_CHANGES,
    NEW_PATCHSETS,
    SUBMITTED_CHANGES,

    ALL
  }

  public static final String FILTER_ALL = "*";

  public static final String WATCH_CONFIG = "watch.config";
  public static final String PROJECT = "project";
  public static final String KEY_NOTIFY = "notify";

  private final Account.Id accountId;
  private final Config cfg;
  private final ValidationError.Sink validationErrorSink;

  private ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches;

  ProjectWatches(Account.Id accountId, Config cfg, ValidationError.Sink validationErrorSink) {
    this.accountId = checkNotNull(accountId, "accountId");
    this.cfg = checkNotNull(cfg, "cfg");
    this.validationErrorSink = checkNotNull(validationErrorSink, "validationErrorSink");
  }

  public ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> getProjectWatches() {
    if (projectWatches == null) {
      parse();
    }
    return projectWatches;
  }

  public void parse() {
    projectWatches = parse(accountId, cfg, validationErrorSink);
  }

  /**
   * Parses project watches from the given config file and returns them as a map.
   *
   * <p>A project watch is defined on a project and has a filter to match changes for which the
   * project watch should be applied. The project and the filter form the map key. The map value is
   * a set of notify types that decide for which events email notifications should be sent.
   *
   * <p>A project watch on the {@code All-Projects} project applies for all projects unless the
   * project has a matching project watch.
   *
   * <p>A project watch can have an empty set of notify types. An empty set of notify types means
   * that no notification for matching changes should be set. This is different from no project
   * watch as it overwrites matching project watches from the {@code All-Projects} project.
   *
   * <p>Since we must be able to differentiate a project watch with an empty set of notify types
   * from no project watch we can't use a {@link Multimap} as return type.
   *
   * @param accountId the ID of the account for which the project watches should be parsed
   * @param cfg the config file from which the project watches should be parsed
   * @param validationErrorSink validation error sink
   * @return the parsed project watches
   */
  @VisibleForTesting
  public static ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> parse(
      Account.Id accountId, Config cfg, ValidationError.Sink validationErrorSink) {
    Map<ProjectWatchKey, Set<NotifyType>> projectWatches = new HashMap<>();
    for (String projectName : cfg.getSubsections(PROJECT)) {
      String[] notifyValues = cfg.getStringList(PROJECT, projectName, KEY_NOTIFY);
      for (String nv : notifyValues) {
        if (Strings.isNullOrEmpty(nv)) {
          continue;
        }

        NotifyValue notifyValue =
            NotifyValue.parse(accountId, projectName, nv, validationErrorSink);
        if (notifyValue == null) {
          continue;
        }

        ProjectWatchKey key =
            ProjectWatchKey.create(new Project.NameKey(projectName), notifyValue.filter());
        if (!projectWatches.containsKey(key)) {
          projectWatches.put(key, EnumSet.noneOf(NotifyType.class));
        }
        projectWatches.get(key).addAll(notifyValue.notifyTypes());
      }
    }
    return immutableCopyOf(projectWatches);
  }

  public Config save(Map<ProjectWatchKey, Set<NotifyType>> projectWatches) {
    this.projectWatches = immutableCopyOf(projectWatches);

    for (String projectName : cfg.getSubsections(PROJECT)) {
      cfg.unsetSection(PROJECT, projectName);
    }

    ListMultimap<String, String> notifyValuesByProject =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : projectWatches.entrySet()) {
      NotifyValue notifyValue = NotifyValue.create(e.getKey().filter(), e.getValue());
      notifyValuesByProject.put(e.getKey().project().get(), notifyValue.toString());
    }

    for (Map.Entry<String, Collection<String>> e : notifyValuesByProject.asMap().entrySet()) {
      cfg.setStringList(PROJECT, e.getKey(), KEY_NOTIFY, new ArrayList<>(e.getValue()));
    }

    return cfg;
  }

  private static ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> immutableCopyOf(
      Map<ProjectWatchKey, Set<NotifyType>> projectWatches) {
    ImmutableMap.Builder<ProjectWatchKey, ImmutableSet<NotifyType>> b = ImmutableMap.builder();
    projectWatches
        .entrySet()
        .stream()
        .forEach(e -> b.put(e.getKey(), ImmutableSet.copyOf(e.getValue())));
    return b.build();
  }

  @AutoValue
  public abstract static class NotifyValue {
    public static NotifyValue parse(
        Account.Id accountId,
        String project,
        String notifyValue,
        ValidationError.Sink validationErrorSink) {
      notifyValue = notifyValue.trim();
      int i = notifyValue.lastIndexOf('[');
      if (i < 0 || notifyValue.charAt(notifyValue.length() - 1) != ']') {
        validationErrorSink.error(
            new ValidationError(
                WATCH_CONFIG,
                String.format(
                    "Invalid project watch of account %d for project %s: %s",
                    accountId.get(), project, notifyValue)));
        return null;
      }
      String filter = notifyValue.substring(0, i).trim();
      if (filter.isEmpty() || FILTER_ALL.equals(filter)) {
        filter = null;
      }

      Set<NotifyType> notifyTypes = EnumSet.noneOf(NotifyType.class);
      if (i + 1 < notifyValue.length() - 2) {
        for (String nt :
            Splitter.on(',')
                .trimResults()
                .splitToList(notifyValue.substring(i + 1, notifyValue.length() - 1))) {
          NotifyType notifyType = Enums.getIfPresent(NotifyType.class, nt).orNull();
          if (notifyType == null) {
            validationErrorSink.error(
                new ValidationError(
                    WATCH_CONFIG,
                    String.format(
                        "Invalid notify type %s in project watch "
                            + "of account %d for project %s: %s",
                        nt, accountId.get(), project, notifyValue)));
            continue;
          }
          notifyTypes.add(notifyType);
        }
      }
      return create(filter, notifyTypes);
    }

    public static NotifyValue create(@Nullable String filter, Collection<NotifyType> notifyTypes) {
      return new AutoValue_ProjectWatches_NotifyValue(
          Strings.emptyToNull(filter), Sets.immutableEnumSet(notifyTypes));
    }

    public abstract @Nullable String filter();

    public abstract ImmutableSet<NotifyType> notifyTypes();

    @Override
    public String toString() {
      List<NotifyType> notifyTypes = new ArrayList<>(notifyTypes());
      StringBuilder notifyValue = new StringBuilder();
      notifyValue.append(firstNonNull(filter(), FILTER_ALL)).append(" [");
      Joiner.on(", ").appendTo(notifyValue, notifyTypes);
      notifyValue.append("]");
      return notifyValue.toString();
    }
  }
}

// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;

/**
 * This class can tell efficiently if changes are visible to a user. It is intended to be used when
 * serving Git traffic on the Git wire protocol and in similar use cases when we need to know
 * efficiently if a (potentially large number) of changes are visible to a user.
 *
 * <p>The efficiency of this class comes from heuristic optimization:
 *
 * <ul>
 *   <li>For a low number of expected checks, we check visibility one-by-one.
 *   <li>For a high number of expected checks we use the ChangesByProjectCache.
 * </ul>
 *
 * <p>Changes that fail to load are pretended to be invisible. This is important on the Git paths as
 * we don't want to advertise change refs where we were unable to check the visibility (e.g. due to
 * data corruption on that change). At the same time, the overall operation should succeed as
 * otherwise a single broken change would break Git operations for an entire repo.
 */
public class GitVisibleChangeFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int CHANGE_LIMIT_FOR_DIRECT_FILTERING = 5;

  private GitVisibleChangeFilter() {}

  /** Returns a map of all visible changes. Might pretend old changes are invisible. */
  static ImmutableMap<Change.Id, ChangeData> getVisibleChanges(
      ChangesByProjectCache changesByProjectCache,
      ChangeData.Factory changeDataFactory,
      Project.NameKey projectName,
      PermissionBackend.ForProject forProject,
      Repository repository,
      ImmutableSet<Change.Id> changes) {
    Stream<ChangeData> changeDatas = Stream.empty();
    if (changes.size() < CHANGE_LIMIT_FOR_DIRECT_FILTERING) {
      changeDatas = loadChangeDatasOneByOne(changes, changeDataFactory, projectName);
    } else {
      try {
        changeDatas = changesByProjectCache.streamChangeDatas(projectName, repository);
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Unable to scanChangeIds for %s", projectName);
      }
    }

    return changeDatas
        .filter(cd -> changes.contains(cd.getId()))
        .filter(
            cd -> {
              try {
                return forProject.change(cd).test(ChangePermission.READ);
              } catch (PermissionBackendException e) {
                // This is almost the same as the message .testOrFalse() would log, but with the
                // added context of the change and coming from this class
                logger.atWarning().withCause(e).log(
                    "Cannot test read permission for %s; assuming not visible", cd);
                return false;
              }
            })
        .collect(toImmutableMap(ChangeData::getId, Function.identity()));
  }

  /** Get a stream of changes by loading them individually. */
  private static Stream<ChangeData> loadChangeDatasOneByOne(
      Set<Change.Id> ids, ChangeData.Factory changeDataFactory, Project.NameKey projectName) {
    return ids.stream()
        .map(
            id -> {
              try {
                ChangeData cd = changeDataFactory.create(projectName, id);
                cd.notes(); // Make sure notes are available. This will trigger loading notes and
                // throw an exception in case the change is corrupt and can't be loaded. It will
                // then be omitted from the result.
                return cd;
              } catch (Exception e) {
                // We drop changes that we can't load. The repositories contain 'dead' change refs
                // and we want to overall operation to continue.
                logger.atFinest().withCause(e).log("Can't load Change notes for %s", id);
                return null;
              }
            })
        .filter(Objects::nonNull);
  }
}

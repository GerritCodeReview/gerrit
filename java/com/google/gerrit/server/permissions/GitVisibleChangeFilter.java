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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.notedb.ChangeNotes;
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
 *   <li>For a high number of expected checks and settings where the change index is available, we
 *       load the N most recent changes from the index and filter them by visibility. This is fast,
 *       but comes with the caveat that older changes are pretended to be invisible.
 *   <li>For a high number of expected checks and settings where the change index is unavailable, we
 *       scan the repo and determine visibility one-by-one. This is *very* expensive.
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
      @Nullable SearchingChangeCacheImpl searchingChangeCache,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      Project.NameKey projectName,
      PermissionBackend.ForProject forProject,
      Repository repository,
      ImmutableSet<Change.Id> changes) {
    Stream<ChangeData> changeDatas;
    if (changes.size() < CHANGE_LIMIT_FOR_DIRECT_FILTERING) {
      changeDatas = loadChangeDatasOneByOne(changes, changeDataFactory, projectName);
    } else if (searchingChangeCache != null) {
      changeDatas = searchingChangeCache.getChangeData(projectName);
    } else {
      changeDatas =
          scanRepoForChangeDatas(changeNotesFactory, changeDataFactory, repository, projectName);
    }

    return changeDatas
        .filter(cd -> changes.contains(cd.getId()))
        .filter(
            cd -> {
              try {
                return forProject.change(cd).test(ChangePermission.READ);
              } catch (PermissionBackendException e) {
                throw new StorageException(e);
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

  /** Get a stream of all changes by scanning the repo. This is extremely slow. */
  private static Stream<ChangeData> scanRepoForChangeDatas(
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      Repository repository,
      Project.NameKey projectName) {
    Stream<ChangeData> cds;
    try {
      cds =
          changeNotesFactory
              .scan(repository, projectName)
              .map(
                  notesResult -> {
                    if (!notesResult.error().isPresent()) {
                      return changeDataFactory.create(notesResult.notes());
                    } else {
                      logger.atWarning().withCause(notesResult.error().get()).log(
                          "Unable to load ChangeNotes for %s", notesResult.id());
                      return null;
                    }
                  })
              .filter(Objects::nonNull);
    } catch (IOException e) {
      throw new StorageException(e);
    }
    return cds;
  }
}

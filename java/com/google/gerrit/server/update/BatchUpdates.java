// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.LimitExceededException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate.ChangesHandle;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs execute methods of a collection of {@link BatchUpdate}s calling listeners when appropriate.
 *
 * <p>This class does not maintain any state about the updates it executes. The only reason it is
 * non-static is to provide convenient access to {@link ChangeData.Factory} without needing to
 * provide one as an argument.
 */
@Singleton
public class BatchUpdates {
  public class Result {
    private final Map<Change.Id, ChangeData> changeDatas;

    private Result() {
      this(new HashMap<>());
    }

    private Result(Map<Change.Id, ChangeData> changeDatas) {
      this.changeDatas = changeDatas;
    }

    /**
     * Returns the updated {@link ChangeData} for the given project and change ID.
     *
     * <p>If the requested {@link ChangeData} was already loaded after the {@link BatchUpdate} has
     * been executed the cached {@link ChangeData} instance is returned, otherwise the requested
     * {@link ChangeData} is loaded and put into the cache.
     */
    public ChangeData getChangeData(Project.NameKey projectName, Change.Id changeId) {
      return changeDatas.computeIfAbsent(
          changeId, id -> changeDataFactory.create(projectName, changeId));
    }
  }

  private final ChangeData.Factory changeDataFactory;

  @Inject
  BatchUpdates(ChangeData.Factory changeDataFactory) {
    this.changeDataFactory = changeDataFactory;
  }

  @CanIgnoreReturnValue
  public Result execute(
      Collection<BatchUpdate> updates, ImmutableList<BatchUpdateListener> listeners, boolean dryrun)
      throws UpdateException, RestApiException {
    requireNonNull(listeners);
    if (updates.isEmpty()) {
      return new Result();
    }

    checkDifferentProject(updates);

    try {
      List<ListenableFuture<ChangeData>> indexFutures = new ArrayList<>();
      List<ChangesHandle> changesHandles = new ArrayList<>(updates.size());
      try {
        for (BatchUpdate u : updates) {
          u.executeUpdateRepo();
        }
        notifyAfterUpdateRepo(listeners);
        for (BatchUpdate u : updates) {
          changesHandles.add(u.executeChangeOps(listeners, dryrun));
        }
        for (ChangesHandle h : changesHandles) {
          h.execute();
          if (h.requiresReindex()) {
            indexFutures.addAll(h.startIndexFutures());
          }
        }
        notifyAfterUpdateRefs(listeners);
        notifyAfterUpdateChanges(listeners);
      } finally {
        for (ChangesHandle h : changesHandles) {
          h.close();
        }
      }

      Map<Change.Id, ChangeData> changeDatas =
          Futures.allAsList(indexFutures).get().stream()
              // filter out null values that were returned for change deletions
              .filter(Objects::nonNull)
              .collect(toMap(cd -> cd.change().getId(), Function.identity()));

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates.forEach(BatchUpdate::fireRefChangeEvents);

      if (!dryrun) {
        for (BatchUpdate u : updates) {
          u.executePostOps(changeDatas);
        }
      }

      return new Result(changeDatas);
    } catch (Exception e) {
      wrapAndThrowException(e);
      return new Result();
    }
  }

  private static void notifyAfterUpdateRepo(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRepos();
    }
  }

  private static void notifyAfterUpdateRefs(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRefs();
    }
  }

  private static void notifyAfterUpdateChanges(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateChanges();
    }
  }

  private static void checkDifferentProject(Collection<BatchUpdate> updates) {
    Multiset<Project.NameKey> projectCounts =
        updates.stream().map(u -> u.getProject()).collect(toImmutableMultiset());
    checkArgument(
        projectCounts.entrySet().size() == updates.size(),
        "updates must all be for different projects, got: %s",
        projectCounts);
  }

  private static void wrapAndThrowException(Exception e) throws UpdateException, RestApiException {
    // Convert common non-REST exception types with user-visible messages to corresponding REST
    // exception types.
    if (e instanceof InvalidChangeOperationException || e instanceof LimitExceededException) {
      throw new ResourceConflictException(e.getMessage(), e);
    } else if (e instanceof NoSuchChangeException
        || e instanceof NoSuchRefException
        || e instanceof NoSuchProjectException) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } else if (e instanceof CommentsRejectedException) {
      // SC_BAD_REQUEST is not ideal because it's not a syntactic error, but there is no better
      // status code and it's isolated in monitoring.
      throw new BadRequestException(e.getMessage(), e);
    }

    Throwables.throwIfUnchecked(e);

    // Propagate REST API exceptions thrown by operations; they commonly throw exceptions like
    // ResourceConflictException to indicate an atomic update failure.
    Throwables.throwIfInstanceOf(e, UpdateException.class);
    Throwables.throwIfInstanceOf(e, RestApiException.class);

    // Otherwise, wrap in a generic UpdateException, which does not include a user-visible message.
    throw new UpdateException(e);
  }
}

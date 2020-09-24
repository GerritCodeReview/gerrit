// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.base.Throwables;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import com.google.gerrit.server.update.BatchUpdate.ChangesHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Runs a collection of BatchUpdates as a Submission. */
public class MultibatchExecutor {

  private MultibatchExecutor() {}

  public static void execute(
      Collection<BatchUpdate> updates, boolean dryrun, BatchUpdateListener listener)
      throws UpdateException, RestApiException {
    requireNonNull(listener);
    if (updates.isEmpty()) {
      return;
    }

    checkDifferentProject(updates);

    try {
      List<ListenableFuture<?>> indexFutures = new ArrayList<>();
      List<ChangesHandle> changesHandles = new ArrayList<>(updates.size());
      try {
        for (BatchUpdate u : updates) {
          u.executeUpdateRepo();
        }
        listener.afterUpdateRepos();

        for (BatchUpdate u : updates) {
          changesHandles.add(u.executeChangeOps(listener, dryrun));
        }
        for (ChangesHandle h : changesHandles) {
          h.execute();
          indexFutures.addAll(h.startIndexFutures());
        }
        listener.afterUpdateRefs();
        listener.afterUpdateChanges();
      } finally {
        for (ChangesHandle h : changesHandles) {
          h.close();
        }
      }

      ((ListenableFuture<?>) Futures.allAsList(indexFutures)).get();

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates.stream().forEach(BatchUpdate::fireRefChangeEvent);

      if (!dryrun) {
        for (BatchUpdate u : updates) {
          u.executePostOps();
        }
      }
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  private static void checkDifferentProject(Collection<BatchUpdate> updates) {
    Multiset<Project.NameKey> projectCounts =
        updates.stream().map(BatchUpdate::getProject).collect(toImmutableMultiset());
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

// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.patch.DiffExecutor;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Implementation of FileInfoJson which uses {@link FileInfoJsonOldImpl}, but also runs {@link
 * FileInfoJsonNewImpl} asynchronously and compares the results. This implementation is temporary
 * and will be used to verify that the results are the same.
 */
public class FileInfoJsonComparingImpl implements FileInfoJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final FileInfoJsonOldImpl oldImpl;
  private final FileInfoJsonNewImpl newImpl;
  private final ExecutorService executor;
  private final Metrics metrics;

  /**
   * TODO(ghareeb): These metrics are temporary for launching the new diff cache redesign and are
   * not documented. These will be removed soon.
   */
  @VisibleForTesting
  @Singleton
  static class Metrics {
    private enum Status {
      MATCH,
      MISMATCH,
      ERROR
    }

    final Counter1<Status> diffs;

    @Inject
    Metrics(MetricMaker metricMaker) {
      diffs =
          metricMaker.newCounter(
              "diff/list_files/dark_launch",
              new Description(
                      "Total number of matching, non-matching, or error in list-files diffs in the old and new diff cache implementations.")
                  .setRate()
                  .setUnit("count"),
              Field.ofEnum(Status.class, "type", Metadata.Builder::eventType).build());
    }
  }

  @Inject
  public FileInfoJsonComparingImpl(
      FileInfoJsonOldImpl oldImpl,
      FileInfoJsonNewImpl newImpl,
      @DiffExecutor ExecutorService executor,
      Metrics metrics) {
    this.oldImpl = oldImpl;
    this.newImpl = newImpl;
    this.executor = executor;
    this.metrics = metrics;
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    Map<String, FileInfo> result = oldImpl.getFileInfoMap(change, objectId, base);
    @SuppressWarnings("unused")
    Future<?> ignored =
        executor.submit(
            () -> {
              try {
                Map<String, FileInfo> fileInfoNew = newImpl.getFileInfoMap(change, objectId, base);
                compareAndLogMetrics(
                    result,
                    fileInfoNew,
                    String.format(
                        "Mismatch comparing old and new diff implementations for change: %s, objectId: %s and base: %s",
                        change, objectId, base == null ? "none" : base.id()));
              } catch (ResourceConflictException | PatchListNotAvailableException e) {
                // If an exception happens while evaluating the new diff, increment the non-matching
                // counter
                metrics.diffs.increment(Metrics.Status.ERROR);
                logger.atWarning().withCause(e).log(
                    "Error comparing old and new diff implementations.");
              }
            });
    return result;
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    Map<String, FileInfo> result = oldImpl.getFileInfoMap(project, objectId, parentNum);
    @SuppressWarnings("unused")
    Future<?> ignored =
        executor.submit(
            () -> {
              try {
                Map<String, FileInfo> resultNew =
                    newImpl.getFileInfoMap(project, objectId, parentNum);
                compareAndLogMetrics(
                    result,
                    resultNew,
                    String.format(
                        "Mismatch comparing old and new diff implementations for project: %s, objectId: %s and parentNum: %d",
                        project, objectId, parentNum));
              } catch (ResourceConflictException | PatchListNotAvailableException e) {
                // If an exception happens while evaluating the new diff, increment the non-matching
                // ctr
                metrics.diffs.increment(Metrics.Status.ERROR);
                logger.atWarning().withCause(e).log(
                    "Error comparing old and new diff implementations.");
              }
            });
    return result;
  }

  private void compareAndLogMetrics(
      Map<String, FileInfo> fileInfoMapOld,
      Map<String, FileInfo> fileInfoMapNew,
      String warningMessage) {
    if (fileInfoMapOld.equals(fileInfoMapNew)) {
      metrics.diffs.increment(Metrics.Status.MATCH);
      return;
    }
    metrics.diffs.increment(Metrics.Status.MISMATCH);
    logger.atWarning().log(
        warningMessage
            + "\n"
            + "Result using old impl: "
            + fileInfoMapOld
            + "\n"
            + "Result using new impl: "
            + fileInfoMapNew);
  }
}

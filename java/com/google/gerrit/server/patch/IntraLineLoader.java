// Copyright (C) 2009 The Android Open Source Project
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
//

package com.google.gerrit.server.patch;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Config;

public class IntraLineLoader implements Callable<IntraLineDiff> {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    IntraLineLoader create(IntraLineDiffKey key, IntraLineDiffArgs args);
  }

  private final ExecutorService diffExecutor;
  private final long timeoutMillis;
  private final IntraLineDiffKey key;
  private final IntraLineDiffArgs args;

  @Inject
  IntraLineLoader(
      @DiffExecutor ExecutorService diffExecutor,
      @GerritServerConfig Config cfg,
      @Assisted IntraLineDiffKey key,
      @Assisted IntraLineDiffArgs args) {
    this.diffExecutor = diffExecutor;
    timeoutMillis =
        ConfigUtil.getTimeUnit(
            cfg,
            "cache",
            PatchListCacheImpl.INTRA_NAME,
            "timeout",
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
    this.key = key;
    this.args = args;
  }

  @Override
  public IntraLineDiff call() throws Exception {
    Future<IntraLineDiff> result =
        diffExecutor.submit(
            () ->
                IntraLineLoader.compute(
                    args.aText(), args.bText(), args.edits(), args.editsDueToRebase()));
    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      logger.atWarning().log(
          "%s ms timeout reached for IntraLineDiff"
              + " in project %s on commit %s for path %s comparing %s..%s",
          timeoutMillis,
          args.project(),
          args.commit().name(),
          args.path(),
          key.getBlobA().name(),
          key.getBlobB().name());
      result.cancel(true);
      return new IntraLineDiff(IntraLineDiff.Status.TIMEOUT);
    } catch (ExecutionException e) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      Throwables.throwIfInstanceOf(e.getCause(), Exception.class);
      throw new Exception(e.getMessage(), e.getCause());
    }
  }

  public static IntraLineDiff compute(
      Text aText,
      Text bText,
      ImmutableList<Edit> immutableEdits,
      ImmutableSet<Edit> immutableEditsDueToRebase) {
    ImmutableList<Edit> computed =
        com.google.gitiles.diff.IntraLineDiff.compute(
            aText, bText, immutableEdits, immutableEditsDueToRebase);
    List<Edit> edits = new ArrayList<>(computed.size());
    // Convert back to Gerrit's ReplaceEdit before caching: the persistent intraline
    // cache serializes internal edits only for Gerrit's own type, so caching
    // Gitiles' sibling would drop them.
    for (Edit e : computed) {
      if (e instanceof com.google.gitiles.diff.ReplaceEdit re) {
        edits.add(new ReplaceEdit(re, re.getInternalEdits()));
      } else {
        edits.add(e);
      }
    }
    return new IntraLineDiff(edits);
  }
}

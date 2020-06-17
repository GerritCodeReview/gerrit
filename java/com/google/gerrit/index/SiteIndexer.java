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

package com.google.gerrit.index;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.io.NullOutputStream;

/** Base class for implementations that can index all entities of a given type. */
public abstract class SiteIndexer<K, V, I extends Index<K, V>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Result of an operation to index a subset or all of the entities of a given type. */
  @AutoValue
  public abstract static class Result {
    public abstract long elapsedNanos();

    public abstract boolean success();

    public abstract int doneCount();

    public abstract int failedCount();

    public static Result create(Stopwatch sw, boolean success, int done, int failed) {
      return new AutoValue_SiteIndexer_Result(
          sw.elapsed(TimeUnit.NANOSECONDS), success, done, failed);
    }

    public long elapsed(TimeUnit timeUnit) {
      return timeUnit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
    }
  }

  protected int totalWork = -1;
  protected OutputStream progressOut = NullOutputStream.INSTANCE;
  protected PrintWriter verboseWriter = newPrintWriter(NullOutputStream.INSTANCE);

  public void setTotalWork(int num) {
    totalWork = num;
  }

  public void setProgressOut(OutputStream out) {
    progressOut = requireNonNull(out);
  }

  public void setVerboseOut(OutputStream out) {
    verboseWriter = newPrintWriter(requireNonNull(out));
  }

  /** Indexes all entities for the provided index. */
  public abstract Result indexAll(I index);

  protected final void addErrorListener(
      ListenableFuture<?> future, String desc, ProgressMonitor progress, AtomicBoolean ok) {
    future.addListener(
        new ErrorListener(future, desc, progress, ok), MoreExecutors.directExecutor());
  }

  protected PrintWriter newPrintWriter(OutputStream out) {
    return new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
  }

  private static class ErrorListener implements Runnable {
    private final ListenableFuture<?> future;
    private final String desc;
    private final ProgressMonitor progress;
    private final AtomicBoolean ok;

    private ErrorListener(
        ListenableFuture<?> future, String desc, ProgressMonitor progress, AtomicBoolean ok) {
      this.future = future;
      this.desc = desc;
      this.progress = progress;
      this.ok = ok;
    }

    @Override
    public void run() {
      try {
        future.get();
      } catch (RejectedExecutionException e) {
        // Server shutdown, don't spam the logs.
        failSilently();
      } catch (ExecutionException | InterruptedException e) {
        fail(e);
      } catch (RuntimeException e) {
        failAndThrow(e);
      } catch (Error e) {
        // Can't join with RuntimeException because "RuntimeException |
        // Error" becomes Throwable, which messes with signatures.
        failAndThrow(e);
      } finally {
        synchronized (progress) {
          progress.update(1);
        }
      }
    }

    private void failSilently() {
      ok.set(false);
    }

    private void fail(Throwable t) {
      logger.atSevere().withCause(t).log("Failed to index %s", desc);
      ok.set(false);
    }

    private void failAndThrow(RuntimeException e) {
      fail(e);
      throw e;
    }

    private void failAndThrow(Error e) {
      fail(e);
      throw e;
    }
  }
}

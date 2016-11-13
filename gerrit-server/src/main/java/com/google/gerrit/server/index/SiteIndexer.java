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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiteIndexer<K, V, I extends Index<K, V>> {
  private static final Logger log = LoggerFactory.getLogger(SiteIndexer.class);

  public static class Result {
    private final long elapsedNanos;
    private final boolean success;
    private final int done;
    private final int failed;

    public Result(Stopwatch sw, boolean success, int done, int failed) {
      this.elapsedNanos = sw.elapsed(TimeUnit.NANOSECONDS);
      this.success = success;
      this.done = done;
      this.failed = failed;
    }

    public boolean success() {
      return success;
    }

    public int doneCount() {
      return done;
    }

    public int failedCount() {
      return failed;
    }

    public long elapsed(TimeUnit timeUnit) {
      return timeUnit.convert(elapsedNanos, TimeUnit.NANOSECONDS);
    }
  }

  protected int totalWork = -1;
  protected OutputStream progressOut = NullOutputStream.INSTANCE;
  protected PrintWriter verboseWriter = new PrintWriter(NullOutputStream.INSTANCE);

  public void setTotalWork(int num) {
    totalWork = num;
  }

  public void setProgressOut(OutputStream out) {
    progressOut = checkNotNull(out);
  }

  public void setVerboseOut(OutputStream out) {
    verboseWriter = new PrintWriter(checkNotNull(out));
  }

  public abstract Result indexAll(I index);

  protected final void addErrorListener(
      ListenableFuture<?> future, String desc, ProgressMonitor progress, AtomicBoolean ok) {
    future.addListener(
        new ErrorListener(future, desc, progress, ok), MoreExecutors.directExecutor());
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

    private void fail(Throwable t) {
      log.error("Failed to index " + desc, t);
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

// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.patch.IntraLineLoader.log;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.patch.FileHeader;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class DiffWorkerPool {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(DiffWorkerPool.class);
    }
  }

  private final BlockingQueue<Worker> workerPool;

  @Inject
  public DiffWorkerPool(@GerritServerConfig Config cfg) {
    int workers = cfg.getInt(
        "cache", PatchListCacheImpl.FILE_NAME, "maxIdleWorkers",
        Runtime.getRuntime().availableProcessors() * 3 / 2);
    workerPool = new ArrayBlockingQueue<>(workers, true /* fair */);
  }

  Worker acquire() {
    Worker w = workerPool.poll();
    if (w == null) {
      // If no worker is immediately available, start a new one.
      // Maximum parallelism is controlled by the web server.
      w = new Worker();
      w.start();
    }
    return w;
  }

  void release(Worker w) {
    if (!workerPool.offer(w)) {
      // If the idle worker pool is full, terminate the worker.
      w.shutdownGracefully();
    }
  }

  static class Worker extends Thread {
    private static final AtomicInteger count = new AtomicInteger(1);

    private final ArrayBlockingQueue<Input> input;
    private final ArrayBlockingQueue<Result> result;

    Worker() {
      input = new ArrayBlockingQueue<>(1);
      result = new ArrayBlockingQueue<>(1);

      setName("Diff-" + count.getAndIncrement());
      setDaemon(true);
    }

    Result toFileHeaderWithTimeout(PatchListKey key,
        DiffFormatter diffFormatter, DiffEntry diffEntry, long timeoutMillis)
        throws InterruptedException {
      if (!input.offer(new Input(diffFormatter, diffEntry))) {
        log.error("Cannot enqueue task to thread " + getName());
        return Result.TIMEOUT;
      }

      Result r = result.poll(timeoutMillis, TimeUnit.MILLISECONDS);
      if (r != null) {
        return r;
      } else {
        log.warn(timeoutMillis + " ms timeout reached for Diff loader"
            + " in project " + key.projectKey.get()
            + " on commit " + key.getNewId()
            + " on path " + diffEntry.getNewPath()
            + " comparing " + diffEntry.getOldId()
            + ".." +diffEntry.getNewId()
            + ".  Killing " + getName());
        forcefullyKillThreadInAnUglyWay();
        return Result.TIMEOUT;
      }
    }

    @SuppressWarnings("deprecation")
    private void forcefullyKillThreadInAnUglyWay() {
      try {
        stop();
      } catch (Throwable error) {
        // Ignore any reason the thread won't stop.
        log.error("Cannot stop runaway thread " + getName(), error);
      }
    }

    private void shutdownGracefully() {
      if (!input.offer(Input.END_THREAD)) {
        log.error("Cannot gracefully stop thread " + getName());
      }
    }

    @Override
    public void run() {
      try {
        for (;;) {
          Input in;
          try {
            in = input.take();
          } catch (InterruptedException e) {
            log.error("Unexpected interrupt on " + getName());
            continue;
          }

          if (in == Input.END_THREAD) {
            return;
          }

          Result r;
          try {
            r = new Result(in.diffFormatter.toFileHeader(in.diffEntry));
          } catch (Exception error) {
            r = new Result(error);
          }

          if (!result.offer(r)) {
            log.error("Cannot return result from " + getName());
          }
        }
      } catch (ThreadDeath iHaveBeenShot) {
        // Handle thread death by gracefully returning to the caller,
        // allowing the thread to be destroyed.
      }
    }

    private static class Input {
      static final Input END_THREAD = new Input(null, null);

      final DiffFormatter diffFormatter;
      final DiffEntry diffEntry;

      Input(DiffFormatter diffFormatter, DiffEntry diffEntry) {
        this.diffFormatter = diffFormatter;
        this.diffEntry = diffEntry;
      }
    }

    static class Result {
      static final Result TIMEOUT = new Result((FileHeader) null);

      final FileHeader fileHeader;
      final Exception error;

      Result(FileHeader fileHeader) {
        this.fileHeader = fileHeader;
        this.error = null;
      }

      Result(Exception error) {
        this.fileHeader = null;
        this.error = error;
      }
    }
  }
}

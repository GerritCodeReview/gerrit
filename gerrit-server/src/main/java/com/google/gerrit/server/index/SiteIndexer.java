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

import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

public abstract class SiteIndexer<K, V, I extends Index<K, V>> {
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
  protected PrintWriter verboseWriter =
      new PrintWriter(NullOutputStream.INSTANCE);

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
}

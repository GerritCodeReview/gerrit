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

package com.google.gerrit.pgm.http.jetty;

import com.google.gerrit.server.cache.ThreadLocalCacheCleaner;
import java.util.concurrent.BlockingQueue;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/** Overrides the default implementation in order to perform thread's local cache cleanup. */
class QueuedThreadPoolWithThreadLocalCacheCleaner extends QueuedThreadPool {
  QueuedThreadPoolWithThreadLocalCacheCleaner(
      @Name("maxThreads") int maxThreads,
      @Name("minThreads") int minThreads,
      @Name("idleTimeout") int idleTimeout,
      @Name("queue") BlockingQueue<Runnable> queue) {
    super(maxThreads, minThreads, idleTimeout, queue);
  }

  @Override
  protected void runJob(Runnable job) {
    super.runJob(job);
    ThreadLocalCacheCleaner.get().cleanThreadCache();
  }
}

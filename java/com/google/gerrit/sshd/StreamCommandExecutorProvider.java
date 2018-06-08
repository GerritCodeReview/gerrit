// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.eclipse.jgit.lib.Config;

class StreamCommandExecutorProvider implements Provider<ScheduledThreadPoolExecutor> {
  private final int poolSize;
  private final WorkQueue queues;

  @Inject
  StreamCommandExecutorProvider(@GerritServerConfig Config config, WorkQueue wq) {
    final int cores = Runtime.getRuntime().availableProcessors();
    poolSize = config.getInt("sshd", "streamThreads", cores + 1);
    queues = wq;
  }

  @Override
  public ScheduledThreadPoolExecutor get() {
    return queues.createQueue(poolSize, "SSH-Stream-Worker", Thread.MIN_PRIORITY, true);
  }
}

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

package com.google.gerrit.server.config;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.PushAllProjectsOp;
import com.google.gerrit.server.git.ReloadChangeTestMergeQueue;
import com.google.gerrit.server.git.ReloadSubmitQueueOp;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

/** Configuration for a master node in a cluster of servers. */
public class MasterNodeStartup extends LifecycleModule {
  @Override
  public void configure() {
    listener().to(OnStart.class);
  }

  static class OnStart implements LifecycleListener {
    private final PushAllProjectsOp.Factory pushAll;
    private final ReloadSubmitQueueOp.Factory submit;
    private final ReloadChangeTestMergeQueue.Factory changeTestMerge;
    private final boolean replicateOnStartup;

    @Inject
    OnStart(final PushAllProjectsOp.Factory pushAll,
        final ReloadSubmitQueueOp.Factory submit,
        final ReloadChangeTestMergeQueue.Factory changeTestMerge,
        final @GerritServerConfig Config cfg) {
      this.pushAll = pushAll;
      this.submit = submit;
      this.changeTestMerge = changeTestMerge;

      replicateOnStartup = cfg.getBoolean("gerrit", "replicateOnStartup", true);
    }

    @Override
    public void start() {
      if (replicateOnStartup) {
        pushAll.create(null).start(30, TimeUnit.SECONDS);
      }

      submit.create().start(15, TimeUnit.SECONDS);
      changeTestMerge.create().start(15, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
    }
  }
}

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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.ReloadSubmitQueueOp;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.ScheduledFuture;

/** Configuration for a master node in a cluster of servers. */
public class MasterNodeStartup extends LifecycleModule {
  @Override
  public void configure() {
    listener().to(Lifecycle.class);
  }

  @Singleton
  static class Lifecycle implements LifecycleListener {
    private static final int INITIAL_DELAY_S = 15;

    private final ReloadSubmitQueueOp.Factory submit;
    private final long delay;
    private volatile ScheduledFuture<?> handle;

    @Inject
    Lifecycle(ReloadSubmitQueueOp.Factory submit,
        @GerritServerConfig Config config) {
      this.submit = submit;
      this.delay = ConfigUtil.getTimeUnit(config,
          "changeMerge", null, "checkFrequency",
          SECONDS.convert(5, MINUTES), SECONDS);
    }

    @Override
    public void start() {
      if (delay > 0) {
        handle = submit.create()
            .startWithFixedDelay(INITIAL_DELAY_S, delay, SECONDS);
      } else {
        handle = submit.create().start(INITIAL_DELAY_S, SECONDS);
      }
    }

    @Override
    public void stop() {
      ScheduledFuture<?> f = handle;
      if (f != null) {
        handle = null;
        f.cancel(true);
      }
    }
  }
}

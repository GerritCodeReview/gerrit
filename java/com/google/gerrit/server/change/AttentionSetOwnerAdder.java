// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AttentionSetConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;

/** Runnable to enable scheduling change cleanups to run periodically */
public class AttentionSetOwnerAdder implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class AttentionSetOwnerAdderModule extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final AttentionSetOwnerAdder runner;
    private final AttentionSetConfig cfg;

    @Inject
    Lifecycle(WorkQueue queue, AttentionSetOwnerAdder runner, AttentionSetConfig cfg) {
      this.queue = queue;
      this.runner = runner;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      cfg.getSchedule().ifPresent(s -> queue.scheduleAtFixedRate(runner, s));
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final OneOffRequestContext oneOffRequestContext;
  private final BatchUpdate.Factory updateFactory;
  private final ReaddOwnerUtil readdOwnerUtil;

  @Inject
  AttentionSetOwnerAdder(
      OneOffRequestContext oneOffRequestContext,
      BatchUpdate.Factory updateFactory,
      ReaddOwnerUtil readdOwnerUtil) {
    this.oneOffRequestContext = oneOffRequestContext;
    this.updateFactory = updateFactory;
    this.readdOwnerUtil = readdOwnerUtil;
  }

  @Override
  public void run() {
    logger.atInfo().log("Running attention-set owner adder.");
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      readdOwnerUtil.readdOwnerForInactiveOpenChanges(updateFactory);
    }
  }

  @Override
  public String toString() {
    return "attention-set adder";
  }
}

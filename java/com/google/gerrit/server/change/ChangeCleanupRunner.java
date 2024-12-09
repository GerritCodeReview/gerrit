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

package com.google.gerrit.server.change;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.LockManager;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.concurrent.locks.Lock;

/** Runnable to enable scheduling change cleanups to run periodically */
public class ChangeCleanupRunner implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class ChangeCleanupRunnerModule extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
      factory(Factory.class);
    }
  }

  public interface Factory {
    ChangeCleanupRunner create();

    ChangeCleanupRunner create(long abandonAfterMillis, boolean abandonIfMergeable, String message);
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final ChangeCleanupRunner runner;
    private final ChangeCleanupConfig cfg;

    @Inject
    Lifecycle(WorkQueue queue, ChangeCleanupRunner.Factory runner, ChangeCleanupConfig cfg) {
      this.queue = queue;
      this.runner = runner.create();
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
  private final AbandonUtil abandonUtil;
  private final RetryHelper retryHelper;
  private final LockManager lockManager;
  private final long abandonAfterMillis;
  private final boolean abandonIfMergeable;
  @Nullable private final String message;

  @AssistedInject
  ChangeCleanupRunner(
      OneOffRequestContext oneOffRequestContext,
      AbandonUtil abandonUtil,
      RetryHelper retryHelper,
      LockManager lockManager,
      @Assisted long abandonAfterMillis,
      @Assisted boolean abandonIfMergeable,
      @Assisted @Nullable String message) {
    this.oneOffRequestContext = oneOffRequestContext;
    this.abandonUtil = abandonUtil;
    this.retryHelper = retryHelper;
    this.lockManager = lockManager;
    this.abandonAfterMillis = abandonAfterMillis;
    this.abandonIfMergeable = abandonIfMergeable;
    this.message = message;
  }

  @AssistedInject
  ChangeCleanupRunner(
      OneOffRequestContext oneOffRequestContext,
      AbandonUtil abandonUtil,
      RetryHelper retryHelper,
      LockManager lockManager,
      ChangeCleanupConfig cfg) {
    this.oneOffRequestContext = oneOffRequestContext;
    this.abandonUtil = abandonUtil;
    this.retryHelper = retryHelper;
    this.lockManager = lockManager;
    this.abandonAfterMillis = cfg.getAbandonAfter();
    this.abandonIfMergeable = cfg.getAbandonIfMergeable();
    this.message = cfg.getAbandonMessage();
  }

  @Override
  public void run() {
    Lock lock = lockManager.getLock("change-cleanup");
    if (!lock.tryLock()) {
      logger.atInfo().log(
          "Couldn't acquire change-cleanup lock. Assuming another server is running"
              + " change-cleanup");
      return;
    }

    logger.atInfo().log("Running change cleanups.");
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      // abandonInactiveOpenChanges skips failures instead of throwing, so retrying will never
      // actually happen. For the purposes of this class that is fine: they'll get tried again the
      // next time the scheduled task is run.
      @SuppressWarnings("unused")
      var unused =
          retryHelper
              .changeUpdate(
                  "abandonInactiveOpenChanges",
                  updateFactory -> {
                    abandonUtil.abandonInactiveOpenChanges(
                        updateFactory, abandonAfterMillis, abandonIfMergeable, message);
                    return null;
                  })
              .call();
    } catch (RestApiException | UpdateException e) {
      logger.atSevere().withCause(e).log("Failed to cleanup changes.");
    } finally {
      lock.unlock();
    }
  }

  @Override
  public String toString() {
    return "change cleanup runner";
  }
}

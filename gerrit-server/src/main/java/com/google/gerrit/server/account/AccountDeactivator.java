// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.config.ScheduleConfig.MISSING_CONFIG;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runnable to enable scheduling account deactivations to run periodically */
public class AccountDeactivator implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(AccountDeactivator.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final AccountDeactivator deactivator;
    private final boolean supportAutomaticAccountActivityUpdate;
    private final ScheduleConfig scheduleConfig;

    @Inject
    Lifecycle(WorkQueue queue, AccountDeactivator deactivator, @GerritServerConfig Config cfg) {
      this.queue = queue;
      this.deactivator = deactivator;
      scheduleConfig = new ScheduleConfig(cfg, "accountDeactivation");
      this.supportAutomaticAccountActivityUpdate =
          cfg.getBoolean("auth", "autoUpdateAccountActiveStatus", false);
    }

    @Override
    public void start() {
      if (!supportAutomaticAccountActivityUpdate) {
        return;
      }
      long interval = scheduleConfig.getInterval();
      long delay = scheduleConfig.getInitialDelay();
      if (delay == MISSING_CONFIG && interval == MISSING_CONFIG) {
        log.info("Ignoring missing accountDeactivator schedule configuration");
      } else if (delay < 0 || interval <= 0) {
        log.warn(
            String.format(
                "Ignoring invalid accountDeactivator schedule configuration: %s", scheduleConfig));
      } else {
        queue
            .getDefaultQueue()
            .scheduleAtFixedRate(deactivator, delay, interval, TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final OneOffRequestContext oneOffRequestContext;
  private final Accounts accounts;
  private final Realm realm;
  private final SetInactiveFlag sif;
  private final IdentifiedUser.GenericFactory genericUserFactory;

  @Inject
  AccountDeactivator(
      OneOffRequestContext oneOffRequestContext,
      Accounts accounts,
      SetInactiveFlag sif,
      IdentifiedUser.GenericFactory genericUserFactory,
      Realm realm) {
    this.oneOffRequestContext = oneOffRequestContext;
    this.accounts = accounts;
    this.sif = sif;
    this.genericUserFactory = genericUserFactory;
    this.realm = realm;
  }

  @Override
  public void run() {
    log.info("Running account deactivations.");
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      for (Account acc : accounts.all()) {
        if (acc.isActive() && !realm.isActive(acc.getUserName())) {
          sif.deactivate(genericUserFactory.create(acc.getId()));
        }
      }
      log.info("Deactivations complete");
    } catch (Exception e) {
      log.error("Failed to deactivate inactive accounts.", e);
    }
  }

  @Override
  public String toString() {
    return "account deactivator";
  }
}

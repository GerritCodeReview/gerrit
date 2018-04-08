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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.ScheduleConfig;
import com.google.gerrit.config.ScheduleConfig.Schedule;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.query.account.AccountPredicates;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
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
    private final Optional<Schedule> schedule;

    @Inject
    Lifecycle(WorkQueue queue, AccountDeactivator deactivator, @GerritServerConfig Config cfg) {
      this.queue = queue;
      this.deactivator = deactivator;
      schedule = ScheduleConfig.createSchedule(cfg, "accountDeactivation");
      supportAutomaticAccountActivityUpdate =
          cfg.getBoolean("auth", "autoUpdateAccountActiveStatus", false);
    }

    @Override
    public void start() {
      if (!supportAutomaticAccountActivityUpdate) {
        return;
      }
      schedule.ifPresent(s -> queue.scheduleAtFixedRate(deactivator, s));
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Realm realm;
  private final SetInactiveFlag sif;

  @Inject
  AccountDeactivator(
      Provider<InternalAccountQuery> accountQueryProvider, SetInactiveFlag sif, Realm realm) {
    this.accountQueryProvider = accountQueryProvider;
    this.sif = sif;
    this.realm = realm;
  }

  @Override
  public void run() {
    log.info("Running account deactivations");
    try {
      int numberOfAccountsDeactivated = 0;
      for (AccountState acc : accountQueryProvider.get().query(AccountPredicates.isActive())) {
        if (processAccount(acc)) {
          numberOfAccountsDeactivated++;
        }
      }
      log.info(
          "Deactivations complete, {} account(s) were deactivated", numberOfAccountsDeactivated);
    } catch (Exception e) {
      log.error("Failed to complete deactivation of accounts: " + e.getMessage(), e);
    }
  }

  private boolean processAccount(AccountState accountState) {
    if (!accountState.getUserName().isPresent()) {
      return false;
    }

    String userName = accountState.getUserName().get();
    log.debug("processing account " + userName);
    try {
      if (realm.accountBelongsToRealm(accountState.getExternalIds()) && !realm.isActive(userName)) {
        sif.deactivate(accountState.getAccount().getId());
        log.info("deactivated account " + userName);
        return true;
      }
    } catch (ResourceConflictException e) {
      log.info("Account {} already deactivated, continuing...", userName);
    } catch (Exception e) {
      log.error(
          "Error deactivating account: {} ({}) {}",
          userName,
          accountState.getAccount().getId(),
          e.getMessage(),
          e);
    }
    return false;
  }

  @Override
  public String toString() {
    return "account deactivator";
  }
}
